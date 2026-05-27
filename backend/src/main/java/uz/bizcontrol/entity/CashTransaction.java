package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Unified money movement record.
 * <p>
 * {@code paymentSource}: cash | bank | card | click | payme
 * {@code transactionType}: income | expense
 * {@code status}: active | reversed | cancelled
 */
@Entity
@Table(name = "money_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CashTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** income | expense */
    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    /** cash | bank | card | click | payme */
    @Column(name = "payment_source")
    private String paymentSource = "cash";

    @Column(nullable = false)
    private BigDecimal amount;

    private String currency = "UZS";
    private String category;

    @Column(name = "related_sale_id")
    private Long relatedSaleId;
    @Column(name = "related_purchase_id")
    private Long relatedPurchaseId;
    @Column(name = "related_customer_id")
    private Long relatedCustomerId;
    @Column(name = "related_supplier_id")
    private Long relatedSupplierId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    private String note;

    /** active | reversed | cancelled */
    private String status = "active";

    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_by") private Long updatedBy;

    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
