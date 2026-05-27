package uz.bizcontrol.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Validated request body for manual money transactions (cashbox and bank).
 * Raw-entity endpoints are replaced with this DTO to prevent mass-assignment
 * and enforce business-level constraints on the way in.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MoneyTransactionRequest {

    /** income | expense */
    @NotBlank(message = "transactionType is required")
    @Pattern(regexp = "income|expense",
             message = "transactionType must be 'income' or 'expense'")
    private String transactionType;

    /**
     * cash | bank | card | click | payme
     * The bank controller forces this to "bank" regardless.
     */
    @Pattern(regexp = "cash|bank|card|click|payme",
             message = "paymentSource must be one of: cash, bank, card, click, payme")
    private String paymentSource = "cash";

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    @Digits(integer = 15, fraction = 2, message = "amount has too many digits")
    private BigDecimal amount;

    @Pattern(regexp = "UZS|USD|EUR|RUB", message = "currency must be UZS, USD, EUR, or RUB")
    private String currency = "UZS";

    @Size(max = 100, message = "category must not exceed 100 characters")
    private String category;

    private Long relatedSaleId;
    private Long relatedPurchaseId;
    private Long relatedCustomerId;
    private Long relatedSupplierId;

    private LocalDateTime transactionDate;

    @Size(max = 1000, message = "note must not exceed 1000 characters")
    private String note;
}
