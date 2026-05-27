package uz.bizcontrol.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SaleRequest {
    private Long customerId;

    /** Source warehouse to deduct stock from (optional → company main warehouse). */
    private Long warehouseId;

    @NotNull
    private LocalDateTime saleDate;

    @NotEmpty
    @Valid
    private List<SaleItemRequest> items;

    /** Header-level discount; must be zero or positive. */
    @DecimalMin(value = "0", message = "Discount cannot be negative")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Amount paid immediately; must be zero or positive. */
    @DecimalMin(value = "0", message = "Paid amount cannot be negative")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    private String paymentMethod = "cash";
    private String currency = "UZS";
    private String note;

    @Data
    public static class SaleItemRequest {
        @NotNull
        private Long productId;

        @NotNull
        @Positive(message = "Quantity must be greater than zero")
        private BigDecimal quantity;

        @NotNull
        @Positive(message = "Selling price must be greater than zero")
        private BigDecimal sellingPrice;

        @DecimalMin(value = "0", message = "Item discount cannot be negative")
        private BigDecimal discountAmount = BigDecimal.ZERO;
    }
}
