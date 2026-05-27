package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A single product line on a logistics order. {@code itemValue = quantity × unitCost}. */
@Entity
@Table(name = "logistics_order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LogisticsOrderItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "logistics_order_id", nullable = false)
    private Long logisticsOrderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 18, scale = 2, nullable = false)
    private BigDecimal unitCost;

    @Column(name = "item_value", precision = 18, scale = 2, nullable = false)
    private BigDecimal itemValue;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
