package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A country a company trades with (origin of imports, destination of exports).
 * Warehouses and suppliers may optionally be linked to a country via FK.
 */
@Entity
@Table(name = "countries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Country {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 8)
    private String code;

    @Builder.Default
    @Column(length = 10)
    private String currency = "UZS";

    @Column(length = 60)
    private String timezone;

    @Column(length = 8)
    private String language;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** active | archived */
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "active";

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
