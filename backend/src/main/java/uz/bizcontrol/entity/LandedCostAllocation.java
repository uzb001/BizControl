package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The proportional share of all logistics expenses allocated to one item.
 * <pre>
 *   allocatedAmount = expensesTotal × (itemValue / itemsValue)
 *   unitLandedCost  = (itemValue + allocatedAmount) / quantity
 * </pre>
 * Stored on confirmation so subsequent reports + audits can replay the math.
 */
@Entity
@Table(name = "landed_cost_allocations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LandedCostAllocation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "logistics_order_id", nullable = false)
    private Long logisticsOrderId;

    @Column(name = "logistics_order_item_id", nullable = false)
    private Long logisticsOrderItemId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "item_value", precision = 18, scale = 2, nullable = false)
    private BigDecimal itemValue;

    @Column(name = "allocated_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal allocatedAmount;

    @Column(name = "unit_landed_cost", precision = 18, scale = 4, nullable = false)
    private BigDecimal unitLandedCost;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
