package uz.bizcontrol.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductRequest {
    @NotBlank
    private String name;

    private String sku;
    private String barcode;
    private Long   categoryId;
    private String brand;
    private String unit = "piece";

    @NotNull
    @Positive(message = "Purchase price must be greater than zero")
    private BigDecimal purchasePrice;

    @NotNull
    @Positive(message = "Selling price must be greater than zero")
    private BigDecimal sellingPrice;

    @DecimalMin(value = "0", message = "Wholesale price cannot be negative")
    private BigDecimal wholesalePrice;

    @DecimalMin(value = "0", message = "Min stock level cannot be negative")
    private BigDecimal minStockLevel = BigDecimal.ZERO;

    private Long   supplierId;
    private String currency = "UZS";
    private String description;
    private String status = "active";
}
