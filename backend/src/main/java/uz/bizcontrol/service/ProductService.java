package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.dto.request.ProductRequest;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.entity.ApprovalRequest;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.exception.PendingApprovalException;
import uz.bizcontrol.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CompanyService companyService;
    private final AuditService auditService;
    private final AlertService alertService;
    private final ApprovalService approvalService;
    private final WarehouseStockService warehouseStockService;

    @Transactional
    public Product create(Long companyId, Long userId, ProductRequest req) {
        if (req.getSku() != null && productRepository.existsByCompanyIdAndSku(companyId, req.getSku())) {
            throw new BusinessException("SKU already exists");
        }
        Company company = companyService.getById(companyId);

        Product product = Product.builder()
                .company(company)
                .name(req.getName())
                .sku(req.getSku())
                .barcode(req.getBarcode())
                .brand(req.getBrand())
                .unit(req.getUnit() != null ? req.getUnit() : "piece")
                .purchasePrice(req.getPurchasePrice())
                .sellingPrice(req.getSellingPrice())
                .wholesalePrice(req.getWholesalePrice())
                .minStockLevel(req.getMinStockLevel() != null ? req.getMinStockLevel() : BigDecimal.ZERO)
                .currentStock(BigDecimal.ZERO)
                .currency(req.getCurrency() != null ? req.getCurrency() : "UZS")
                .description(req.getDescription())
                .status(req.getStatus() != null ? req.getStatus() : "active")
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        if (req.getCategoryId() != null) {
            categoryRepository.findByCompanyIdAndId(companyId, req.getCategoryId())
                    .ifPresent(product::setCategory);
        }
        if (req.getSupplierId() != null) {
            supplierRepository.findByCompanyIdAndId(companyId, req.getSupplierId())
                    .ifPresent(product::setSupplier);
        }

        product = productRepository.save(product);
        auditService.log(companyId, userId, "CREATE", "Product", product.getId(), null, product.getName());

        if (req.getSellingPrice().compareTo(req.getPurchasePrice()) < 0) {
            alertService.create(companyId, "low_margin", "Product selling price below cost",
                    "Product '" + product.getName() + "' selling price is lower than purchase price",
                    "Product", product.getId());
        }
        return product;
    }

    public Page<Product> list(Long companyId, Specification<Product> spec, Pageable pageable) {
        Specification<Product> withCompany = Specification.<Product>where(
                (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId)
        ).and(spec);
        return productRepository.findAll(withCompany, pageable);
    }

    public Product getOne(Long companyId, Long id) {
        return productRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> BusinessException.notFound("Product"));
    }

    @Transactional
    public Product update(Long companyId, Long userId, Long id, ProductRequest req) {
        Product product = getOne(companyId, id);
        String oldValue = product.getName() + " price=" + product.getSellingPrice();

        product.setName(req.getName());
        if (req.getSku() != null && !req.getSku().equals(product.getSku())) {
            if (productRepository.existsByCompanyIdAndSku(companyId, req.getSku()))
                throw new BusinessException("SKU already exists");
            product.setSku(req.getSku());
        }
        product.setBarcode(req.getBarcode());
        product.setBrand(req.getBrand());
        if (req.getUnit() != null) product.setUnit(req.getUnit());
        product.setPurchasePrice(req.getPurchasePrice());
        product.setSellingPrice(req.getSellingPrice());
        product.setWholesalePrice(req.getWholesalePrice());
        if (req.getMinStockLevel() != null) product.setMinStockLevel(req.getMinStockLevel());
        if (req.getCurrency() != null) product.setCurrency(req.getCurrency());
        product.setDescription(req.getDescription());
        if (req.getStatus() != null) product.setStatus(req.getStatus());
        product.setUpdatedBy(userId);

        if (req.getCategoryId() != null) {
            categoryRepository.findByCompanyIdAndId(companyId, req.getCategoryId())
                    .ifPresent(product::setCategory);
        }
        if (req.getSupplierId() != null) {
            supplierRepository.findByCompanyIdAndId(companyId, req.getSupplierId())
                    .ifPresent(product::setSupplier);
        }

        product = productRepository.save(product);
        auditService.log(companyId, userId, "UPDATE", "Product", product.getId(), oldValue, product.getName() + " price=" + product.getSellingPrice());
        return product;
    }

    @Transactional
    public void deactivate(Long companyId, Long userId, Long id) {
        Product product = getOne(companyId, id);
        product.setStatus("inactive");
        product.setUpdatedBy(userId);
        productRepository.save(product);
        auditService.log(companyId, userId, "DEACTIVATE", "Product", id, "active", "inactive");
    }

    /** Backward-compatible entry point — large adjustments stay gated behind approval. */
    @Transactional
    public StockMovement adjustStock(Long companyId, Long userId, Long productId, BigDecimal quantity, String note) {
        return adjustStock(companyId, userId, productId, quantity, note, false);
    }

    /**
     * Public stock-adjustment endpoint.
     * Signed quantity: positive = stock IN, negative = stock OUT. SET is performed by the
     * caller computing the delta (target − current).
     * <p>When {@code allowLarge} is false, large adjustments (|qty| ≥ 100) are deferred to a
     * LARGE_STOCK_ADJUST approval (→ HTTP 202). OWNER passes {@code allowLarge=true} and applies
     * any size immediately. Negative resulting stock is always rejected.</p>
     */
    @Transactional
    public StockMovement adjustStock(Long companyId, Long userId, Long productId, BigDecimal quantity, String note, boolean allowLarge) {
        Product product = getOne(companyId, productId);
        BigDecimal prev = product.getCurrentStock();
        BigDecimal newStock = prev.add(quantity);
        if (newStock.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("Stock cannot be negative");

        if (!allowLarge && approvalService.isLargeStockAdjust(quantity)) {
            if (approvalService.hasPendingFor(companyId, "Product", productId)) {
                throw new BusinessException("A large stock-adjustment approval is already pending for this product.");
            }
            String payload = "{\"action\":\"LARGE_STOCK_ADJUST\""
                    + ",\"companyId\":\"" + companyId + "\""
                    + ",\"requesterId\":\"" + userId + "\""
                    + ",\"productId\":\"" + productId + "\""
                    + ",\"quantity\":\"" + quantity.toPlainString() + "\""
                    + ",\"note\":\"" + (note != null ? note.replace("\"", "'") : "") + "\"}";

            ApprovalRequest ar = approvalService.requestDeferred(
                    companyId, userId, "LARGE_STOCK_ADJUST", "Product", productId,
                    "Stock adjustment of " + quantity + " on product '" + product.getName() + "'",
                    approvalService.buildMetadata(Map.of(
                            "productId", productId, "quantity", quantity,
                            "previousStock", prev, "productName", product.getName())),
                    payload);

            // Do NOT update stock — throw to return 202
            throw new PendingApprovalException(
                    "Stock adjustment of " + quantity + " for '" + product.getName()
                            + "' requires manager/owner approval",
                    ar.getId());
        }

        return adjustStockDirectly(companyId, userId, productId, quantity, note);
    }

    /**
     * Applies a stock adjustment without the approval gate.
     * Called by {@link DeferredActionService} after LARGE_STOCK_ADJUST is approved,
     * and internally for small adjustments (already gated above).
     */
    @Transactional
    StockMovement adjustStockDirectly(Long companyId, Long userId, Long productId, BigDecimal quantity, String note) {
        Product product = getOne(companyId, productId);
        BigDecimal prev = product.getCurrentStock();
        BigDecimal newStock = prev.add(quantity);
        if (newStock.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("Stock cannot be negative");

        Long whId = warehouseStockService.resolveWarehouseId(companyId, null);

        product.setCurrentStock(newStock);
        productRepository.save(product);

        StockMovement movement = StockMovement.builder()
                .company(product.getCompany())
                .product(product)
                .warehouseId(whId)
                .movementType("adjustment")
                .quantity(quantity)
                .previousStock(prev)
                .newStock(newStock)
                .note(note)
                .createdBy(userId)
                .build();
        movement = stockMovementRepository.save(movement);
        warehouseStockService.syncWarehouseDelta(companyId, whId, productId, quantity);
        auditService.log(companyId, userId, "STOCK_ADJUST", "Product", productId,
                "stock=" + prev, "stock=" + newStock);
        checkLowStock(product, companyId);
        return movement;
    }

    private void checkLowStock(Product p, Long companyId) {
        if (p.getCurrentStock().compareTo(BigDecimal.ZERO) == 0) {
            alertService.create(companyId, "out_of_stock", "Out of stock: " + p.getName(),
                    "Product '" + p.getName() + "' is out of stock", "Product", p.getId());
        } else if (p.getCurrentStock().compareTo(p.getMinStockLevel()) <= 0) {
            alertService.create(companyId, "low_stock", "Low stock: " + p.getName(),
                    "Product '" + p.getName() + "' has low stock: " + p.getCurrentStock() + " " + p.getUnit(),
                    "Product", p.getId());
        }
    }
}
