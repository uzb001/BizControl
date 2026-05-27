package uz.bizcontrol.production;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A manufacturing order: produce N units of a product from a BOM. */
@Entity
@Table(name = "production_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductionOrder {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "bom_template_id")
    private Long bomTemplateId;

    @Column(name = "planned_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal plannedQuantity;

    @Builder.Default
    @Column(name = "completed_quantity", precision = 18, scale = 3)
    private BigDecimal completedQuantity = BigDecimal.ZERO;

    @Builder.Default
    private String unit = "piece";

    /** draft | planned | in_progress | quality_check | completed | cancelled */
    @Builder.Default
    @Column(nullable = false)
    private String status = "draft";

    @Column(name = "source_warehouse_id")
    private Long sourceWarehouseId;

    @Column(name = "production_warehouse_id")
    private Long productionWarehouseId;

    @Column(name = "finished_goods_warehouse_id")
    private Long finishedGoodsWarehouseId;

    @Column(name = "responsible_user_id")
    private Long responsibleUserId;

    @Column(name = "planned_start_date")
    private LocalDateTime plannedStartDate;

    @Column(name = "planned_end_date")
    private LocalDateTime plannedEndDate;

    @Column(name = "actual_start_date")
    private LocalDateTime actualStartDate;

    @Column(name = "actual_end_date")
    private LocalDateTime actualEndDate;

    @Builder.Default
    @Column(name = "total_cost", precision = 18, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "cost_per_unit", precision = 18, scale = 4)
    private BigDecimal costPerUnit = BigDecimal.ZERO;

    @Builder.Default
    private String currency = "UZS";

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
