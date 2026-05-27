package uz.bizcontrol.accounting;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A Chart-of-Accounts entry. One row per (company, code).
 * Accounts are per-company and seeded from the standard template.
 */
@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Account {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    /** ASSET | LIABILITY | EQUITY | REVENUE | EXPENSE */
    @Column(nullable = false, length = 20)
    private String type;

    /** DEBIT | CREDIT — the side that increases this account */
    @Column(name = "normal_balance", nullable = false, length = 6)
    private String normalBalance;

    @Builder.Default
    @Column(name = "is_system")
    private boolean isSystem = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
