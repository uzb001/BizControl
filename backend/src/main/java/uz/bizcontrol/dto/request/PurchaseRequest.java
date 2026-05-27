package uz.bizcontrol.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseRequest {
    private Long supplierId;

    /** Receiving warehouse to add stock into (optional → company main warehouse). */
    private Long warehouseId;

    @NotNull
    private LocalDateTime purchaseDate;

    @NotEmpty
    @Valid
    private List<PurchaseItemRequest> items;

    @DecimalMin(value = "0", message = "Discount cannot be negative")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "Additional cost cannot be negative")
    private BigDecimal additionalCost = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "Paid amount cannot be negative")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    private String paymentMethod = "cash";
    private String currency = "UZS";
    private String note;

    @Data
    public static class PurchaseItemRequest {
        @NotNull
        private Long productId;

        @NotNull
        @Positive(message = "Quantity must be greater than zero")
        private BigDecimal quantity;

        @NotNull
        @Positive(message = "Purchase price must be greater than zero")
        private BigDecimal purchasePrice;

        @DecimalMin(value = "0", message = "Item discount cannot be negative")
        private BigDecimal discountAmount = BigDecimal.ZERO;
    }
}
