package uz.bizcontrol.production;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An additional (non-material) cost line on a production order. */
@Entity
@Table(name = "production_costs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductionCost {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "production_order_id", nullable = false)
    private Long productionOrderId;

    /** raw_material | packaging | labor | transport | overhead | other */
    @Column(name = "cost_type", nullable = false)
    private String costType;

    @Builder.Default
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Builder.Default
    private String currency = "UZS";

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
