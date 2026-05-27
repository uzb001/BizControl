package uz.bizcontrol.production;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** A raw-material line on a {@link ProductionOrder} (snapshot of BOM × quantity). */
@Entity
@Table(name = "production_order_components")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductionOrderComponent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "production_order_id", nullable = false)
    private Long productionOrderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Builder.Default
    @Column(name = "required_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal requiredQuantity = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "consumed_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal consumedQuantity = BigDecimal.ZERO;

    @Builder.Default
    private String unit = "piece";

    @Builder.Default
    @Column(name = "unit_cost", precision = 18, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_cost", precision = 18, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "waste_percent", precision = 6, scale = 2)
    private BigDecimal wastePercent = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "waste_quantity", precision = 18, scale = 3)
    private BigDecimal wasteQuantity = BigDecimal.ZERO;
}
