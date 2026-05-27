package uz.bizcontrol.production;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A bill-of-materials / recipe that defines how a finished product is made. */
@Entity
@Table(name = "bom_templates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BomTemplate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Finished product this recipe produces. */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    private String version = "v1";

    @Builder.Default
    @Column(name = "output_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal outputQuantity = BigDecimal.ONE;

    @Builder.Default
    private String unit = "piece";

    /** active | inactive */
    @Builder.Default
    @Column(nullable = false)
    private String status = "active";

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
