package uz.bizcontrol.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.dto.request.LoginRequest;
import uz.bizcontrol.dto.request.SignupRequest;
import uz.bizcontrol.dto.response.AuthResponse;
import uz.bizcontrol.entity.CompanyUser;
import uz.bizcontrol.entity.User;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.CompanyUserRepository;
import uz.bizcontrol.repository.UserRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.security.JwtTokenProvider;
import uz.bizcontrol.service.AuthService;
import uz.bizcontrol.service.CompanyService;
import uz.bizcontrol.service.PermissionService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService           authService;
    private final UserRepository        userRepository;
    private final CompanyService        companyService;
    private final CompanyUserRepository companyUserRepository;
    private final PermissionService     permissionService;
    private final JwtTokenProvider      jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest req) {
        return ResponseEntity.ok(authService.signup(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }

    /**
     * GET /auth/me — returns the current user + company context including permissions.
     * Used on app startup to refresh the session.
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal BizControlPrincipal principal) {
        User user = userRepository.findById(principal.getUserId()).orElseThrow();
        var company = companyService.getById(principal.getCompanyId());

        return ResponseEntity.ok(AuthResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .companyId(company.getId())
                .companyName(company.getName())
                .role(principal.getRole())
                .roleId(principal.getRoleId())
                .permissions(principal.getPermissions())
                .build());
    }

    /**
     * GET /auth/me/permissions — diagnostic endpoint to see exactly what the current
     * session can do (who am I, which company, role, owner flag, permission codes).
     * Invaluable for debugging "Access denied" issues.
     */
    @GetMapping("/me/permissions")
    public ResponseEntity<Map<String, Object>> myPermissions(
            @AuthenticationPrincipal BizControlPrincipal principal) {
        if (principal == null) throw BusinessException.forbidden("Not authenticated");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", principal.getUserId());
        body.put("companyId", principal.getCompanyId());
        body.put("roleId", principal.getRoleId());
        body.put("roleCode", principal.getRole());
        body.put("isOwner", principal.isOwner());
        body.put("permissions", principal.getPermissions());
        return ResponseEntity.ok(body);
    }

    /**
     * GET /auth/companies — returns all companies the authenticated user belongs to.
     * Used after login when the user has multiple companies.
     */
    @GetMapping("/companies")
    public ResponseEntity<List<AuthResponse.CompanyChoice>> companies(
            @AuthenticationPrincipal BizControlPrincipal principal) {

        List<CompanyUser> memberships = companyUserRepository
                .findByUserId(principal.getUserId()).stream()
                .filter(cu -> "active".equals(cu.getStatus()))
                .toList();

        List<AuthResponse.CompanyChoice> choices = memberships.stream().map(cu ->
                AuthResponse.CompanyChoice.builder()
                        .companyId(cu.getCompany().getId())
                        .companyName(cu.getCompany().getName())
                        .role(cu.effectiveRoleCode())
                        .roleId(cu.getRoleObj() != null ? cu.getRoleObj().getId() : null)
                        .build()
        ).toList();

        return ResponseEntity.ok(choices);
    }

    /**
     * POST /auth/select-company — issue a full JWT for the specified company.
     * <p>
     * Supports two flows:
     * <ol>
     *   <li><b>Multi-company login</b>: body contains {@code selectionToken} (short-lived
     *       5-minute JWT returned by login). No Bearer header required.</li>
     *   <li><b>Authenticated company switch</b>: caller already has a valid Bearer JWT
     *       (e.g. from the TopBar). {@code selectionToken} may be omitted.</li>
     * </ol>
     * Body: {@code { "companyId": 123, "selectionToken": "..." (optional) }}
     */
    @PostMapping("/select-company")
    public ResponseEntity<AuthResponse> selectCompany(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestBody Map<String, Object> body) {

        Long companyId = body.get("companyId") != null
                ? Long.parseLong(body.get("companyId").toString()) : null;
        if (companyId == null)
            throw new BusinessException("companyId is required");

        Long userId;
        String selectionToken = body.get("selectionToken") instanceof String s ? s : null;

        if (selectionToken != null && !selectionToken.isBlank()) {
            // Flow 1: multi-company login — validate selection token
            if (!jwtTokenProvider.validateToken(selectionToken) || !jwtTokenProvider.isSelectionToken(selectionToken))
                throw new BusinessException("Invalid or expired selection token — please log in again");
            userId = jwtTokenProvider.getUserId(selectionToken);
        } else if (principal != null) {
            // Flow 2: already-authenticated user switching companies
            userId = principal.getUserId();
        } else {
            throw new BusinessException("selectionToken or valid Authorization header is required");
        }

        return ResponseEntity.ok(authService.selectCompany(userId, companyId));
    }

    /**
     * POST /auth/accept-invite — employee accepts an invitation link.
     * Body: { token, fullName?, password }
     */
    @PostMapping("/accept-invite")
    public ResponseEntity<AuthResponse> acceptInvite(@RequestBody Map<String, String> body) {
        String token    = body.get("token");
        String fullName = body.get("fullName");
        String password = body.get("password");

        if (token == null || token.isBlank())
            throw new BusinessException("Invitation token is required");
        if (password == null || password.length() < 6)
            throw new BusinessException("Password must be at least 6 characters");

        return ResponseEntity.ok(authService.acceptInvite(token, fullName, password));
    }
}
