package uz.bizcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.PermissionRepository;
import uz.bizcontrol.repository.TemporaryPermissionRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.AccessLogService;
import uz.bizcontrol.service.PermissionService;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies role-permission enforcement at the service layer.
 * These tests run entirely in-memory (no Spring context, no DB).
 */
@ExtendWith(MockitoExtension.class)
class AuthSecurityTest {

    @Mock PermissionRepository permissionRepository;
    @Mock AccessLogService accessLogService;
    @Mock TemporaryPermissionRepository temporaryPermissionRepository;
    @InjectMocks PermissionService permissionService;

    // ── Helper to build principals ────────────────────────────────────────────

    private BizControlPrincipal principal(String role, String... perms) {
        return new BizControlPrincipal(42L, 1L, role, 99L, Set.of(perms));
    }

    // ── OWNER bypasses all permission checks ──────────────────────────────────

    @Test
    void owner_hasEveryPermission() {
        BizControlPrincipal owner = principal("OWNER");
        assertTrue(permissionService.hasPermission(owner, "sales.create"));
        assertTrue(permissionService.hasPermission(owner, "reports.view"));
        assertTrue(permissionService.hasPermission(owner, "cashbox.cancel"));
        assertTrue(permissionService.hasPermission(owner, "approvals.decide"));
    }

    // ── products.create enforcement (the reported blocker) ────────────────────

    @Test
    void owner_canCreateProducts() {
        assertTrue(permissionService.hasPermission(principal("OWNER"), "products.create"));
        assertDoesNotThrow(() -> permissionService.require(principal("OWNER"), "products.create"));
    }

    @Test
    void seller_withoutPermission_cannotCreateProducts() {
        BizControlPrincipal seller = principal("SELLER", "products.view");
        assertFalse(permissionService.hasPermission(seller, "products.create"));
        assertThrows(BusinessException.class, () -> permissionService.require(seller, "products.create"));
    }

    @Test
    void seller_grantedProductsCreate_canCreate() {
        BizControlPrincipal seller = principal("SELLER", "products.view", "products.create");
        assertTrue(permissionService.hasPermission(seller, "products.create"));
    }

    @Test
    void wildcardPermission_grantsEverything() {
        BizControlPrincipal admin = principal("ADMIN", "*");
        assertTrue(permissionService.hasPermission(admin, "products.create"));
        assertTrue(permissionService.hasPermission(admin, "anything.else"));
    }

    // ── Seller cannot see profit ──────────────────────────────────────────────

    @Test
    void seller_withoutReportsView_cannotAccessReports() {
        BizControlPrincipal seller = principal("SELLER", "sales.create");
        assertFalse(permissionService.hasPermission(seller, "reports.view"));
    }

    @Test
    void seller_withoutReportsView_require_throws403() {
        BizControlPrincipal seller = principal("SELLER", "sales.create");
        assertThrows(BusinessException.class,
                () -> permissionService.require(seller, "reports.view"));
    }

    @Test
    void seller_explicitlyGrantedPermission_isAllowed() {
        BizControlPrincipal seller = principal("SELLER", "sales.create", "sales.view");
        assertTrue(permissionService.hasPermission(seller, "sales.create"));
        assertTrue(permissionService.hasPermission(seller, "sales.view"));
    }

    // ── Missing permission → 403 ──────────────────────────────────────────────

    @Test
    void require_missingPermission_throwsForbiddenBusinessException() {
        BizControlPrincipal user = principal("CASHIER");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionService.require(user, "approvals.decide"));
        assertEquals(403, ex.getStatus().value());
    }

    // ── requireAny → passes if at least one permission matches ───────────────

    @Test
    void requireAny_oneMatch_passes() {
        BizControlPrincipal user = principal("MANAGER", "approvals.request");
        assertDoesNotThrow(() -> permissionService.requireAny(user, "approvals.decide", "approvals.request"));
    }

    @Test
    void requireAny_noMatch_throws() {
        BizControlPrincipal user = principal("CASHIER");
        assertThrows(BusinessException.class,
                () -> permissionService.requireAny(user, "approvals.decide", "approvals.request"));
    }

    // ── Company isolation on principal ────────────────────────────────────────

    @Test
    void principal_companyId_isCorrect() {
        BizControlPrincipal p = new BizControlPrincipal(10L, 5L, "SELLER");
        assertEquals(5L, p.getCompanyId());
        assertEquals(10L, p.getUserId());
    }

    // ── Null principal → no permission ────────────────────────────────────────

    @Test
    void hasPermission_nullPrincipal_returnsFalse() {
        assertFalse(permissionService.hasPermission((BizControlPrincipal) null, "any.perm"));
    }

    // ── Permission cache invalidation ─────────────────────────────────────────

    @Test
    void invalidateCache_removesEntry() {
        when(permissionRepository.findPermissionCodesByRoleId(1L))
                .thenReturn(Set.of("sales.view"));
        when(temporaryPermissionRepository.findEffectiveCodes(any(), any(), any()))
                .thenReturn(List.of());

        // Load into cache
        Set<String> perms = permissionService.loadPermissions(1L, 42L, 1L);
        assertTrue(perms.contains("sales.view"));

        // Invalidate
        permissionService.invalidateCache(1L);

        // Return different permissions next call
        when(permissionRepository.findPermissionCodesByRoleId(1L))
                .thenReturn(Set.of("sales.view", "sales.cancel"));
        Set<String> perms2 = permissionService.loadPermissions(1L, 42L, 1L);
        assertTrue(perms2.contains("sales.cancel"));
    }
}
