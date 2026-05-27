package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Warehouse this movement applied to (nullable for legacy rows). */
    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "movement_type", nullable = false)
    private String movementType;  // purchase, sale, adjustment, return, transfer_in, transfer_out, in, out

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "previous_stock", nullable = false)
    private BigDecimal previousStock;

    @Column(name = "new_stock", nullable = false)
    private BigDecimal newStock;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_type")
    private String referenceType;

    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;
}
