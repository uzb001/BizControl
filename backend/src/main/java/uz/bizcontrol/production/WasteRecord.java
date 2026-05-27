package uz.bizcontrol.production;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A recorded loss/scrap of material, optionally tied to a production order. */
@Entity
@Table(name = "waste_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WasteRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "production_order_id")
    private Long productionOrderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Builder.Default
    private String unit = "piece";

    private String reason;

    @Builder.Default
    @Column(name = "cost_impact", precision = 18, scale = 2)
    private BigDecimal costImpact = BigDecimal.ZERO;

    @Column(name = "created_by")
    private Long createdBy;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
