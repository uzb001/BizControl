package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.dto.request.LoginRequest;
import uz.bizcontrol.dto.request.SignupRequest;
import uz.bizcontrol.dto.response.AuthResponse;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.*;
import uz.bizcontrol.security.JwtTokenProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository            userRepository;
    private final CompanyUserRepository     companyUserRepository;
    private final UserInvitationRepository  invitationRepository;
    private final PasswordEncoder           passwordEncoder;
    private final JwtTokenProvider          jwtTokenProvider;
    private final CompanyService            companyService;
    private final RoleService               roleService;
    private final PermissionService         permissionService;
    private final AuditService              auditService;

    // ── Normal signup: creates a brand-new company and assigns OWNER ──

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        if (req.getEmail() == null && req.getPhone() == null)
            throw new BusinessException("Email or phone is required");
        if (req.getEmail() != null && userRepository.existsByEmail(req.getEmail()))
            throw new BusinessException("Email already registered");
        if (req.getPhone() != null && userRepository.existsByPhone(req.getPhone()))
            throw new BusinessException("Phone already registered");

        // 1. Create user
        User user = userRepository.save(User.builder()
                .fullName(req.getFullName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .status("active")
                .build());

        // 2. Create company
        Company company = companyService.save(Company.builder()
                .name(req.getCompanyName())
                .businessType(req.getBusinessType())
                .mainCurrency(req.getMainCurrency() != null ? req.getMainCurrency() : "UZS")
                .cashBalance(BigDecimal.ZERO)
                .bankBalance(BigDecimal.ZERO)
                .build());

        // 3. Bootstrap default roles (creates OWNER, ADMIN, SELLER, … for this company)
        Map<String, Role> roles = roleService.createDefaultRoles(company);
        Role ownerRole = roles.get("OWNER");

        // 4. Assign OWNER role to user
        companyUserRepository.save(CompanyUser.builder()
                .company(company)
                .user(user)
                .role("OWNER")
                .roleObj(ownerRole)
                .status("active")
                .joinedAt(LocalDateTime.now())
                .build());

        String token = jwtTokenProvider.generateToken(
                user.getId(), company.getId(), "OWNER", ownerRole.getId(), user.getTokenVersion());
        auditService.log(company.getId(), user.getId(), "SIGNUP", "User", user.getId(), null, null);

        return buildResponse(token, user, company, "OWNER", ownerRole,
                Set.of("*"), null);
    }

    // ── Login: single company → issue token; multiple companies → list ──

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmailOrPhone(req.getLogin())
                .orElseThrow(() -> new BusinessException("Invalid credentials"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash()))
            throw new BusinessException("Invalid credentials");

        if (!"active".equals(user.getStatus()))
            throw new BusinessException("Account is not active");

        List<CompanyUser> memberships = companyUserRepository.findByUserId(user.getId())
                .stream()
                .filter(cu -> "active".equals(cu.getStatus()))
                .collect(Collectors.toList());

        if (memberships.isEmpty())
            throw new BusinessException("User has no active company");

        // Single company → issue token immediately
        if (memberships.size() == 1) {
            return issueTokenForMembership(user, memberships.get(0));
        }

        // Multiple companies → return list + a short-lived selection token
        List<AuthResponse.CompanyChoice> choices = memberships.stream().map(cu -> {
            Role r = cu.getRoleObj();
            return AuthResponse.CompanyChoice.builder()
                    .companyId(cu.getCompany().getId())
                    .companyName(cu.getCompany().getName())
                    .role(cu.effectiveRoleCode())
                    .roleId(r != null ? r.getId() : null)
                    .build();
        }).collect(Collectors.toList());

        // Issue a 5-minute selection token so the client can securely call /auth/select-company
        String selectionToken = jwtTokenProvider.generateSelectionToken(user.getId());

        return AuthResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .selectionToken(selectionToken)
                .companies(choices)
                .build();
    }

    // ── Company selection (when user belongs to multiple) ──

    public AuthResponse selectCompany(Long userId, Long companyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User"));

        CompanyUser cu = companyUserRepository
                .findByCompanyIdAndUserId(companyId, userId)
                .orElseThrow(() -> BusinessException.forbidden("User does not belong to this company"));

        if (!"active".equals(cu.getStatus()))
            throw BusinessException.forbidden("Your account is inactive in this company");

        return issueTokenForMembership(user, cu);
    }

    // ── Accept invite: new user sets password & joins company ──

    @Transactional
    public AuthResponse acceptInvite(String token, String fullName, String password) {
        UserInvitation inv = invitationRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("Invalid or expired invitation link"));

        if (!"pending".equals(inv.getStatus()))
            throw new BusinessException("This invitation has already been " + inv.getStatus());
        if (inv.getExpiresAt() != null && inv.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new BusinessException("Invitation link has expired");

        // Find or create user
        String login = inv.getEmail() != null ? inv.getEmail() : inv.getPhone();
        Optional<User> existingOpt = userRepository.findByEmailOrPhone(login);

        User user;
        if (existingOpt.isPresent()) {
            user = existingOpt.get();
            // If user has no password yet (created via old CompanyController), set it now
            if (password != null && !password.isBlank()) {
                user.setPasswordHash(passwordEncoder.encode(password));
                userRepository.save(user);
            }
        } else {
            user = userRepository.save(User.builder()
                    .fullName(fullName != null ? fullName : "New User")
                    .email(inv.getEmail())
                    .phone(inv.getPhone())
                    .passwordHash(passwordEncoder.encode(password))
                    .status("active")
                    .build());
        }

        // Check not already in company
        if (!companyUserRepository
                .existsByCompanyIdAndUserId(inv.getCompanyId(), user.getId())) {

            Company company = new Company();
            company.setId(inv.getCompanyId());

            if (inv.getRole() == null)
                throw new BusinessException("Invitation has no role assigned — cannot accept");

            companyUserRepository.save(CompanyUser.builder()
                    .company(company)
                    .user(user)
                    .role(inv.getRole().getCode())
                    .roleObj(inv.getRole())
                    .status("active")
                    .invitedBy(inv.getInvitedBy())
                    .joinedAt(LocalDateTime.now())
                    .build());
        }

        inv.setStatus("accepted");
        inv.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(inv);

        Company company = companyService.getById(inv.getCompanyId());
        CompanyUser cu = companyUserRepository
                .findByCompanyIdAndUserId(inv.getCompanyId(), user.getId()).orElseThrow();

        return issueTokenForMembership(user, cu);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private AuthResponse issueTokenForMembership(User user, CompanyUser cu) {
        Role roleObj    = cu.getRoleObj();
        String roleCode = cu.effectiveRoleCode();
        Long roleId     = roleObj != null ? roleObj.getId() : null;

        Set<String> perms = "OWNER".equalsIgnoreCase(roleCode)
                ? Set.of("*")
                : permissionService.loadPermissionsForRole(roleId);

        // Always embed the current tokenVersion so the filter can detect stale tokens.
        String jwt = jwtTokenProvider.generateToken(
                user.getId(), cu.getCompany().getId(), roleCode, roleId, user.getTokenVersion());

        auditService.log(cu.getCompany().getId(), user.getId(),
                "LOGIN", "User", user.getId(), null, null);

        return buildResponse(jwt, user, cu.getCompany(), roleCode, roleObj, perms, null);
    }

    private AuthResponse buildResponse(String token, User user, Company company,
                                       String roleCode, Role roleObj,
                                       Set<String> permissions,
                                       List<AuthResponse.CompanyChoice> companies) {
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .companyId(company != null ? company.getId() : null)
                .companyName(company != null ? company.getName() : null)
                .role(roleCode)
                .roleId(roleObj != null ? roleObj.getId() : null)
                .permissions(permissions)
                .companies(companies)
                .build();
    }
}
