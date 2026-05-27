package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A settlement against an unpaid or partially-paid {@link LogisticsExpense}.
 * Each payment writes a {@link CashTransaction} and a single balanced journal
 * entry (DR Logistics Payable / CR Cash·Bank), and reduces the parent
 * expense's outstanding balance.
 */
@Entity
@Table(name = "logistics_expense_payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LogisticsExpensePayment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "logistics_order_id", nullable = false)
    private Long logisticsOrderId;

    @Column(name = "logistics_expense_id", nullable = false)
    private Long logisticsExpenseId;

    /** Amount of this settlement in the expense's original currency. */
    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Builder.Default
    @Column(name = "exchange_rate", precision = 18, scale = 6, nullable = false)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "converted_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal convertedAmount;

    @Column(name = "payment_source", nullable = false, length = 20)
    private String paymentSource;

    @Column(name = "cash_transaction_id")
    private Long cashTransactionId;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /**
     * Signed FX delta in the order (base) currency (V20+):
     * <ul>
     *   <li>&gt; 0 — FX <b>loss</b> (paid more than booked → DR FX_DIFFERENCE)</li>
     *   <li>&lt; 0 — FX <b>gain</b> (paid less than booked → CR FX_DIFFERENCE)</li>
     *   <li>= 0 — same rate as booked, or cross-currency exact match (no FX line)</li>
     * </ul>
     */
    @Builder.Default
    @Column(name = "fx_delta", precision = 18, scale = 2)
    private BigDecimal fxDelta = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Builder.Default
    @Column(name = "paid_at")
    private LocalDateTime paidAt = LocalDateTime.now();

    @Column(name = "paid_by")
    private Long paidBy;
}
