package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A physical or logical stock location within a company (main, retail, transit,
 * damaged, customs, temporary). Per-warehouse balances live in {@link WarehouseStock}.
 */
@Entity
@Table(name = "warehouses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Warehouse {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String name;

    private String code;
    private String location;

    /** Optional FK to the {@link Country} this warehouse is in (V15+). */
    @Column(name = "country_id")
    private Long countryId;

    @Column(name = "responsible_person")
    private String responsiblePerson;

    private String phone;

    /** main | retail | transit | damaged | customs | temporary */
    @Builder.Default
    @Column(nullable = false)
    private String type = "main";

    /** active | inactive | archived */
    @Builder.Default
    @Column(nullable = false)
    private String status = "active";

    @Column(columnDefinition = "TEXT")
    private String note;

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
