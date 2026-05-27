package uz.bizcontrol.entity;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @deprecated Replaced by {@link CashTransaction} with paymentSource="bank".
 * This class is kept only for compilation compatibility and will be removed.
 */
@Deprecated
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @lombok.Builder
public class BankTransaction {
    private Long id;
    private String transactionType;
    private BigDecimal amount;
    private String currency = "UZS";
    private String category;
    private Long relatedSaleId;
    private Long relatedPurchaseId;
    private Long relatedCustomerId;
    private Long relatedSupplierId;
    private LocalDateTime transactionDate;
    private String note;
    private String status = "active";
    private Long createdBy;
    // company field removed — use CashTransaction directly
}
