package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.CashTransactionRepository;
import uz.bizcontrol.repository.CompanyUserRepository;
import uz.bizcontrol.repository.UserInvitationRepository;
import uz.bizcontrol.repository.UserRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.AccessLogService;
import uz.bizcontrol.service.AuditService;
import uz.bizcontrol.service.CompanyService;
import uz.bizcontrol.service.ExchangeRateService;
import uz.bizcontrol.service.PermissionService;
import uz.bizcontrol.service.RoleService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService            companyService;
    private final CompanyUserRepository     companyUserRepository;
    private final UserRepository            userRepository;
    private final UserInvitationRepository  invitationRepository;
    private final AuditService              auditService;
    private final PermissionService         permissionService;
    private final RoleService               roleService;
    private final AccessLogService          accessLogService;
    private final CashTransactionRepository  cashTransactionRepository;
    private final ExchangeRateService        exchangeRateService;

    /**
     * Per-currency cash & bank balances derived from the active transaction log.
     * UZS and USD (etc.) are reported separately — nothing is auto-converted.
     */
    @GetMapping("/balances")
    public ResponseEntity<Map<String, Object>> balances(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "dashboard.view");
        boolean canCash = permissionService.hasPermission(p, "cashbox.view");
        boolean canBank = permissionService.hasPermission(p, "bank.view");
        String base = exchangeRateService.baseCurrency(p.getCompanyId());

        Map<String, BigDecimal> cash = new LinkedHashMap<>();
        Map<String, BigDecimal> bank = new LinkedHashMap<>();
        for (Object[] r : cashTransactionRepository.balancesByCurrencyAndSource(p.getCompanyId())) {
            String cur = r[0] != null ? r[0].toString() : base;
            String src = r[1] != null ? r[1].toString() : "cash";
            BigDecimal net = r[2] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
            boolean isBank = "bank".equals(src) || "bank_transfer".equals(src);
            (isBank ? bank : cash).merge(cur, net, BigDecimal::add);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("base", base);
        result.put("cash", canCash ? toCurrencyList(cash, base) : List.of());
        result.put("bank", canBank ? toCurrencyList(bank, base) : List.of());
        return ResponseEntity.ok(result);
    }

    private List<Map<String, Object>> toCurrencyList(Map<String, BigDecimal> m, String base) {
        List<Map<String, Object>> list = new ArrayList<>();
        m.entrySet().stream()
                .sorted((a, b) -> a.getKey().equals(base) ? -1 : b.getKey().equals(base) ? 1 : a.getKey().compareTo(b.getKey()))
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("currency", e.getKey());
                    row.put("balance", e.getValue());
                    list.add(row);
                });
        return list;
    }

    // ── Company Info ──────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Company> get(@AuthenticationPrincipal BizControlPrincipal p) {
        Company c = companyService.getById(p.getCompanyId());
        // Mask sensitive balance fields based on permissions
        if (!permissionService.hasPermission(p, "cashbox.view")) {
            c.setCashBalance(null);
        }
        if (!permissionService.hasPermission(p, "bank.view")) {
            c.setBankBalance(null);
        }
        return ResponseEntity.ok(c);
    }

    @PutMapping
    public ResponseEntity<Company> update(
            @AuthenticationPrincipal BizControlPrincipal p,
            @RequestBody Company updates) {
        permissionService.require(p, "settings.edit_company");
        return ResponseEntity.ok(companyService.update(p.getCompanyId(), updates));
    }

    // ── User Management ───────────────────────────────────────────────────

    /** GET /company/users — list all members with role + status */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers(
            @AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "users.view");

        List<CompanyUser> companyUsers = companyUserRepository.findByCompanyId(p.getCompanyId());
        List<Map<String, Object>> result = companyUsers.stream().map(cu -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",       cu.getId());
            m.put("userId",   cu.getUser().getId());
            m.put("role",     cu.effectiveRoleCode());
            m.put("roleId",   cu.getRoleObj() != null ? cu.getRoleObj().getId() : null);
            m.put("roleName", cu.getRoleObj() != null ? cu.getRoleObj().getName() : cu.effectiveRoleCode());
            m.put("status",   cu.getStatus());
            m.put("joinedAt", cu.getJoinedAt());

            User u = cu.getUser();
            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("id",       u.getId());
            userInfo.put("fullName", u.getFullName());
            userInfo.put("email",    u.getEmail());
            userInfo.put("phone",    u.getPhone());
            userInfo.put("status",   u.getStatus());
            m.put("user", userInfo);
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * POST /company/users/invite
     * Creates a UserInvitation with a secure UUID token (no temp password).
     * The invitee receives the token and calls POST /auth/accept-invite to set their password.
     */
    @PostMapping("/users/invite")
    public ResponseEntity<Map<String, Object>> inviteUser(
            @AuthenticationPrincipal BizControlPrincipal p,
            @RequestBody Map<String, Object> body) {
        permissionService.require(p, "users.invite");

        String email    = (String) body.get("email");
        String phone    = (String) body.get("phone");
        String fullName = (String) body.getOrDefault("fullName", "");

        if ((email == null || email.isBlank()) && (phone == null || phone.isBlank()))
            throw new BusinessException("Email or phone is required");

        // Resolve role object — roleId takes priority; role code as fallback
        Long roleId = body.get("roleId") != null
                ? Long.parseLong(body.get("roleId").toString()) : null;
        String roleCode = body.get("role") instanceof String s && !s.isBlank() ? s : null;

        Role roleObj = null;
        if (roleId != null) {
            roleObj = roleService.getByIdForCompany(p.getCompanyId(), roleId);
        } else if (roleCode != null) {
            roleObj = roleService.findByCodeForCompany(p.getCompanyId(), roleCode)
                    .orElseThrow(() -> new BusinessException("Role not found: " + roleCode));
        }

        // Role is mandatory — no silent STAFF fallback
        if (roleObj == null)
            throw new BusinessException(
                "roleId or role code is required. Valid roles: ADMIN, MANAGER, SELLER, " +
                "ACCOUNTANT, WAREHOUSE, CASHIER, AUDITOR, READ_ONLY");

        // Guard: cannot invite as OWNER
        String effectiveCode = roleObj.getCode();
        if ("OWNER".equalsIgnoreCase(effectiveCode))
            throw new BusinessException("Cannot invite a user as OWNER");

        // Check if user is already in this company
        String login = (email != null && !email.isBlank()) ? email : phone;
        Optional<User> existing = userRepository.findByEmailOrPhone(login);
        if (existing.isPresent() &&
            companyUserRepository.existsByCompanyIdAndUserId(p.getCompanyId(), existing.get().getId())) {
            throw new BusinessException("This user is already a member of the company");
        }

        // Generate secure 64-char token
        String token = UUID.randomUUID().toString().replace("-", "") +
                       UUID.randomUUID().toString().replace("-", "");

        UserInvitation inv = UserInvitation.builder()
                .companyId(p.getCompanyId())
                .invitedBy(p.getUserId())
                .email(email != null && !email.isBlank() ? email : null)
                .phone(phone != null && !phone.isBlank() ? phone : null)
                .role(roleObj)
                .token(token)
                .status("pending")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();

        invitationRepository.save(inv);

        auditService.log(p.getCompanyId(), p.getUserId(),
                "INVITE_USER", "UserInvitation", inv.getId(), null,
                "Invited " + login + " as " + effectiveCode);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("invitationId", inv.getId());
        response.put("email",        inv.getEmail());
        response.put("phone",        inv.getPhone());
        response.put("role",         effectiveCode);
        response.put("roleId",       roleObj.getId());
        response.put("expiresAt",    inv.getExpiresAt());
        response.put("inviteToken",  token);
        response.put("acceptUrl",    "/accept-invite?token=" + token);
        response.put("message",
                "Send this link to the user so they can set their password and join the company.");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /company/invitations — list pending invitations for this company
     */
    @GetMapping("/invitations")
    public ResponseEntity<List<Map<String, Object>>> listInvitations(
            @AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "users.invite");

        List<UserInvitation> invitations = invitationRepository
                .findByCompanyIdOrderByCreatedAtDesc(p.getCompanyId());

        List<Map<String, Object>> result = invitations.stream().map(inv -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",        inv.getId());
            m.put("email",     inv.getEmail());
            m.put("phone",     inv.getPhone());
            m.put("role",      inv.getRole() != null ? inv.getRole().getCode() : null);
            m.put("roleId",    inv.getRole() != null ? inv.getRole().getId()   : null);
            m.put("roleName",  inv.getRole() != null ? inv.getRole().getName() : null);
            m.put("status",    inv.getStatus());
            m.put("expiresAt", inv.getExpiresAt());
            m.put("acceptedAt",inv.getAcceptedAt());
            m.put("createdAt", inv.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * DELETE /company/invitations/{id} — cancel a pending invitation
     */
    @DeleteMapping("/invitations/{id}")
    public ResponseEntity<Void> cancelInvitation(
            @AuthenticationPrincipal BizControlPrincipal p,
            @PathVariable Long id) {
        permissionService.require(p, "users.invite");

        UserInvitation inv = invitationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Invitation"));

        if (!inv.getCompanyId().equals(p.getCompanyId()))
            throw BusinessException.forbidden("Invitation not found");

        if (!"pending".equals(inv.getStatus()))
            throw new BusinessException("Only pending invitations can be cancelled");

        inv.setStatus("cancelled");
        invitationRepository.save(inv);

        auditService.log(p.getCompanyId(), p.getUserId(),
                "CANCEL_INVITATION", "UserInvitation", id, null, "Invitation cancelled");
        return ResponseEntity.noContent().build();
    }

    /** PUT /company/users/{userId}/role — assign a new role to a member */
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<Map<String, Object>> changeRole(
            @AuthenticationPrincipal BizControlPrincipal p,
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {
        permissionService.require(p, "users.change_role");

        if (userId.equals(p.getUserId()))
            throw new BusinessException("Cannot change your own role");

        CompanyUser cu = companyUserRepository
                .findByCompanyIdAndUserId(p.getCompanyId(), userId)
                .orElseThrow(() -> BusinessException.notFound("User"));

        // Protect the company owner
        if ("OWNER".equalsIgnoreCase(cu.effectiveRoleCode()))
            throw new BusinessException("Cannot change the role of the company owner");

        Long newRoleId = body.get("roleId") != null
                ? Long.parseLong(body.get("roleId").toString()) : null;
        String newRoleCode = body.get("role") != null ? (String) body.get("role") : null;

        Role newRoleObj = null;
        if (newRoleId != null) {
            newRoleObj = roleService.getByIdForCompany(p.getCompanyId(), newRoleId);
        } else if (newRoleCode != null) {
            newRoleObj = roleService.findByCodeForCompany(p.getCompanyId(), newRoleCode)
                    .orElseThrow(() -> new BusinessException("Role not found: " + newRoleCode));
        } else {
            throw new BusinessException("roleId or role code is required");
        }

        if ("OWNER".equalsIgnoreCase(newRoleObj.getCode()))
            throw new BusinessException("Cannot assign OWNER role via this endpoint");

        String oldRole = cu.effectiveRoleCode();
        cu.setRole(newRoleObj.getCode());
        cu.setRoleObj(newRoleObj);
        companyUserRepository.save(cu);

        // Invalidate the user's existing tokens by bumping tokenVersion
        User targetUser = userRepository.findById(userId).orElseThrow();
        targetUser.setTokenVersion(targetUser.getTokenVersion() + 1);
        userRepository.save(targetUser);

        auditService.log(p.getCompanyId(), p.getUserId(),
                "CHANGE_ROLE", "User", userId,
                "role:" + oldRole, "role:" + newRoleObj.getCode());
        accessLogService.logAllowed(p.getCompanyId(), p.getUserId(), "users.change_role", "users");

        // Invalidate permission cache for the old role
        if (cu.getRoleObj() != null) permissionService.invalidateCache(cu.getRoleObj().getId());

        return ResponseEntity.ok(Map.of(
                "id",       cu.getId(),
                "userId",   userId,
                "role",     newRoleObj.getCode(),
                "roleId",   newRoleObj.getId(),
                "roleName", newRoleObj.getName()
        ));
    }

    /** POST /company/users/{userId}/deactivate */
    @PostMapping("/users/{userId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateUser(
            @AuthenticationPrincipal BizControlPrincipal p,
            @PathVariable Long userId) {
        permissionService.require(p, "users.deactivate");

        if (userId.equals(p.getUserId()))
            throw new BusinessException("Cannot deactivate yourself");

        CompanyUser cu = companyUserRepository
                .findByCompanyIdAndUserId(p.getCompanyId(), userId)
                .orElseThrow(() -> BusinessException.notFound("User"));

        if ("OWNER".equalsIgnoreCase(cu.effectiveRoleCode()))
            throw new BusinessException("Cannot deactivate the company owner — transfer ownership first");

        // Extra safety: block deactivating the last active owner even if role label differs
        if (companyUserRepository.countActiveOwners(p.getCompanyId()) <= 1 &&
            "OWNER".equalsIgnoreCase(cu.effectiveRoleCode()))
            throw new BusinessException("Cannot deactivate the last owner of the company");

        cu.setStatus("inactive");
        companyUserRepository.save(cu);

        // Invalidate the user's tokens immediately so the deactivated session dies at next request
        userRepository.findById(userId).ifPresent(u -> {
            u.setTokenVersion(u.getTokenVersion() + 1);
            userRepository.save(u);
        });

        auditService.log(p.getCompanyId(), p.getUserId(),
                "DEACTIVATE_USER", "User", userId, "status:active", "status:inactive");

        return ResponseEntity.ok(Map.of("message", "User deactivated successfully"));
    }

    /** POST /company/users/{userId}/activate */
    @PostMapping("/users/{userId}/activate")
    public ResponseEntity<Map<String, Object>> activateUser(
            @AuthenticationPrincipal BizControlPrincipal p,
            @PathVariable Long userId) {
        permissionService.require(p, "users.deactivate"); // same permission gate

        CompanyUser cu = companyUserRepository
                .findByCompanyIdAndUserId(p.getCompanyId(), userId)
                .orElseThrow(() -> BusinessException.notFound("User"));

        cu.setStatus("active");
        companyUserRepository.save(cu);

        auditService.log(p.getCompanyId(), p.getUserId(),
                "ACTIVATE_USER", "User", userId, "status:inactive", "status:active");

        return ResponseEntity.ok(Map.of("message", "User reactivated successfully"));
    }

    /** DELETE /company/users/{userId} — remove user from company */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> removeUser(
            @AuthenticationPrincipal BizControlPrincipal p,
            @PathVariable Long userId) {
        permissionService.require(p, "users.remove");

        if (userId.equals(p.getUserId()))
            throw new BusinessException("Cannot remove yourself");

        CompanyUser cu = companyUserRepository
                .findByCompanyIdAndUserId(p.getCompanyId(), userId)
                .orElseThrow(() -> BusinessException.notFound("User"));

        if ("OWNER".equalsIgnoreCase(cu.effectiveRoleCode()))
            throw new BusinessException("Cannot remove the company owner — transfer ownership first");

        companyUserRepository.delete(cu);

        auditService.log(p.getCompanyId(), p.getUserId(),
                "REMOVE_USER", "User", userId, null, "Removed from company");

        return ResponseEntity.noContent().build();
    }
}
