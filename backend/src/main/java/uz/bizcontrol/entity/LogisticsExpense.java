package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single expense line on a logistics order (shipping, customs, broker, insurance, other).
 * Each line is paid from cash or bank at confirmation time and produces both a
 * {@link CashTransaction} row and a journal-entry line. Never affects supplier debt.
 */
@Entity
@Table(name = "logistics_expenses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LogisticsExpense {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "logistics_order_id", nullable = false)
    private Long logisticsOrderId;

    /** shipping | customs | broker | insurance | other */
    @Column(name = "expense_type", nullable = false, length = 40)
    private String expenseType;

    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String currency = "UZS";

    /** Rate from {@link #currency} to the parent order's currency. Equals 1 when same currency. */
    @Builder.Default
    @Column(name = "exchange_rate", precision = 18, scale = 6, nullable = false)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    /** {@code amount × exchangeRate} in the parent order's currency. Used by allocation + journal. */
    @Builder.Default
    @Column(name = "converted_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal convertedAmount = BigDecimal.ZERO;

    /** cash | bank — only meaningful when fully or partially paid on confirmation. */
    @Builder.Default
    @Column(name = "payment_source", nullable = false, length = 20)
    private String paymentSource = "cash";

    /** Set on confirmation: the cash/bank entry created for the paid portion (if any). */
    @Column(name = "cash_transaction_id")
    private Long cashTransactionId;

    /** Amount of {@link #amount} already settled in the expense's own currency. */
    @Builder.Default
    @Column(name = "paid_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** paid | partial | unpaid — derived from {@link #paidAmount}. */
    @Builder.Default
    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus = "paid";

    @Column(columnDefinition = "TEXT")
    private String note;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
