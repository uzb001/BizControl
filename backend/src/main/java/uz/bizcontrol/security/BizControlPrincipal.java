package uz.bizcontrol.security;

import lombok.Getter;
import java.util.Collections;
import java.util.Set;

/**
 * Immutable principal held in the Spring Security context for every request.
 *
 * <ul>
 *   <li>{@code role}        — role code string (e.g. "OWNER", "SELLER")</li>
 *   <li>{@code roleId}      — FK to roles table (null for legacy sessions)</li>
 *   <li>{@code permissions} — set of permission codes loaded from cache/DB</li>
 * </ul>
 */
@Getter
public class BizControlPrincipal {

    private final Long userId;
    private final Long companyId;
    private final String role;       // role code
    private final Long roleId;       // roles.id (may be null for legacy tokens)
    private final Set<String> permissions;

    public BizControlPrincipal(Long userId, Long companyId, String role,
                                Long roleId, Set<String> permissions) {
        this.userId      = userId;
        this.companyId   = companyId;
        this.role        = role;
        this.roleId      = roleId;
        this.permissions = permissions != null ? Collections.unmodifiableSet(permissions)
                                               : Collections.emptySet();
    }

    /** Convenience constructor for legacy tokens that carry no roleId. */
    public BizControlPrincipal(Long userId, Long companyId, String role) {
        this(userId, companyId, role, null, Collections.emptySet());
    }

    public boolean isOwner()  { return "OWNER".equalsIgnoreCase(role); }
    public boolean isAdmin()  { return "ADMIN".equalsIgnoreCase(role) || isOwner(); }

    /** Returns true for OWNER (always) or if the permission is in the set. */
    public boolean can(String permCode) {
        return isOwner() || permissions.contains(permCode);
    }
}
