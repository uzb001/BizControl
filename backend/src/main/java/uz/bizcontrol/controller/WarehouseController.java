package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.StockMovement;
import uz.bizcontrol.entity.StockTransfer;
import uz.bizcontrol.entity.Warehouse;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.StockMovementRepository;
import uz.bizcontrol.repository.StockTransferRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;
import uz.bizcontrol.service.WarehouseService;
import uz.bizcontrol.service.WarehouseStockService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseStockService warehouseStockService;
    private final StockMovementRepository stockMovementRepository;
    private final StockTransferRepository stockTransferRepository;
    private final PermissionService permissionService;

    // ── Warehouse CRUD ────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Warehouse>> list(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "warehouses.view");
        return ResponseEntity.ok(warehouseService.list(p.getCompanyId()));
    }

    @GetMapping("/summary")
    public ResponseEntity<List<Map<String, Object>>> summary(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "warehouses.view");
        boolean canValue = permissionService.hasPermission(p, "products.view_purchase_price");
        return ResponseEntity.ok(warehouseService.summaries(p.getCompanyId(), canValue));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Warehouse> get(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "warehouses.view");
        return ResponseEntity.ok(warehouseService.getOne(p.getCompanyId(), id));
    }

    @PostMapping
    public ResponseEntity<Warehouse> create(@AuthenticationPrincipal BizControlPrincipal p,
                                            @RequestBody Map<String, Object> body) {
        permissionService.require(p, "warehouses.create");
        return ResponseEntity.ok(warehouseService.create(p.getCompanyId(), p.getUserId(), body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Warehouse> update(@AuthenticationPrincipal BizControlPrincipal p,
                                            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        permissionService.require(p, "warehouses.edit");
        return ResponseEntity.ok(warehouseService.update(p.getCompanyId(), p.getUserId(), id, body));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Warehouse> archive(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "warehouses.archive");
        return ResponseEntity.ok(warehouseService.archive(p.getCompanyId(), p.getUserId(), id));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Warehouse> restore(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "warehouses.archive");
        return ResponseEntity.ok(warehouseService.restore(p.getCompanyId(), p.getUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "warehouses.archive");
        warehouseService.delete(p.getCompanyId(), p.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Warehouse stock views ─────────────────────────────────────────────────

    @GetMapping("/{id}/stock")
    public ResponseEntity<List<Map<String, Object>>> warehouseStock(
            @AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "warehouse_stock.view");
        boolean canValue = permissionService.hasPermission(p, "products.view_purchase_price");
        return ResponseEntity.ok(warehouseService.warehouseStock(p.getCompanyId(), id, canValue));
    }

    @GetMapping("/stock/product/{productId}")
    public ResponseEntity<?> productBreakdown(
            @AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long productId) {
        permissionService.require(p, "warehouse_stock.view");
        return ResponseEntity.ok(warehouseStockService.breakdownForProduct(p.getCompanyId(), productId));
    }

    // ── Stock operations ──────────────────────────────────────────────────────

    @PostMapping("/stock/in")
    public ResponseEntity<StockMovement> stockIn(@AuthenticationPrincipal BizControlPrincipal p,
                                                 @RequestBody Map<String, Object> body) {
        permissionService.require(p, "warehouse_stock.adjust");
        return ResponseEntity.ok(warehouseStockService.stockIn(
                p.getCompanyId(), p.getUserId(), reqLong(body, "productId"), reqLong(body, "warehouseId"),
                bd(body.get("quantity")), str(body.get("note")), p.isOwner()));
    }

    @PostMapping("/stock/out")
    public ResponseEntity<StockMovement> stockOut(@AuthenticationPrincipal BizControlPrincipal p,
                                                  @RequestBody Map<String, Object> body) {
        permissionService.require(p, "warehouse_stock.adjust");
        return ResponseEntity.ok(warehouseStockService.stockOut(
                p.getCompanyId(), p.getUserId(), reqLong(body, "productId"), reqLong(body, "warehouseId"),
                bd(body.get("quantity")), str(body.get("note")), p.isOwner(), false));
    }

    @PostMapping("/stock/set")
    public ResponseEntity<StockMovement> stockSet(@AuthenticationPrincipal BizControlPrincipal p,
                                                  @RequestBody Map<String, Object> body) {
        permissionService.require(p, "warehouse_stock.adjust");
        return ResponseEntity.ok(warehouseStockService.stockSet(
                p.getCompanyId(), p.getUserId(), reqLong(body, "productId"), reqLong(body, "warehouseId"),
                bd(body.get("quantity")), str(body.get("note")), p.isOwner()));
    }

    @PostMapping("/stock/transfer")
    public ResponseEntity<StockTransfer> transfer(@AuthenticationPrincipal BizControlPrincipal p,
                                                  @RequestBody Map<String, Object> body) {
        permissionService.require(p, "warehouse_stock.transfer");
        return ResponseEntity.ok(warehouseStockService.transfer(
                p.getCompanyId(), p.getUserId(), reqLong(body, "productId"),
                reqLong(body, "fromWarehouseId"), reqLong(body, "toWarehouseId"),
                bd(body.get("quantity")), str(body.get("note"))));
    }

    // ── History ───────────────────────────────────────────────────────────────

    @GetMapping("/movements")
    public ResponseEntity<Page<StockMovement>> movements(
            @AuthenticationPrincipal BizControlPrincipal p,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        permissionService.require(p, "warehouse_stock.view_movements");
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<StockMovement> result = warehouseId != null
                ? stockMovementRepository.findByCompanyIdAndWarehouseId(p.getCompanyId(), warehouseId, pageable)
                : stockMovementRepository.findByCompanyId(p.getCompanyId(), pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/transfers")
    public ResponseEntity<Page<StockTransfer>> transfers(
            @AuthenticationPrincipal BizControlPrincipal p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        permissionService.require(p, "warehouse_stock.view_movements");
        return ResponseEntity.ok(stockTransferRepository.findByCompanyIdOrderByCreatedAtDesc(
                p.getCompanyId(), PageRequest.of(page, size)));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String str(Object o) { return o != null ? o.toString() : null; }

    private static Long reqLong(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) {
            if ("warehouseId".equals(key)) return null; // optional → resolves to main
            throw new BusinessException(key + " is required");
        }
        return Long.parseLong(v.toString());
    }

    private static BigDecimal bd(Object o) {
        if (o == null) throw new BusinessException("quantity is required");
        return new BigDecimal(o.toString());
    }
}
