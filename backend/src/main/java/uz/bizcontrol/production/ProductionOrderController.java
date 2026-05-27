package uz.bizcontrol.production;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/production/orders")
@RequiredArgsConstructor
public class ProductionOrderController {

    private final ProductionOrderService orderService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Page<ProductionOrder>> list(
            @AuthenticationPrincipal BizControlPrincipal p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        permissionService.require(p, "production.view");
        return ResponseEntity.ok(orderService.list(p.getCompanyId(), PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "production.view");
        return ResponseEntity.ok(orderService.detail(p.getCompanyId(), id));
    }

    @PostMapping
    public ResponseEntity<ProductionOrder> create(@AuthenticationPrincipal BizControlPrincipal p,
                                                  @RequestBody Map<String, Object> body) {
        permissionService.require(p, "production.create");
        return ResponseEntity.ok(orderService.create(p.getCompanyId(), p.getUserId(), body));
    }

    @PostMapping("/{id}/plan")
    public ResponseEntity<ProductionOrder> plan(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "production.edit");
        return ResponseEntity.ok(orderService.plan(p.getCompanyId(), p.getUserId(), id));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ProductionOrder> start(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "production.start");
        return ResponseEntity.ok(orderService.start(p.getCompanyId(), p.getUserId(), id));
    }

    @PostMapping("/{id}/quality-check")
    public ResponseEntity<ProductionOrder> qualityCheck(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "production.quality_check");
        return ResponseEntity.ok(orderService.qualityCheck(p.getCompanyId(), p.getUserId(), id));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ProductionOrder> complete(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "production.complete");
        return ResponseEntity.ok(orderService.complete(p.getCompanyId(), p.getUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ProductionOrder> cancel(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "production.cancel");
        return ResponseEntity.ok(orderService.cancel(p.getCompanyId(), p.getUserId(), id));
    }

    // ── Costs ──────────────────────────────────────────────────────────────────
    @GetMapping("/{id}/costs")
    public ResponseEntity<List<ProductionCost>> costs(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "production.view_cost");
        return ResponseEntity.ok(orderService.costs(p.getCompanyId(), id));
    }

    @PostMapping("/{id}/costs")
    public ResponseEntity<ProductionCost> addCost(@AuthenticationPrincipal BizControlPrincipal p,
                                                  @PathVariable Long id, @RequestBody Map<String, Object> body) {
        permissionService.require(p, "production.edit");
        return ResponseEntity.ok(orderService.addCost(p.getCompanyId(), p.getUserId(), id, body));
    }

    // ── Waste ──────────────────────────────────────────────────────────────────
    @GetMapping("/{id}/waste")
    public ResponseEntity<List<WasteRecord>> waste(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "production_waste.view");
        return ResponseEntity.ok(orderService.waste(p.getCompanyId(), id));
    }

    @PostMapping("/{id}/waste")
    public ResponseEntity<WasteRecord> addWaste(@AuthenticationPrincipal BizControlPrincipal p,
                                                @PathVariable Long id, @RequestBody Map<String, Object> body) {
        permissionService.require(p, "production_waste.create");
        return ResponseEntity.ok(orderService.addWaste(p.getCompanyId(), p.getUserId(), id, body));
    }
}
