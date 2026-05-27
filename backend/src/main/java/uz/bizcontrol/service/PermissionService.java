package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.PermissionRepository;
import uz.bizcontrol.repository.TemporaryPermissionRepository;
import uz.bizcontrol.security.BizControlPrincipal;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central permission checking service.
 *
 * <p>Caches permissions per role for 5 minutes to avoid repeated DB hits.</p>
 *
 * <p>Usage in controllers:
 * <pre>
 *   permissionService.require(principal, "sales.create");
 * </pre>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final AccessLogService accessLogService;
    private final TemporaryPermissionRepository temporaryPermissionRepository;

    private static final long CACHE_TTL_MS = 5 * 60 * 1_000L; // 5 min

    private record CachedPerms(Set<String> codes, long timestamp) {}

    private final Map<Long, CachedPerms> cache = new ConcurrentHashMap<>();

    // ── Cache management ──────────────────────────────────────────

    public Set<String> loadPermissionsForRole(Long roleId) {
        if (roleId == null) return Collections.emptySet();
        CachedPerms cached = cache.get(roleId);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp()) < CACHE_TTL_MS) {
            return cached.codes();
        }
        Set<String> codes = permissionRepository.findPermissionCodesByRoleId(roleId);
        cache.put(roleId, new CachedPerms(codes, System.currentTimeMillis()));
        return codes;
    }

    /**
     * Loads role-based permissions merged with any active temporary permissions for the user.
     * Called at request time from JwtAuthenticationFilter.
     */
    public Set<String> loadPermissions(Long roleId, Long userId, Long companyId) {
        Set<String> base = new HashSet<>(loadPermissionsForRole(roleId));
        // Merge temporary permissions (no caching — they expire and are time-sensitive)
        List<String> tempCodes = temporaryPermissionRepository
                .findEffectiveCodes(userId, companyId, LocalDateTime.now());
        base.addAll(tempCodes);
        return Collections.unmodifiableSet(base);
    }

    /** Invalidate cache entry after role permissions are modified. */
    public void invalidateCache(Long roleId) {
        cache.remove(roleId);
    }

    // ── Check helpers ─────────────────────────────────────────────

    /**
     * Returns true if the principal has the requested permission.
     * OWNER always returns true (full access).
     */
    public boolean hasPermission(BizControlPrincipal principal, String permCode) {
        if (principal == null) return false;
        if ("OWNER".equalsIgnoreCase(principal.getRole())) return true;   // OWNER => full access, always
        Set<String> perms = principal.getPermissions();
        return perms != null && (perms.contains("*") || perms.contains(permCode));
    }

    /** Spring Security SpEL usage: @permissionService.hasPermission(authentication, 'perm') */
    public boolean hasPermission(Authentication auth, String permCode) {
        if (auth == null || !(auth.getPrincipal() instanceof BizControlPrincipal p)) return false;
        return hasPermission(p, permCode);
    }

    /**
     * Throws 403 if the principal lacks the permission.
     * Also writes an access-denied log entry asynchronously.
     */
    public void require(BizControlPrincipal principal, String permCode) {
        if (hasPermission(principal, permCode)) return;

        // Detailed, structured denial log to make permission problems obvious.
        Long   userId   = principal != null ? principal.getUserId()    : null;
        Long   companyId= principal != null ? principal.getCompanyId() : null;
        Long   roleId   = principal != null ? principal.getRoleId()    : null;
        String roleCode = principal != null ? principal.getRole()      : null;
        String available = (principal != null && principal.getPermissions() != null)
                ? String.join(",", principal.getPermissions()) : "(none)";
        log.warn("[AccessDenied] userId={} companyId={} roleId={} roleCode={} requestedPermission={} availablePermissions=[{}]",
                userId, companyId, roleId, roleCode, permCode, available);

        if (principal != null) {
            accessLogService.logDenied(companyId, userId, permCode, permCode.split("\\.")[0],
                    "Missing permission: " + permCode);
        }
        throw BusinessException.forbidden("Access denied: missing permission '" + permCode + "'");
    }

    /** Convenience: require any one of the listed permissions. */
    public void requireAny(BizControlPrincipal principal, String... permCodes) {
        for (String code : permCodes) {
            if (hasPermission(principal, code)) return;
        }
        String needed = String.join(" | ", permCodes);
        if (principal != null) {
            accessLogService.logDenied(principal.getCompanyId(), principal.getUserId(),
                    needed, permCodes[0].split("\\.")[0], "Missing permissions: " + needed);
        }
        throw BusinessException.forbidden("Access denied: requires one of [" + needed + "]");
    }
}
