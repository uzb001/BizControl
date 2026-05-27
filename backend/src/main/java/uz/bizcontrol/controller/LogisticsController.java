package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.repository.*;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * REST surface for the logistics module. Reads (list/get/detail) are gated by
 * {@code logistics.view}; mutations have separate codes ({@code logistics.create},
 * {@code logistics.confirm}, {@code logistics.cancel}). The Excel export checks
 * {@code logistics.export} and writes an access-log entry.
 */
@RestController
@RequestMapping("/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LogisticsService logisticsService;
    private final LogisticsOrderRepository orderRepository;
    private final LogisticsOrderItemRepository itemRepository;
    private final LogisticsExpenseRepository expenseRepository;
    private final LandedCostAllocationRepository allocationRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final CountryRepository countryRepository;
    private final SupplierRepository supplierRepository;
    private final CompanyService companyService;
    private final ExcelExportService excel;
    private final AccessLogService accessLog;
    private final PermissionService permissionService;

    // ── List & read ────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Page<LogisticsOrder>> list(
            @AuthenticationPrincipal BizControlPrincipal p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        permissionService.require(p, "logistics.view");
        return ResponseEntity.ok(orderRepository.findByCompanyIdOrderByCreatedAtDesc(
                p.getCompanyId(), PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@AuthenticationPrincipal BizControlPrincipal p,
                                                   @PathVariable Long id) {
        permissionService.require(p, "logistics.view");
        boolean canSeeLanded = permissionService.hasPermission(p, "logistics.view_landed_cost");
        Map<String, Object> detail = logisticsService.getDetail(p.getCompanyId(), id, canSeeLanded);
        // Enrich for the UI without changing the canonical entities
        detail.put("itemDetails", enrichItems(p.getCompanyId(), id));
        return ResponseEntity.ok(detail);
    }

    // ── Draft CRUD ─────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<LogisticsOrder> create(@AuthenticationPrincipal BizControlPrincipal p,
                                                 @RequestBody Map<String, Object> body) {
        permissionService.require(p, "logistics.create");
        return ResponseEntity.ok(logisticsService.createDraft(p.getCompanyId(), p.getUserId(), body));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<LogisticsOrderItem> addItem(@AuthenticationPrincipal BizControlPrincipal p,
                                                      @PathVariable Long id,
                                                      @RequestBody Map<String, Object> body) {
        permissionService.require(p, "logistics.create");
        return ResponseEntity.ok(logisticsService.addItem(p.getCompanyId(), p.getUserId(), id, body));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<Void> removeItem(@AuthenticationPrincipal BizControlPrincipal p,
                                           @PathVariable Long id, @PathVariable Long itemId) {
        permissionService.require(p, "logistics.create");
        logisticsService.removeItem(p.getCompanyId(), p.getUserId(), id, itemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/expenses")
    public ResponseEntity<LogisticsExpense> addExpense(@AuthenticationPrincipal BizControlPrincipal p,
                                                       @PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        permissionService.require(p, "logistics.create");
        return ResponseEntity.ok(logisticsService.addExpense(p.getCompanyId(), p.getUserId(), id, body));
    }

    @DeleteMapping("/{id}/expenses/{expenseId}")
    public ResponseEntity<Void> removeExpense(@AuthenticationPrincipal BizControlPrincipal p,
                                              @PathVariable Long id, @PathVariable Long expenseId) {
        permissionService.require(p, "logistics.create");
        logisticsService.removeExpense(p.getCompanyId(), p.getUserId(), id, expenseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<LogisticsOrder> confirm(@AuthenticationPrincipal BizControlPrincipal p,
                                                  @PathVariable Long id) {
        permissionService.require(p, "logistics.confirm");
        return ResponseEntity.ok(logisticsService.confirm(p.getCompanyId(), p.getUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, String>> cancel(@AuthenticationPrincipal BizControlPrincipal p,
                                                      @PathVariable Long id) {
        permissionService.require(p, "logistics.cancel");
        logisticsService.cancelDraft(p.getCompanyId(), p.getUserId(), id);
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<LogisticsOrder> reverse(@AuthenticationPrincipal BizControlPrincipal p,
                                                  @PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        permissionService.require(p, "logistics.reverse");
        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : null;
        return ResponseEntity.ok(logisticsService.reverse(p.getCompanyId(), p.getUserId(), id, reason));
    }

    @PostMapping("/{id}/expenses/{expenseId}/pay")
    public ResponseEntity<uz.bizcontrol.entity.LogisticsExpensePayment> payExpense(
            @AuthenticationPrincipal BizControlPrincipal p,
            @PathVariable Long id, @PathVariable Long expenseId,
            @RequestBody Map<String, Object> body) {
        permissionService.require(p, "logistics.pay_payable");
        return ResponseEntity.ok(logisticsService.payExpense(p.getCompanyId(), p.getUserId(), id, expenseId, body));
    }

    // ── Excel export ───────────────────────────────────────────────────────────

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportOne(@AuthenticationPrincipal BizControlPrincipal p,
                                            @PathVariable Long id) throws Exception {
        permissionService.require(p, "logistics.export");
        LogisticsOrder o = logisticsService.getOne(p.getCompanyId(), id);
        boolean canSeeLanded = permissionService.hasPermission(p, "logistics.view_landed_cost");

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Items", "", "", "", "", "", "", ""});
        for (LogisticsOrderItem it : itemRepository.findByLogisticsOrderIdOrderByIdAsc(id)) {
            rows.add(new Object[]{"  item", prodName(p.getCompanyId(), it.getProductId()),
                    it.getQuantity(), it.getUnitCost(), it.getItemValue(),
                    "", "", ""});
        }
        rows.add(new Object[]{"Expenses", "", "", "", "", "", "", ""});
        for (LogisticsExpense e : expenseRepository.findByLogisticsOrderIdOrderByIdAsc(id)) {
            rows.add(new Object[]{"  expense", e.getExpenseType(), e.getAmount(),
                    e.getCurrency(), e.getPaymentSource(), "", "",
                    e.getNote() != null ? e.getNote() : ""});
        }
        if (canSeeLanded) {
            rows.add(new Object[]{"Landed cost", "", "", "", "", "", "", ""});
            for (LandedCostAllocation a : allocationRepository.findByLogisticsOrderIdOrderByIdAsc(id)) {
                rows.add(new Object[]{"  alloc", prodName(p.getCompanyId(), a.getProductId()),
                        a.getQuantity(), a.getItemValue(), a.getAllocatedAmount(),
                        a.getUnitLandedCost(), "", ""});
            }
        }

        String filter = "Order " + o.getOrderNumber() + " | status=" + o.getStatus()
                + " | itemsValue=" + o.getItemsValue() + " | expenses=" + o.getExpensesTotal()
                + " | landed=" + o.getLandedTotal();
        String company = companyService.getById(p.getCompanyId()).getName();
        byte[] data = excel.genericReport(company, "Logistics " + o.getOrderNumber(),
                p.getRole() + " (user #" + p.getUserId() + ")", filter,
                new String[]{"Section", "Name/Type", "Qty/Amount", "Unit/Curr", "Value", "Unit landed", "—", "Note"},
                rows, null, new int[]{4});

        accessLog.logAllowed(p.getCompanyId(), p.getUserId(), "logistics.export", "logistics");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.setContentDisposition(ContentDisposition.attachment()
                .filename("logistics_" + o.getOrderNumber() + ".xlsx").build());
        return new ResponseEntity<>(data, h, HttpStatus.OK);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportAll(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        permissionService.require(p, "logistics.export");
        List<LogisticsOrder> all = orderRepository.findByCompanyIdOrderByCreatedAtDesc(
                p.getCompanyId(), PageRequest.of(0, 10000)).getContent();
        List<Object[]> rows = new ArrayList<>();
        for (LogisticsOrder o : all) {
            rows.add(new Object[]{
                    o.getOrderNumber(),
                    fmt(o.getCreatedAt()),
                    whName(p.getCompanyId(), o.getSourceWarehouseId()),
                    whName(p.getCompanyId(), o.getDestinationWarehouseId()),
                    countryName(p.getCompanyId(), o.getSourceCountryId()),
                    countryName(p.getCompanyId(), o.getDestinationCountryId()),
                    o.getCurrency(),
                    o.getItemsValue(),
                    o.getExpensesTotal(),
                    o.getLandedTotal(),
                    o.getStatus(),
            });
        }
        String company = companyService.getById(p.getCompanyId()).getName();
        byte[] data = excel.genericReport(company, "Logistics orders",
                p.getRole() + " (user #" + p.getUserId() + ")",
                "All logistics orders (latest 10000)",
                new String[]{"Order #", "Created", "Source WH", "Dest WH", "Source country",
                        "Dest country", "Currency", "Items value", "Expenses", "Landed total", "Status"},
                rows, null, new int[]{7, 8, 9});
        accessLog.logAllowed(p.getCompanyId(), p.getUserId(), "logistics.export", "logistics");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.setContentDisposition(ContentDisposition.attachment().filename("logistics_orders.xlsx").build());
        return new ResponseEntity<>(data, h, HttpStatus.OK);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Decorate items with product name/SKU for the detail UI. */
    private List<Map<String, Object>> enrichItems(Long companyId, Long orderId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LogisticsOrderItem it : itemRepository.findByLogisticsOrderIdOrderByIdAsc(orderId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", it.getId());
            m.put("productId", it.getProductId());
            m.put("productName", prodName(companyId, it.getProductId()));
            m.put("productSku", productRepository.findByCompanyIdAndId(companyId, it.getProductId())
                    .map(Product::getSku).orElse(null));
            m.put("quantity", it.getQuantity());
            m.put("unitCost", it.getUnitCost());
            m.put("itemValue", it.getItemValue());
            out.add(m);
        }
        return out;
    }

    private String prodName(Long cid, Long pid) {
        return productRepository.findByCompanyIdAndId(cid, pid).map(Product::getName).orElse("#" + pid);
    }
    private String whName(Long cid, Long whId) {
        if (whId == null) return "—";
        return warehouseRepository.findByCompanyIdAndId(cid, whId).map(Warehouse::getName).orElse("#" + whId);
    }
    private String countryName(Long cid, Long countryId) {
        if (countryId == null) return "—";
        return countryRepository.findByCompanyIdAndId(cid, countryId).map(Country::getName).orElse("#" + countryId);
    }
    private static String fmt(LocalDateTime d) { return d != null ? d.format(DT) : ""; }
}
