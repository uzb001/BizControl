package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An order that physically moves products between two warehouses (typically across
 * countries) and capitalises customs/shipping/broker/insurance expenses on top of
 * the items' source cost. Confirmation runs stock transfers + cash transactions +
 * a balanced journal entry atomically. Drafts can be edited and cancelled freely;
 * confirmed orders are immutable.
 */
@Entity
@Table(name = "logistics_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LogisticsOrder {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "order_number", nullable = false, length = 40)
    private String orderNumber;

    @Column(name = "source_country_id")
    private Long sourceCountryId;

    @Column(name = "source_warehouse_id", nullable = false)
    private Long sourceWarehouseId;

    @Column(name = "destination_country_id")
    private Long destinationCountryId;

    @Column(name = "destination_warehouse_id", nullable = false)
    private Long destinationWarehouseId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String currency = "UZS";

    @Builder.Default
    @Column(name = "exchange_rate", precision = 18, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Builder.Default
    @Column(name = "items_value", precision = 18, scale = 2)
    private BigDecimal itemsValue = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "expenses_total", precision = 18, scale = 2)
    private BigDecimal expensesTotal = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "landed_total", precision = 18, scale = 2)
    private BigDecimal landedTotal = BigDecimal.ZERO;

    /** draft | confirmed | cancelled */
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "draft";

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversal_reason", columnDefinition = "TEXT")
    private String reversalReason;

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
