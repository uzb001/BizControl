package uz.bizcontrol.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate a JWT containing userId, companyId, role code, roleId and tokenVersion.
     * Permissions are NOT stored in the token — they are loaded from DB/cache
     * on every request by {@link JwtAuthenticationFilter}.
     *
     * @param tokenVersion current value of {@code User.tokenVersion}; embedded so the
     *                     filter can reject stale tokens after role/status changes.
     */
    public String generateToken(Long userId, Long companyId, String roleCode, Long roleId, int tokenVersion) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + jwtExpiration);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("companyId",    companyId)
                .claim("role",         roleCode)
                .claim("roleId",       roleId)
                .claim("type",         "access")
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(exp)
                .signWith(getSigningKey())
                .compact();
    }

    /** Backward-compat overload (no roleId, tokenVersion defaults to 1). */
    public String generateToken(Long userId, Long companyId, String roleCode, Long roleId) {
        return generateToken(userId, companyId, roleCode, roleId, 1);
    }

    /** Backward-compat overload (no roleId, no tokenVersion). */
    public String generateToken(Long userId, Long companyId, String roleCode) {
        return generateToken(userId, companyId, roleCode, null, 1);
    }

    /**
     * Generate a short-lived (5-minute) company-selection token.
     * Used when the user belongs to multiple companies: login returns this token,
     * and the client must send it back in the /auth/select-company body.
     * The token carries only userId + type=selection — no company context.
     */
    public String generateSelectionToken(Long userId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + 5 * 60 * 1_000L); // 5 minutes
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "selection")
                .issuedAt(now)
                .expiration(exp)
                .signWith(getSigningKey())
                .compact();
    }

    /** Returns true only if the token was issued as a selection token. */
    public boolean isSelectionToken(String token) {
        try {
            String type = parseClaims(token).get("type", String.class);
            return "selection".equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    public Long    getUserId(String token)    { return Long.parseLong(parseClaims(token).getSubject()); }
    public Long    getCompanyId(String token) { return parseClaims(token).get("companyId", Long.class); }
    public String  getRole(String token)      { return parseClaims(token).get("role", String.class); }
    public Long    getRoleId(String token) {
        Object v = parseClaims(token).get("roleId");
        if (v == null) return null;
        return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString());
    }

    /** Returns the "type" claim ("access" | "selection"). */
    public String getTokenType(String token) {
        try { return parseClaims(token).get("type", String.class); }
        catch (Exception e) { return null; }
    }

    /**
     * Returns the tokenVersion embedded at issue time.
     * Returns 1 for legacy tokens that pre-date this field (safe default).
     */
    public int getTokenVersion(String token) {
        try {
            Object v = parseClaims(token).get("tokenVersion");
            if (v == null) return 1; // legacy token — treated as version 1
            return v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString());
        } catch (Exception e) { return 1; }
    }

    public boolean validateToken(String token) {
        try { parseClaims(token); return true; }
        catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT: {}", ex.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
