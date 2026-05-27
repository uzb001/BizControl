package uz.bizcontrol.production;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.accounting.ChartOfAccounts;
import uz.bizcontrol.accounting.ChartOfAccountsService;
import uz.bizcontrol.accounting.JournalLine;
import uz.bizcontrol.accounting.JournalService;
import uz.bizcontrol.entity.Product;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.ProductRepository;
import uz.bizcontrol.service.AlertService;
import uz.bizcontrol.service.AuditService;
import uz.bizcontrol.service.WarehouseStockService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Manufacturing workflow engine. Integrates with the rest of the ERP through the
 * existing services only — {@link WarehouseStockService} for stock movements,
 * {@link JournalService} for balanced accounting entries, and {@link AuditService}
 * for the audit trail. Nothing in Sales/Purchases/Stock is modified.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionOrderService {

    private final ProductionOrderRepository orderRepository;
    private final ProductionOrderComponentRepository componentRepository;
    private final ProductionStepRepository stepRepository;
    private final ProductionCostRepository costRepository;
    private final WasteRecordRepository wasteRepository;
    private final BomTemplateRepository bomTemplateRepository;
    private final BomService bomService;
    private final ProductRepository productRepository;
    private final WarehouseStockService warehouseStockService;
    private final ChartOfAccountsService chart;
    private final JournalService journalService;
    private final AuditService auditService;
    private final AlertService alertService;

    // ── Queries ──────────────────────────────────────────────────────────────

    public org.springframework.data.domain.Page<ProductionOrder> list(
            Long companyId, org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
    }

    public ProductionOrder getOne(Long companyId, Long id) {
        return orderRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> BusinessException.notFound("Production order"));
    }

    public Map<String, Object> detail(Long companyId, Long id) {
        ProductionOrder o = getOne(companyId, id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order", o);
        m.put("components", componentRepository.findByProductionOrderId(id));
        m.put("steps", stepRepository.findByProductionOrderIdOrderBySortOrderAsc(id));
        m.put("costs", costRepository.findByProductionOrderId(id));
        m.put("waste", wasteRepository.findByProductionOrderId(id));
        return m;
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public ProductionOrder create(Long companyId, Long userId, Map<String, Object> body) {
        Long productId = asLong(body.get("productId"));
        Long bomId = asLong(body.get("bomTemplateId"));
        BigDecimal plannedQty = bd(body.get("plannedQuantity"), BigDecimal.ZERO);
        if (plannedQty.signum() <= 0) throw new BusinessException("Planned quantity must be greater than zero");

        BomTemplate bom = null;
        if (bomId != null) {
            bom = bomTemplateRepository.findByCompanyIdAndId(companyId, bomId)
                    .orElseThrow(() -> BusinessException.notFound("BOM"));
            if (!"active".equals(bom.getStatus())) throw new BusinessException("Selected BOM is inactive");
            if (productId == null) productId = bom.getProductId();
        }
        if (productId == null) throw new BusinessException("Finished product is required");
        Product product = productRepository.findByCompanyIdAndId(companyId, productId)
                .orElseThrow(() -> new BusinessException("Finished product not found"));

        Long sourceWh = warehouseStockService.resolveWarehouseId(companyId, asLong(body.get("sourceWarehouseId")));
        Long finishedWh = warehouseStockService.resolveWarehouseId(companyId, asLong(body.get("finishedGoodsWarehouseId")));
        Long prodWh = asLong(body.get("productionWarehouseId"));

        ProductionOrder order = orderRepository.save(ProductionOrder.builder()
                .companyId(companyId)
                .orderNumber(generateOrderNumber(companyId))
                .productId(productId)
                .bomTemplateId(bomId)
                .plannedQuantity(plannedQty)
                .unit(str(body.getOrDefault("unit", product.getUnit() != null ? product.getUnit() : "piece")))
                .status("draft")
                .sourceWarehouseId(sourceWh)
                .productionWarehouseId(prodWh)
                .finishedGoodsWarehouseId(finishedWh)
                .responsibleUserId(asLong(body.getOrDefault("responsibleUserId", userId)))
                .plannedStartDate(dt(body.get("plannedStartDate")))
                .plannedEndDate(dt(body.get("plannedEndDate")))
                .note(str(body.get("note")))
                .createdBy(userId)
                .build());

        // Snapshot required components from the BOM.
        BigDecimal estimate = BigDecimal.ZERO;
        if (bom != null) {
            List<ProductionOrderComponent> comps = bomService.computeRequired(companyId, bom, plannedQty, sourceWh);
            for (ProductionOrderComponent c : comps) {
                c.setProductionOrderId(order.getId());
                componentRepository.save(c);
                estimate = estimate.add(c.getTotalCost());
            }
        }
        order.setTotalCost(estimate);
        order.setCostPerUnit(plannedQty.signum() > 0 ? estimate.divide(plannedQty, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        order = orderRepository.save(order);

        // Default workflow steps for the timeline.
        String[] defaults = {"Material Preparation", "Production", "Quality Check"};
        for (int i = 0; i < defaults.length; i++) {
            stepRepository.save(ProductionStep.builder()
                    .productionOrderId(order.getId()).stepName(defaults[i]).status("pending").sortOrder(i).build());
        }

        auditService.log(companyId, userId, "CREATE", "ProductionOrder", order.getId(), null,
                "Production order " + order.getOrderNumber() + " x" + plannedQty);
        return order;
    }

    // ── Status transitions ─────────────────────────────────────────────────────

    @Transactional
    public ProductionOrder plan(Long companyId, Long userId, Long id) {
        ProductionOrder o = requireStatus(companyId, id, "draft");
        o.setStatus("planned");
        o = orderRepository.save(o);
        auditService.log(companyId, userId, "PLAN", "ProductionOrder", id, "draft", "planned");
        return o;
    }

    @Transactional
    public ProductionOrder start(Long companyId, Long userId, Long id) {
        ProductionOrder o = getOne(companyId, id);
        if (!List.of("draft", "planned").contains(o.getStatus()))
            throw new BusinessException("Only draft/planned orders can be started (current: " + o.getStatus() + ")");
        o.setStatus("in_progress");
        o.setActualStartDate(LocalDateTime.now());
        o = orderRepository.save(o);
        markStep(id, "Material Preparation", "in_progress");
        auditService.log(companyId, userId, "START", "ProductionOrder", id, null, "in_progress");
        return o;
    }

    @Transactional
    public ProductionOrder qualityCheck(Long companyId, Long userId, Long id) {
        ProductionOrder o = requireStatus(companyId, id, "in_progress");
        o.setStatus("quality_check");
        o = orderRepository.save(o);
        markStep(id, "Quality Check", "in_progress");
        auditService.log(companyId, userId, "QUALITY_CHECK", "ProductionOrder", id, "in_progress", "quality_check");
        return o;
    }

    /** Complete: consume raw materials, produce finished goods, cost, post accounting, audit. */
    @Transactional
    public ProductionOrder complete(Long companyId, Long userId, Long id) {
        ProductionOrder o = getOne(companyId, id);
        if (!List.of("in_progress", "quality_check").contains(o.getStatus()))
            throw new BusinessException("Only in-progress / quality-check orders can be completed (current: " + o.getStatus() + ")");

        List<ProductionOrderComponent> comps = componentRepository.findByProductionOrderId(id);

        // 1) Pre-flight stock check — block with an explicit shortage list.
        List<String> shortages = new ArrayList<>();
        for (ProductionOrderComponent c : comps) {
            Long whId = c.getWarehouseId() != null ? c.getWarehouseId() : o.getSourceWarehouseId();
            BigDecimal avail = warehouseStockService.availableInWarehouse(companyId, whId, c.getProductId());
            if (avail.compareTo(c.getRequiredQuantity()) < 0) {
                String pname = productName(companyId, c.getProductId());
                shortages.add(pname + " (need " + c.getRequiredQuantity() + ", have " + avail + ")");
            }
        }
        if (!shortages.isEmpty())
            throw new BusinessException("Insufficient raw materials: " + String.join("; ", shortages));

        // 2) Consume raw materials + tally costs.
        BigDecimal rawConsumedCost = BigDecimal.ZERO;
        BigDecimal wasteCost = BigDecimal.ZERO;
        String memo = "Production " + o.getOrderNumber();
        for (ProductionOrderComponent c : comps) {
            Long whId = c.getWarehouseId() != null ? c.getWarehouseId() : o.getSourceWarehouseId();
            Product cp = productRepository.findByCompanyIdAndId(companyId, c.getProductId()).orElse(null);
            BigDecimal unitCost = cp != null && cp.getPurchasePrice() != null ? cp.getPurchasePrice() : c.getUnitCost();
            BigDecimal lineCost = c.getRequiredQuantity().multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineWaste = (c.getWasteQuantity() != null ? c.getWasteQuantity() : BigDecimal.ZERO)
                    .multiply(unitCost).setScale(2, RoundingMode.HALF_UP);

            warehouseStockService.applyApprovedAdjust(companyId, userId, c.getProductId(), whId,
                    c.getRequiredQuantity().negate(), "production_consume", memo);

            c.setConsumedQuantity(c.getRequiredQuantity());
            c.setUnitCost(unitCost);
            c.setTotalCost(lineCost);
            componentRepository.save(c);

            rawConsumedCost = rawConsumedCost.add(lineCost);
            wasteCost = wasteCost.add(lineWaste);
        }

        // 3) Additional (non-material) costs.
        BigDecimal addedCost = costRepository.findByProductionOrderId(id).stream()
                .filter(pc -> !"raw_material".equals(pc.getCostType()))
                .map(pc -> pc.getAmount() != null ? pc.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finishedValue = rawConsumedCost.add(addedCost).subtract(wasteCost).max(BigDecimal.ZERO);
        BigDecimal totalCost = rawConsumedCost.add(addedCost);
        BigDecimal completedQty = o.getPlannedQuantity();
        BigDecimal costPerUnit = completedQty.signum() > 0
                ? finishedValue.divide(completedQty, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // 4) Produce finished goods into the finished-goods warehouse.
        Long finishedWh = warehouseStockService.resolveWarehouseId(companyId, o.getFinishedGoodsWarehouseId());
        warehouseStockService.applyApprovedAdjust(companyId, userId, o.getProductId(), finishedWh,
                completedQty, "production_output", memo);

        // 5) Update finished product cost (last-production-cost method).
        if (costPerUnit.signum() > 0) {
            productRepository.findByCompanyIdAndId(companyId, o.getProductId()).ifPresent(fp -> {
                fp.setPurchasePrice(costPerUnit);
                productRepository.save(fp);
            });
        }

        // 6) Balanced accounting entry (only when there is a real cost movement).
        postProductionJournal(companyId, userId, id, memo, finishedValue, wasteCost, rawConsumedCost, addedCost);

        // 7) Finalize order + steps.
        o.setCompletedQuantity(completedQty);
        o.setTotalCost(totalCost);
        o.setCostPerUnit(costPerUnit);
        o.setStatus("completed");
        o.setActualEndDate(LocalDateTime.now());
        o = orderRepository.save(o);
        for (ProductionStep s : stepRepository.findByProductionOrderIdOrderBySortOrderAsc(id)) {
            if (!"skipped".equals(s.getStatus())) { s.setStatus("completed"); s.setCompletedAt(LocalDateTime.now()); stepRepository.save(s); }
        }

        auditService.log(companyId, userId, "COMPLETE", "ProductionOrder", id, null,
                "Completed " + completedQty + " @ cost/unit " + costPerUnit);
        return o;
    }

    @Transactional
    public ProductionOrder cancel(Long companyId, Long userId, Long id) {
        ProductionOrder o = getOne(companyId, id);
        if ("cancelled".equals(o.getStatus())) throw new BusinessException("Order already cancelled");

        if ("completed".equals(o.getStatus())) {
            // Reverse stock: return consumed raw materials, remove finished goods.
            String memo = "Production reversal " + o.getOrderNumber();
            for (ProductionOrderComponent c : componentRepository.findByProductionOrderId(id)) {
                if (c.getConsumedQuantity().signum() > 0) {
                    Long whId = c.getWarehouseId() != null ? c.getWarehouseId() : o.getSourceWarehouseId();
                    warehouseStockService.applyApprovedAdjust(companyId, userId, c.getProductId(), whId,
                            c.getConsumedQuantity(), "production_consume_reversal", memo);
                }
            }
            Long finishedWh = warehouseStockService.resolveWarehouseId(companyId, o.getFinishedGoodsWarehouseId());
            if (o.getCompletedQuantity().signum() > 0) {
                BigDecimal available = warehouseStockService.availableInWarehouse(companyId, finishedWh, o.getProductId());
                if (available.compareTo(o.getCompletedQuantity()) < 0)
                    throw new BusinessException("Cannot reverse: finished goods already moved/sold (have "
                            + available + ", need " + o.getCompletedQuantity() + ").");
                warehouseStockService.applyApprovedAdjust(companyId, userId, o.getProductId(), finishedWh,
                        o.getCompletedQuantity().negate(), "production_output_reversal", memo);
            }
            // Reverse the accounting entry (never destructive).
            journalService.reverseBySource(companyId, userId, "PRODUCTION", id, "Production order cancelled");
        }

        o.setStatus("cancelled");
        o = orderRepository.save(o);
        auditService.log(companyId, userId, "CANCEL", "ProductionOrder", id, null, "cancelled");
        return o;
    }

    // ── Costs & waste ───────────────────────────────────────────────────────────

    @Transactional
    public ProductionCost addCost(Long companyId, Long userId, Long orderId, Map<String, Object> body) {
        ProductionOrder o = getOne(companyId, orderId);
        if ("completed".equals(o.getStatus()) || "cancelled".equals(o.getStatus()))
            throw new BusinessException("Cannot add costs to a " + o.getStatus() + " order");
        BigDecimal amount = bd(body.get("amount"), BigDecimal.ZERO);
        if (amount.signum() <= 0) throw new BusinessException("Cost amount must be greater than zero");
        ProductionCost c = costRepository.save(ProductionCost.builder()
                .productionOrderId(orderId)
                .costType(str(body.getOrDefault("costType", "other")))
                .amount(amount).currency(str(body.getOrDefault("currency", "UZS")))
                .note(str(body.get("note"))).createdBy(userId).build());
        // refresh estimate
        recalcEstimate(o);
        auditService.log(companyId, userId, "ADD_COST", "ProductionOrder", orderId, null,
                c.getCostType() + ": " + amount);
        return c;
    }

    public List<ProductionCost> costs(Long companyId, Long orderId) {
        getOne(companyId, orderId);
        return costRepository.findByProductionOrderId(orderId);
    }

    @Transactional
    public WasteRecord addWaste(Long companyId, Long userId, Long orderId, Map<String, Object> body) {
        ProductionOrder o = getOne(companyId, orderId);
        Long productId = asLong(body.get("productId"));
        if (productId == null) throw new BusinessException("Product is required");
        BigDecimal qty = bd(body.get("quantity"), BigDecimal.ZERO);
        if (qty.signum() <= 0) throw new BusinessException("Waste quantity must be greater than zero");
        Product p = productRepository.findByCompanyIdAndId(companyId, productId).orElse(null);
        BigDecimal unitCost = p != null && p.getPurchasePrice() != null ? p.getPurchasePrice() : BigDecimal.ZERO;
        WasteRecord w = wasteRepository.save(WasteRecord.builder()
                .companyId(companyId).productionOrderId(orderId).productId(productId)
                .warehouseId(asLong(body.get("warehouseId")))
                .quantity(qty).unit(str(body.getOrDefault("unit", p != null ? p.getUnit() : "piece")))
                .reason(str(body.get("reason")))
                .costImpact(qty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP))
                .createdBy(userId).build());
        BigDecimal threshold = o.getPlannedQuantity().multiply(new BigDecimal("0.1"));
        if (qty.compareTo(threshold) > 0) {
            alertService.create(companyId, "production_waste", "High production waste",
                    "Waste of " + qty + " recorded on order " + o.getOrderNumber(), "ProductionOrder", orderId);
        }
        auditService.log(companyId, userId, "ADD_WASTE", "ProductionOrder", orderId, null, "waste " + qty);
        return w;
    }

    public List<WasteRecord> waste(Long companyId, Long orderId) {
        getOne(companyId, orderId);
        return wasteRepository.findByProductionOrderId(orderId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private void postProductionJournal(Long companyId, Long userId, Long orderId, String memo,
                                       BigDecimal finishedValue, BigDecimal wasteCost,
                                       BigDecimal rawConsumedCost, BigDecimal addedCost) {
        if (rawConsumedCost.signum() <= 0 && addedCost.signum() <= 0 && wasteCost.signum() <= 0) return;
        Long inv = chart.require(companyId, ChartOfAccounts.INVENTORY).getId();
        Long waste = chart.ensureAccount(companyId, ChartOfAccounts.PRODUCTION_WASTE,
                "Production Waste", "EXPENSE", "DEBIT").getId();
        Long ovh = chart.ensureAccount(companyId, ChartOfAccounts.PRODUCTION_COSTS_PAYABLE,
                "Production Costs Payable", "LIABILITY", "CREDIT").getId();

        List<JournalLine> lines = new ArrayList<>();
        if (finishedValue.signum() > 0)   lines.add(JournalService.debit(inv, finishedValue, memo + " finished goods"));
        if (wasteCost.signum() > 0)       lines.add(JournalService.debit(waste, wasteCost, memo + " waste"));
        if (rawConsumedCost.signum() > 0) lines.add(JournalService.credit(inv, rawConsumedCost, memo + " materials"));
        if (addedCost.signum() > 0)       lines.add(JournalService.credit(ovh, addedCost, memo + " labor/overhead"));
        if (lines.size() >= 2) {
            journalService.post(companyId, userId, LocalDateTime.now(), "PRODUCTION", orderId, memo, lines);
        }
    }

    private void recalcEstimate(ProductionOrder o) {
        BigDecimal material = componentRepository.findByProductionOrderId(o.getId()).stream()
                .map(c -> c.getTotalCost() != null ? c.getTotalCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal added = costRepository.findByProductionOrderId(o.getId()).stream()
                .filter(pc -> !"raw_material".equals(pc.getCostType()))
                .map(pc -> pc.getAmount() != null ? pc.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = material.add(added);
        o.setTotalCost(total);
        o.setCostPerUnit(o.getPlannedQuantity().signum() > 0
                ? total.divide(o.getPlannedQuantity(), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        orderRepository.save(o);
    }

    private void markStep(Long orderId, String name, String status) {
        stepRepository.findByProductionOrderIdOrderBySortOrderAsc(orderId).stream()
                .filter(s -> name.equalsIgnoreCase(s.getStepName()))
                .findFirst().ifPresent(s -> {
                    s.setStatus(status);
                    if ("in_progress".equals(status) && s.getStartedAt() == null) s.setStartedAt(LocalDateTime.now());
                    stepRepository.save(s);
                });
    }

    private ProductionOrder requireStatus(Long companyId, Long id, String expected) {
        ProductionOrder o = getOne(companyId, id);
        if (!expected.equals(o.getStatus()))
            throw new BusinessException("Order must be '" + expected + "' (current: " + o.getStatus() + ")");
        return o;
    }

    private String productName(Long companyId, Long productId) {
        return productRepository.findByCompanyIdAndId(companyId, productId)
                .map(Product::getName).orElse("Product #" + productId);
    }

    private String generateOrderNumber(Long companyId) {
        String date = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
        long count = orderRepository.countByCompanyId(companyId) + 1;
        String number = "PR-" + date + "-" + String.format("%04d", count);
        while (orderRepository.existsByCompanyIdAndOrderNumber(companyId, number)) {
            count++;
            number = "PR-" + date + "-" + String.format("%04d", count);
        }
        return number;
    }

    private static String str(Object o) { return o != null ? o.toString() : null; }
    private static Long asLong(Object o) { return o == null || o.toString().isBlank() ? null : Long.valueOf(o.toString()); }
    private static BigDecimal bd(Object o, BigDecimal def) { return o == null || o.toString().isBlank() ? def : new BigDecimal(o.toString()); }
    private static LocalDateTime dt(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        try { return LocalDateTime.parse(o.toString()); } catch (Exception e) { return null; }
    }
}
