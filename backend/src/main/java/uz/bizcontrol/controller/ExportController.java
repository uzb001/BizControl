package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.repository.*;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.AccessLogService;
import uz.bizcontrol.service.CompanyService;
import uz.bizcontrol.service.ExcelExportService;
import uz.bizcontrol.service.PermissionService;
import uz.bizcontrol.service.WarehouseService;
import uz.bizcontrol.production.BomComponentRepository;
import uz.bizcontrol.production.BomTemplate;
import uz.bizcontrol.production.BomTemplateRepository;
import uz.bizcontrol.production.ProductionOrder;
import uz.bizcontrol.production.ProductionOrderRepository;
import uz.bizcontrol.production.WasteRecord;
import uz.bizcontrol.production.WasteRecordRepository;
import uz.bizcontrol.util.SaleSpec;
import uz.bizcontrol.util.PurchaseSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExcelExportService excelExportService;
    private final CompanyService companyService;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final DebtRepository debtRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final PermissionService permissionService;
    private final AccessLogService accessLogService;
    private final WarehouseService warehouseService;
    private final ProductionOrderRepository productionOrderRepository;
    private final BomTemplateRepository bomTemplateRepository;
    private final BomComponentRepository bomComponentRepository;
    private final WasteRecordRepository wasteRecordRepository;

    @GetMapping("/products")
    public ResponseEntity<byte[]> exportProducts(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId) throws Exception {
        permissionService.require(principal, "products.export");
        String name = companyService.getById(principal.getCompanyId()).getName();
        byte[] data = excelExportService.exportProducts(principal.getCompanyId(), name);
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "products.export", "export");
        return build(data, "products_" + today() + ".xlsx");
    }

    @GetMapping("/customers")
    public ResponseEntity<byte[]> exportCustomers(@AuthenticationPrincipal BizControlPrincipal principal) throws Exception {
        permissionService.require(principal, "customers.export");
        String name = companyService.getById(principal.getCompanyId()).getName();
        byte[] data = excelExportService.exportCustomers(principal.getCompanyId(), name);
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "customers.export", "export");
        return build(data, "customers_" + today() + ".xlsx");
    }

    @GetMapping("/suppliers")
    public ResponseEntity<byte[]> exportSuppliers(@AuthenticationPrincipal BizControlPrincipal principal) throws Exception {
        permissionService.require(principal, "suppliers.export");
        String name = companyService.getById(principal.getCompanyId()).getName();
        byte[] data = excelExportService.exportSuppliers(principal.getCompanyId(), name);
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "suppliers.export", "export");
        return build(data, "suppliers_" + today() + ".xlsx");
    }

    @GetMapping("/sales")
    public ResponseEntity<byte[]> exportSales(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) throws Exception {

        permissionService.require(principal, "sales.export");
        String name = companyService.getById(principal.getCompanyId()).getName();
        var spec = SaleSpec.build(search, customerId, paymentStatus, paymentMethod, fromDate, toDate);
        var withCompany = org.springframework.data.jpa.domain.Specification.where(
                (org.springframework.data.jpa.domain.Specification<Sale>)
                        (root, q, cb) -> cb.equal(root.get("company").get("id"), principal.getCompanyId())
        ).and(spec);
        List<Sale> sales = saleRepository.findAll(withCompany, Sort.by("saleDate").descending());
        byte[] data = excelExportService.exportSales(principal.getCompanyId(), name, sales);
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "sales.export", "export");
        return build(data, "sales_" + today() + ".xlsx");
    }

    @GetMapping("/purchases")
    public ResponseEntity<byte[]> exportPurchases(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) throws Exception {

        permissionService.require(principal, "purchases.export");
        String name = companyService.getById(principal.getCompanyId()).getName();
        var spec = PurchaseSpec.build(search, supplierId, paymentStatus, null, fromDate, toDate);
        var withCompany = org.springframework.data.jpa.domain.Specification.where(
                (org.springframework.data.jpa.domain.Specification<Purchase>)
                        (root, q, cb) -> cb.equal(root.get("company").get("id"), principal.getCompanyId())
        ).and(spec);
        List<Purchase> purchases = purchaseRepository.findAll(withCompany, Sort.by("purchaseDate").descending());
        byte[] data = excelExportService.exportPurchases(principal.getCompanyId(), name, purchases);
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "purchases.export", "export");
        return build(data, "purchases_" + today() + ".xlsx");
    }

    @GetMapping("/debts")
    public ResponseEntity<byte[]> exportDebts(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String debtType,
            @RequestParam(required = false) String status) throws Exception {

        permissionService.require(principal, "debts.export");
        String name = companyService.getById(principal.getCompanyId()).getName();
        var spec = buildDebtSpec(principal.getCompanyId(), debtType, status);
        List<Debt> debts = debtRepository.findAll(spec);
        byte[] data = excelExportService.exportDebts(principal.getCompanyId(), name, debts);
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "debts.export", "export");
        return build(data, "debts_" + today() + ".xlsx");
    }

    @GetMapping("/cashbox")
    public ResponseEntity<byte[]> exportCashbox(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String paymentSource,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) throws Exception {

        permissionService.require(principal, "cashbox.export");
        String name = companyService.getById(principal.getCompanyId()).getName();
        var spec = buildCashSpec(principal.getCompanyId(), transactionType, paymentSource, fromDate, toDate);
        List<CashTransaction> txns = cashTransactionRepository.findAll(spec, Sort.by("transactionDate").descending());
        byte[] data = excelExportService.exportCashTransactions(principal.getCompanyId(), name, txns);
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "cashbox.export", "export");
        return build(data, "cashbox_" + today() + ".xlsx");
    }

    @GetMapping("/bank")
    public ResponseEntity<byte[]> exportBank(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) throws Exception {

        permissionService.require(principal, "bank.export");
        String name = companyService.getById(principal.getCompanyId()).getName();
        var spec = buildCashSpec(principal.getCompanyId(), transactionType, "bank", fromDate, toDate);
        List<CashTransaction> txns = cashTransactionRepository.findAll(spec, Sort.by("transactionDate").descending());
        byte[] data = excelExportService.exportCashTransactions(principal.getCompanyId(), name, txns);
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "bank.export", "export");
        return build(data, "bank_" + today() + ".xlsx");
    }

    @GetMapping("/stock")
    public ResponseEntity<byte[]> exportStock(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String stockStatus) throws Exception {

        permissionService.require(principal, "stock.export");
        String name = companyService.getById(principal.getCompanyId()).getName();
        var spec = uz.bizcontrol.util.ProductSpec.build(null, "active", null, null, stockStatus);
        var withCompany = org.springframework.data.jpa.domain.Specification.where(
                (org.springframework.data.jpa.domain.Specification<Product>)
                        (root, q, cb) -> cb.equal(root.get("company").get("id"), principal.getCompanyId())
        ).and(spec);
        List<Product> products = productRepository.findAll(withCompany);
        byte[] data = excelExportService.exportStock(principal.getCompanyId(), name, products);
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "stock.export", "export");
        return build(data, "stock_" + today() + ".xlsx");
    }

    @GetMapping("/warehouse-stock")
    public ResponseEntity<byte[]> exportWarehouseStock(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) Long warehouseId) throws Exception {

        permissionService.require(principal, "warehouse_stock.export");
        Long cid = principal.getCompanyId();
        String name = companyService.getById(cid).getName();
        boolean canValue = permissionService.hasPermission(principal, "products.view_purchase_price");

        List<Warehouse> targets = warehouseId != null
                ? List.of(warehouseService.getOne(cid, warehouseId))
                : warehouseService.list(cid);

        List<Object[]> rows = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalVal = BigDecimal.ZERO;
        for (Warehouse w : targets) {
            for (Map<String, Object> r : warehouseService.warehouseStock(cid, w.getId(), canValue)) {
                BigDecimal qty = r.get("quantity") instanceof BigDecimal q ? q : BigDecimal.ZERO;
                Object val = r.get("stockValue");
                rows.add(new Object[]{ w.getName(), r.get("productName"), r.get("sku"), r.get("unit"),
                        qty, r.get("reservedQuantity"), r.get("availableQuantity"), val });
                totalQty = totalQty.add(qty);
                if (val instanceof BigDecimal bv) totalVal = totalVal.add(bv);
            }
        }

        String[] headers = {"Warehouse", "Product", "SKU", "Unit", "Quantity", "Reserved", "Available", "Value"};
        Object[] totals = { "TOTAL", null, null, null, totalQty, null, null, canValue ? totalVal : null };
        int[] moneyCols = canValue ? new int[]{7} : new int[]{};
        String filter = warehouseId != null ? "Warehouse: " + targets.get(0).getName() : "All warehouses";

        byte[] data = excelExportService.genericReport(name, "Warehouse Stock",
                principal.getRole() + " (user #" + principal.getUserId() + ")", filter,
                headers, rows, totals, moneyCols);
        accessLogService.logAllowed(cid, principal.getUserId(), "warehouse_stock.export", "export");
        return build(data, "warehouse_stock_" + today() + ".xlsx");
    }

    // ── Production exports ───────────────────────────────────────────────────────

    @GetMapping("/production/orders")
    public ResponseEntity<byte[]> exportProductionOrders(@AuthenticationPrincipal BizControlPrincipal principal) throws Exception {
        permissionService.require(principal, "production.export");
        Long cid = principal.getCompanyId();
        String name = companyService.getById(cid).getName();
        boolean canCost = permissionService.hasPermission(principal, "production.view_cost");
        List<Object[]> rows = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        for (ProductionOrder o : productionOrderRepository.findByCompanyIdOrderByCreatedAtDesc(cid)) {
            rows.add(new Object[]{ o.getOrderNumber(), productName(cid, o.getProductId()), o.getPlannedQuantity(),
                    o.getCompletedQuantity(), o.getUnit(), o.getStatus(),
                    canCost ? o.getTotalCost() : null, canCost ? o.getCostPerUnit() : null });
            if (o.getTotalCost() != null) totalCost = totalCost.add(o.getTotalCost());
        }
        String[] headers = {"Order #", "Product", "Planned", "Completed", "Unit", "Status", "Total Cost", "Cost/Unit"};
        Object[] totals = { "TOTAL", null, null, null, null, null, canCost ? totalCost : null, null };
        int[] money = canCost ? new int[]{6, 7} : new int[]{};
        byte[] data = excelExportService.genericReport(name, "Production Orders", genBy(principal), "All orders",
                headers, rows, totals, money);
        accessLogService.logAllowed(cid, principal.getUserId(), "production.export", "export");
        return build(data, "production_orders_" + today() + ".xlsx");
    }

    @GetMapping("/production/bom")
    public ResponseEntity<byte[]> exportBom(@AuthenticationPrincipal BizControlPrincipal principal) throws Exception {
        permissionService.require(principal, "bom.export");
        Long cid = principal.getCompanyId();
        String name = companyService.getById(cid).getName();
        List<Object[]> rows = new ArrayList<>();
        for (BomTemplate b : bomTemplateRepository.findByCompanyIdOrderByCreatedAtDesc(cid)) {
            int comps = bomComponentRepository.findByBomTemplateId(b.getId()).size();
            rows.add(new Object[]{ b.getName(), productName(cid, b.getProductId()), b.getVersion(),
                    b.getOutputQuantity(), b.getUnit(), b.getStatus(), comps });
        }
        String[] headers = {"BOM Name", "Product", "Version", "Output Qty", "Unit", "Status", "Components"};
        byte[] data = excelExportService.genericReport(name, "BOM Templates", genBy(principal), "All recipes",
                headers, rows, null, new int[]{});
        accessLogService.logAllowed(cid, principal.getUserId(), "bom.export", "export");
        return build(data, "bom_" + today() + ".xlsx");
    }

    @GetMapping("/production/waste")
    public ResponseEntity<byte[]> exportProductionWaste(@AuthenticationPrincipal BizControlPrincipal principal) throws Exception {
        permissionService.require(principal, "production.export");
        Long cid = principal.getCompanyId();
        String name = companyService.getById(cid).getName();
        List<Object[]> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (WasteRecord w : wasteRecordRepository.findByCompanyIdOrderByCreatedAtDesc(cid)) {
            rows.add(new Object[]{ w.getCreatedAt() != null ? w.getCreatedAt().toString() : "",
                    productName(cid, w.getProductId()), w.getQuantity(), w.getUnit(), w.getReason(), w.getCostImpact() });
            if (w.getCostImpact() != null) total = total.add(w.getCostImpact());
        }
        String[] headers = {"Date", "Product", "Quantity", "Unit", "Reason", "Cost Impact"};
        Object[] totals = { "TOTAL", null, null, null, null, total };
        byte[] data = excelExportService.genericReport(name, "Production Waste", genBy(principal), "All waste",
                headers, rows, totals, new int[]{5});
        accessLogService.logAllowed(cid, principal.getUserId(), "production.export", "export");
        return build(data, "production_waste_" + today() + ".xlsx");
    }

    private String genBy(BizControlPrincipal p) { return p.getRole() + " (user #" + p.getUserId() + ")"; }

    private String productName(Long companyId, Long productId) {
        return productRepository.findByCompanyIdAndId(companyId, productId)
                .map(uz.bizcontrol.entity.Product::getName).orElse("#" + productId);
    }

    private ResponseEntity<byte[]> build(byte[] data, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(data.length);
        return ResponseEntity.ok().headers(headers).body(data);
    }

    private String today() {
        return DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
    }

    private org.springframework.data.jpa.domain.Specification<Debt> buildDebtSpec(Long companyId, String debtType, String status) {
        return (root, q, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("company").get("id"), companyId));
            if (debtType != null && !debtType.isBlank()) preds.add(cb.equal(root.get("debtType"), debtType));
            if (status != null && !status.isBlank()) preds.add(cb.equal(root.get("status"), status));
            else preds.add(cb.notEqual(root.get("status"), "closed"));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private org.springframework.data.jpa.domain.Specification<CashTransaction> buildCashSpec(
            Long companyId, String type, String source, String from, String to) {
        return (root, q, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("company").get("id"), companyId));
            preds.add(cb.equal(root.get("status"), "active"));
            if (type != null && !type.isBlank()) preds.add(cb.equal(root.get("transactionType"), type));
            if (source != null && !source.isBlank()) preds.add(cb.equal(root.get("paymentSource"), source));
            if (from != null && !from.isBlank()) preds.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), LocalDateTime.parse(from + "T00:00:00")));
            if (to != null && !to.isBlank()) preds.add(cb.lessThanOrEqualTo(root.get("transactionDate"), LocalDateTime.parse(to + "T23:59:59")));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
