package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String name;

    private String sku;
    private String barcode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    private String brand;
    private String unit = "piece";

    @Column(name = "purchase_price")
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @Column(name = "selling_price")
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    @Column(name = "wholesale_price")
    private BigDecimal wholesalePrice;

    @Column(name = "min_stock_level")
    private BigDecimal minStockLevel = BigDecimal.ZERO;

    @Column(name = "current_stock")
    private BigDecimal currentStock = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    private String currency = "UZS";
    private String description;
    private String status = "active";

    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_by") private Long updatedBy;

    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    @Transient
    public BigDecimal getMarginPercent() {
        if (purchasePrice == null || purchasePrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return sellingPrice.subtract(purchasePrice)
                .multiply(new BigDecimal("100"))
                .divide(purchasePrice, 2, java.math.RoundingMode.HALF_UP);
    }
}
