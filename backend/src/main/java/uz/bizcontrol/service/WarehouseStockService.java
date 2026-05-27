package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.exception.PendingApprovalException;
import uz.bizcontrol.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The authoritative engine for per-warehouse stock. Guarantees the invariant:
 * <pre>product.currentStock == Σ warehouseStock.quantity</pre>
 * Every IN / OUT / SET updates both the warehouse row and the product total and
 * writes a {@link StockMovement} (tagged with warehouseId) + an audit log.
 * TRANSFER moves quantity between two warehouses and leaves the product total
 * unchanged. Large adjustments are gated behind the existing approval workflow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseStockService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final StockTransferRepository stockTransferRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final CompanyService companyService;
    private final AuditService auditService;
    private final AlertService alertService;
    private final ApprovalService approvalService;

    // ── Lazy resolution ──────────────────────────────────────────────────────

    /** Returns the company's main warehouse, creating one on demand if none exists. */
    @Transactional
    public Warehouse ensureMain(Long companyId) {
        return warehouseRepository.findFirstByCompanyIdAndCode(companyId, "MAIN")
                .orElseGet(() -> {
                    List<Warehouse> all = warehouseRepository.findByCompanyIdOrderByNameAsc(companyId);
                    if (!all.isEmpty()) return all.get(0);
                    Company c = companyService.getById(companyId);
                    return warehouseRepository.save(Warehouse.builder()
                            .company(c).name("Main Warehouse").code("MAIN")
                            .type("main").status("active").note("Default warehouse (auto-created)")
                            .build());
                });
    }

    /** Resolve a requested warehouse id, falling back to the main warehouse. */
    @Transactional
    public Long resolveWarehouseId(Long companyId, Long requested) {
        if (requested != null) {
            return warehouseRepository.findByCompanyIdAndId(companyId, requested)
                    .map(Warehouse::getId)
                    .orElseGet(() -> ensureMain(companyId).getId());
        }
        return ensureMain(companyId).getId();
    }

    @Transactional
    public WarehouseStock getOrCreateRow(Long companyId, Long warehouseId, Long productId) {
        return warehouseStockRepository.findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseGet(() -> warehouseStockRepository.save(WarehouseStock.builder()
                        .companyId(companyId).warehouseId(warehouseId).productId(productId)
                        .quantity(BigDecimal.ZERO).reservedQuantity(BigDecimal.ZERO)
                        .build()));
    }

    public List<WarehouseStock> breakdownForProduct(Long companyId, Long productId) {
        return warehouseStockRepository.findByCompanyIdAndProductId(companyId, productId);
    }

    /** Available (unreserved) quantity of a product in a specific warehouse. */
    public BigDecimal availableInWarehouse(Long companyId, Long warehouseId, Long productId) {
        return warehouseStockRepository.findByWarehouseIdAndProductId(warehouseId, productId)
                .map(WarehouseStock::getAvailableQuantity).orElse(BigDecimal.ZERO);
    }

    // ── Low-level sync (used by sale / purchase / product adjust) ──────────────

    /**
     * Apply a signed delta to a warehouse row only — does NOT touch the product
     * total or create a movement (the caller already did). Used to keep the
     * warehouse breakdown in sync with the existing document flows.
     * Safe no-op when {@code warehouseId} is null (legacy path).
     */
    @Transactional
    public void syncWarehouseDelta(Long companyId, Long warehouseId, Long productId, BigDecimal delta) {
        if (warehouseId == null || delta == null || delta.signum() == 0) return;
        WarehouseStock row = getOrCreateRow(companyId, warehouseId, productId);
        BigDecimal next = row.getQuantity().add(delta);
        if (next.compareTo(BigDecimal.ZERO) < 0) next = BigDecimal.ZERO; // safety: never negative row
        row.setQuantity(next);
        warehouseStockRepository.save(row);
    }

    // ── Public stock operations (controller-facing) ───────────────────────────

    @Transactional
    public StockMovement stockIn(Long companyId, Long userId, Long productId, Long warehouseId,
                                 BigDecimal qty, String note, boolean allowLarge) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("Quantity must be greater than zero");
        return adjust(companyId, userId, productId, warehouseId, qty, "in", note, allowLarge);
    }

    @Transactional
    public StockMovement stockOut(Long companyId, Long userId, Long productId, Long warehouseId,
                                  BigDecimal qty, String note, boolean allowLarge, boolean allowNegative) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("Quantity must be greater than zero");
        if (!allowNegative) {
            BigDecimal available = availableInWarehouse(companyId, warehouseId, productId);
            if (available.compareTo(qty) < 0)
                throw new BusinessException("Insufficient available stock in warehouse. Available: " + available);
        }
        return adjust(companyId, userId, productId, warehouseId, qty.negate(), "out", note, allowLarge);
    }

    @Transactional
    public StockMovement stockSet(Long companyId, Long userId, Long productId, Long warehouseId,
                                  BigDecimal target, String note, boolean allowLarge) {
        if (target == null || target.compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("Target quantity cannot be negative");
        Long whId = resolveWarehouseId(companyId, warehouseId);
        WarehouseStock row = getOrCreateRow(companyId, whId, productId);
        BigDecimal delta = target.subtract(row.getQuantity());
        if (delta.signum() == 0) {
            throw new BusinessException("Stock is already " + target + " in this warehouse");
        }
        return adjust(companyId, userId, productId, whId, delta, "adjustment", note, allowLarge);
    }

    /**
     * Core signed adjust: archived-warehouse guard, large-adjustment approval gate,
     * then apply (warehouse row + product total + movement + audit + alerts).
     */
    @Transactional
    StockMovement adjust(Long companyId, Long userId, Long productId, Long warehouseId,
                         BigDecimal delta, String movementType, String note, boolean allowLarge) {
        Long whId = resolveWarehouseId(companyId, warehouseId);
        Warehouse wh = requireWarehouse(companyId, whId);
        Product product = productRepository.findByCompanyIdAndId(companyId, productId)
                .orElseThrow(() -> BusinessException.notFound("Product"));

        if (delta.signum() > 0 && "archived".equals(wh.getStatus()))
            throw new BusinessException("Archived warehouse cannot receive new stock");

        // Large adjustment → defer to approval (OWNER/allowLarge applies immediately)
        if (!allowLarge && approvalService.isLargeStockAdjust(delta)) {
            if (approvalService.hasPendingFor(companyId, "WarehouseStock", productId)) {
                throw new BusinessException("A large stock-adjustment approval is already pending for this product.");
            }
            String payload = "{\"action\":\"WAREHOUSE_STOCK_ADJUST\""
                    + ",\"companyId\":\"" + companyId + "\""
                    + ",\"requesterId\":\"" + userId + "\""
                    + ",\"productId\":\"" + productId + "\""
                    + ",\"warehouseId\":\"" + whId + "\""
                    + ",\"delta\":\"" + delta.toPlainString() + "\""
                    + ",\"movementType\":\"" + movementType + "\""
                    + ",\"note\":\"" + (note != null ? note.replace("\"", "'") : "") + "\"}";
            ApprovalRequest ar = approvalService.requestDeferred(
                    companyId, userId, "LARGE_STOCK_ADJUST", "WarehouseStock", productId,
                    "Warehouse stock adjustment of " + delta + " on '" + product.getName() + "' @ " + wh.getName(),
                    approvalService.buildMetadata(Map.of(
                            "productId", productId, "warehouseId", whId,
                            "delta", delta, "productName", product.getName(), "warehouse", wh.getName())),
                    payload);
            throw new PendingApprovalException(
                    "Stock adjustment of " + delta + " for '" + product.getName()
                            + "' requires manager/owner approval", ar.getId());
        }

        return applyAdjustInternal(companyId, userId, product, wh, delta, movementType, note);
    }

    /** Applies an approved/permitted adjustment (no further approval gate). */
    @Transactional
    public StockMovement applyApprovedAdjust(Long companyId, Long userId, Long productId, Long warehouseId,
                                             BigDecimal delta, String movementType, String note) {
        Warehouse wh = requireWarehouse(companyId, warehouseId);
        Product product = productRepository.findByCompanyIdAndId(companyId, productId)
                .orElseThrow(() -> BusinessException.notFound("Product"));
        return applyAdjustInternal(companyId, userId, product, wh, delta, movementType, note);
    }

    private StockMovement applyAdjustInternal(Long companyId, Long userId, Product product, Warehouse wh,
                                              BigDecimal delta, String movementType, String note) {
        WarehouseStock row = getOrCreateRow(companyId, wh.getId(), product.getId());
        BigDecimal prevWh = row.getQuantity();
        BigDecimal newWh = prevWh.add(delta);
        if (newWh.compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("Stock cannot be negative in warehouse '" + wh.getName()
                    + "'. Available: " + prevWh);

        BigDecimal prevTotal = product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO;
        BigDecimal newTotal = prevTotal.add(delta);
        if (newTotal.compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("Total stock cannot be negative");

        row.setQuantity(newWh);
        warehouseStockRepository.save(row);
        product.setCurrentStock(newTotal);
        productRepository.save(product);

        StockMovement movement = stockMovementRepository.save(StockMovement.builder()
                .company(product.getCompany())
                .product(product)
                .warehouseId(wh.getId())
                .movementType(movementType)
                .quantity(delta)
                .previousStock(prevWh)
                .newStock(newWh)
                .referenceType("warehouse_adjust")
                .note(note)
                .createdBy(userId)
                .build());

        auditService.log(companyId, userId, "WAREHOUSE_STOCK_" + movementType.toUpperCase(),
                "WarehouseStock", product.getId(),
                "wh=" + wh.getName() + " qty=" + prevWh, "wh=" + wh.getName() + " qty=" + newWh);

        checkLowStock(product, companyId);
        return movement;
    }

    // ── Transfer ──────────────────────────────────────────────────────────────

    @Transactional
    public StockTransfer transfer(Long companyId, Long userId, Long productId,
                                  Long fromWarehouseId, Long toWarehouseId, BigDecimal qty, String note) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("Transfer quantity must be greater than zero");
        if (fromWarehouseId == null || toWarehouseId == null)
            throw new BusinessException("Both source and destination warehouses are required");
        if (fromWarehouseId.equals(toWarehouseId))
            throw new BusinessException("Source and destination warehouses must be different");

        Warehouse from = requireWarehouse(companyId, fromWarehouseId);
        Warehouse to = requireWarehouse(companyId, toWarehouseId);
        if ("archived".equals(to.getStatus()))
            throw new BusinessException("Archived warehouse cannot receive new stock");
        Product product = productRepository.findByCompanyIdAndId(companyId, productId)
                .orElseThrow(() -> BusinessException.notFound("Product"));

        WarehouseStock fromRow = getOrCreateRow(companyId, fromWarehouseId, productId);
        if (fromRow.getAvailableQuantity().compareTo(qty) < 0)
            throw new BusinessException("Insufficient available stock in '" + from.getName()
                    + "' to transfer. Available: " + fromRow.getAvailableQuantity());

        StockTransfer transfer = stockTransferRepository.save(StockTransfer.builder()
                .companyId(companyId)
                .fromWarehouseId(fromWarehouseId)
                .toWarehouseId(toWarehouseId)
                .productId(productId)
                .quantity(qty)
                .status("completed")
                .note(note)
                .createdBy(userId)
                .completedAt(LocalDateTime.now())
                .build());

        // Source leg (product total unchanged on a transfer)
        BigDecimal fromPrev = fromRow.getQuantity();
        BigDecimal fromNew = fromPrev.subtract(qty);
        fromRow.setQuantity(fromNew);
        warehouseStockRepository.save(fromRow);
        stockMovementRepository.save(StockMovement.builder()
                .company(product.getCompany()).product(product).warehouseId(fromWarehouseId)
                .movementType("transfer_out").quantity(qty.negate())
                .previousStock(fromPrev).newStock(fromNew)
                .referenceType("transfer").referenceId(transfer.getId())
                .note("Transfer to " + to.getName() + (note != null ? " — " + note : ""))
                .createdBy(userId).build());

        // Destination leg
        WarehouseStock toRow = getOrCreateRow(companyId, toWarehouseId, productId);
        BigDecimal toPrev = toRow.getQuantity();
        BigDecimal toNew = toPrev.add(qty);
        toRow.setQuantity(toNew);
        warehouseStockRepository.save(toRow);
        stockMovementRepository.save(StockMovement.builder()
                .company(product.getCompany()).product(product).warehouseId(toWarehouseId)
                .movementType("transfer_in").quantity(qty)
                .previousStock(toPrev).newStock(toNew)
                .referenceType("transfer").referenceId(transfer.getId())
                .note("Transfer from " + from.getName() + (note != null ? " — " + note : ""))
                .createdBy(userId).build());

        auditService.log(companyId, userId, "STOCK_TRANSFER", "StockTransfer", transfer.getId(),
                from.getName() + " -" + qty, to.getName() + " +" + qty);
        return transfer;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Warehouse requireWarehouse(Long companyId, Long warehouseId) {
        return warehouseRepository.findByCompanyIdAndId(companyId, warehouseId)
                .orElseThrow(() -> BusinessException.notFound("Warehouse"));
    }

    private void checkLowStock(Product p, Long companyId) {
        if (p.getCurrentStock().compareTo(BigDecimal.ZERO) == 0) {
            alertService.create(companyId, "out_of_stock", "Out of stock: " + p.getName(),
                    "Product '" + p.getName() + "' is out of stock", "Product", p.getId());
        } else if (p.getMinStockLevel() != null && p.getCurrentStock().compareTo(p.getMinStockLevel()) <= 0) {
            alertService.create(companyId, "low_stock", "Low stock: " + p.getName(),
                    "Product '" + p.getName() + "' has low stock: " + p.getCurrentStock() + " " + p.getUnit(),
                    "Product", p.getId());
        }
    }
}
