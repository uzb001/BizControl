package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Sale {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "sale_number", nullable = false)
    private String saleNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "sale_date", nullable = false)
    private LocalDateTime saleDate;

    /** Source warehouse stock was deducted from (defaults to the company's main warehouse). */
    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "total_amount")
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "unpaid_amount")
    private BigDecimal unpaidAmount = BigDecimal.ZERO;

    @Column(name = "payment_method")
    private String paymentMethod = "cash";

    private String currency = "UZS";

    /** active | cancelled */
    private String status = "active";

    /** draft | pending_approval | posted | cancelled | locked */
    @Column(name = "doc_status")
    private String docStatus = "posted";

    @Column(name = "locked")
    private boolean locked = false;

    @Column(name = "pending_approval_id")
    private Long pendingApprovalId;

    private String note;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_by") private Long updatedBy;

    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    @Transient private boolean profitMasked = false;

    @Transient
    public String getPaymentStatus() {
        if (unpaidAmount == null || unpaidAmount.compareTo(BigDecimal.ZERO) == 0) return "paid";
        if (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0) return "partial";
        return "unpaid";
    }

    @Transient
    public BigDecimal getTotalProfit() {
        if (profitMasked) return null;
        if (items == null) return BigDecimal.ZERO;
        return items.stream()
                .map(i -> i.getProfitAmount() != null ? i.getProfitAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
