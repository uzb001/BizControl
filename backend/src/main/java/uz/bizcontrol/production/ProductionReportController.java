package uz.bizcontrol.production;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.Product;
import uz.bizcontrol.repository.ProductRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/production/reports")
@RequiredArgsConstructor
public class ProductionReportController {

    private final ProductionOrderRepository orderRepository;
    private final WasteRecordRepository wasteRepository;
    private final ProductRepository productRepository;
    private final PermissionService permissionService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "production.view");
        Long cid = p.getCompanyId();
        boolean canCost = permissionService.hasPermission(p, "production.view_cost");

        List<ProductionOrder> all = orderRepository.findByCompanyIdOrderByCreatedAtDesc(cid);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : List.of("draft", "planned", "in_progress", "quality_check", "completed", "cancelled")) {
            byStatus.put(s, all.stream().filter(o -> s.equals(o.getStatus())).count());
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalOrders", all.size());
        r.put("byStatus", byStatus);
        r.put("inProgress", byStatus.getOrDefault("in_progress", 0L));
        r.put("qualityCheck", byStatus.getOrDefault("quality_check", 0L));
        r.put("completed", byStatus.getOrDefault("completed", 0L));

        if (canCost) {
            BigDecimal totalCost = all.stream().filter(o -> "completed".equals(o.getStatus()))
                    .map(o -> o.getTotalCost() != null ? o.getTotalCost() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalWaste = wasteRepository.findByCompanyIdOrderByCreatedAtDesc(cid).stream()
                    .map(w -> w.getCostImpact() != null ? w.getCostImpact() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            r.put("totalProductionCost", totalCost);
            r.put("totalWasteCost", totalWaste);
        }

        // Top produced products (by completed quantity)
        Map<Long, BigDecimal> produced = new HashMap<>();
        for (ProductionOrder o : all) {
            if ("completed".equals(o.getStatus()) && o.getCompletedQuantity() != null) {
                produced.merge(o.getProductId(), o.getCompletedQuantity(), BigDecimal::add);
            }
        }
        List<Map<String, Object>> top = produced.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("productId", e.getKey());
                    m.put("productName", productRepository.findById(e.getKey()).map(Product::getName).orElse("#" + e.getKey()));
                    m.put("quantity", e.getValue());
                    return m;
                }).toList();
        r.put("topProducts", top);
        return ResponseEntity.ok(r);
    }

    @GetMapping("/waste")
    public ResponseEntity<List<WasteRecord>> waste(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "production_waste.view");
        return ResponseEntity.ok(wasteRepository.findByCompanyIdOrderByCreatedAtDesc(p.getCompanyId()));
    }
}
