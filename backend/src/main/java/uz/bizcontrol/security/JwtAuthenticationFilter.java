package uz.bizcontrol.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.bizcontrol.repository.CompanyUserRepository;
import uz.bizcontrol.repository.UserRepository;
import uz.bizcontrol.service.AccessLogService;
import uz.bizcontrol.service.PermissionService;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * JWT authentication filter — runs once per request.
 *
 * <p>Security checks performed in order:
 * <ol>
 *   <li>Token must be present and cryptographically valid.</li>
 *   <li>Token type must be {@code "access"} — selection tokens are rejected.</li>
 *   <li>User must exist and be {@code status="active"}.</li>
 *   <li>Token's {@code tokenVersion} must match {@code users.token_version}
 *       (invalidates old tokens after role change / deactivation).</li>
 *   <li>Company membership must be {@code status="active"} (skipped for
 *       endpoints that don't carry a companyId, e.g. selection tokens).</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider         jwtTokenProvider;
    private final PermissionService        permissionService;
    private final UserRepository           userRepository;
    private final CompanyUserRepository    companyUserRepository;
    private final AccessLogService         accessLogService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);
        if (!StringUtils.hasText(token) || !jwtTokenProvider.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── 1. Reject non-access tokens (e.g. selection tokens) ──────────────
        String tokenType = jwtTokenProvider.getTokenType(token);
        if (!"access".equals(tokenType)) {
            log.debug("Rejected non-access token (type={}) for {}", tokenType, request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        Long userId    = jwtTokenProvider.getUserId(token);
        Long companyId = jwtTokenProvider.getCompanyId(token);
        String role    = jwtTokenProvider.getRole(token);
        Long roleId    = jwtTokenProvider.getRoleId(token);
        int  tokenVer  = jwtTokenProvider.getTokenVersion(token);

        // ── 2. User active + tokenVersion check ──────────────────────────────
        var statusOpt = userRepository.findStatusById(userId);
        if (statusOpt.isEmpty()) {
            log.warn("Token userId={} not found in DB", userId);
            filterChain.doFilter(request, response);
            return;
        }
        var userStatus = statusOpt.get();

        if (!"active".equals(userStatus.getStatus())) {
            log.info("Blocked inactive user={} from {}", userId, request.getRequestURI());
            accessLogService.logDenied(companyId, userId, "AUTH", "auth",
                    "User account is inactive", getClientIp(request));
            filterChain.doFilter(request, response);
            return;
        }

        if (userStatus.getTokenVersion() != tokenVer) {
            log.info("Stale token rejected for user={} (tokenVer={} vs DB={})",
                    userId, tokenVer, userStatus.getTokenVersion());
            accessLogService.logDenied(companyId, userId, "AUTH", "auth",
                    "Stale token (version mismatch)", getClientIp(request));
            filterChain.doFilter(request, response);
            return;
        }

        // ── 3. Company membership active check (only when companyId is present) ─
        if (companyId != null) {
            String cuStatus = companyUserRepository
                    .findStatusByCompanyIdAndUserId(companyId, userId)
                    .orElse(null);
            if (!"active".equals(cuStatus)) {
                log.info("Blocked user={} — membership in company={} is {}", userId, companyId, cuStatus);
                accessLogService.logDenied(companyId, userId, "AUTH", "auth",
                        "Company membership is not active", getClientIp(request));
                filterChain.doFilter(request, response);
                return;
            }
        }

        // ── 4. Load permissions and set authentication ─────────────────────────
        Set<String> permissions = "OWNER".equalsIgnoreCase(role)
                ? Set.of("*")
                : permissionService.loadPermissions(roleId, userId, companyId);

        BizControlPrincipal principal =
                new BizControlPrincipal(userId, companyId, role, roleId, permissions);

        var auth = new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    /** Best-effort client IP extraction (handles reverse-proxy headers). */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xri)) return xri;
        return request.getRemoteAddr();
    }
}
