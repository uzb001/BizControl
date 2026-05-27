package uz.bizcontrol.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "company_users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanyUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Legacy role string — kept for backward compat; prefer roleId/Role. */
    @Column(nullable = false)
    private String role = "SELLER";  // OWNER | ADMIN | SELLER (fallback)

    /** The structured Role entity. Nullable for records migrated from v4. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role roleObj;

    private String status = "active";

    /** Who sent the invitation (nullable = direct signup). */
    @Column(name = "invited_by")
    private Long invitedBy;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt = LocalDateTime.now();

    /** Legacy alias — kept so old code reading createdAt still works. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Convenience: return role code from Role entity or fall back to legacy string. */
    public String effectiveRoleCode() {
        if (roleObj != null) return roleObj.getCode();
        return role;
    }
}
