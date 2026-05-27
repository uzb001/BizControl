package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.DashboardService;
import uz.bizcontrol.service.OperationalHealthService;
import uz.bizcontrol.service.PermissionService;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final OperationalHealthService operationalHealthService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDashboard(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(defaultValue = "this_month") String period) {
        permissionService.require(principal, "dashboard.view");
        Map<String, Object> data = dashboardService.getDashboard(principal.getCompanyId(), period);

        // Server-side masking: a role only receives the figures it is allowed to
        // see, so the frontend can never accidentally render restricted data.
        if (!permissionService.hasPermission(principal, "sales.view_profit")) {
            data.remove("profit");
            data.remove("profitGrowth");
        }
        if (!permissionService.hasPermission(principal, "cashbox.view")) {
            data.remove("cashBalance");
        }
        if (!permissionService.hasPermission(principal, "bank.view")) {
            data.remove("bankBalance");
        }
        if (!permissionService.hasPermission(principal, "customers.view_debt")
                && !permissionService.hasPermission(principal, "debts.view_customer")) {
            data.remove("totalCustomerDebt");
        }
        if (!permissionService.hasPermission(principal, "suppliers.view_debt")
                && !permissionService.hasPermission(principal, "debts.view_supplier")) {
            data.remove("totalSupplierDebt");
        }
        // The aggregate health score is derived from financials — only expose it
        // to roles that can view reports.
        if (!permissionService.hasPermission(principal, "reports.view")) {
            data.remove("healthScore");
        }
        return ResponseEntity.ok(data);
    }

    /**
     * Daily Operational Health widget (V19+). Returns sales/cash/bank counts for
     * today + a colour level. Backed by the same evaluator the scheduled job
     * uses, so dashboard ↔ scheduler ↔ Telegram alert all agree.
     */
    @GetMapping("/operational-health")
    public ResponseEntity<Map<String, Object>> operationalHealth(
            @AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "dashboard.view");
        return ResponseEntity.ok(operationalHealthService.widget(principal.getCompanyId()));
    }
}
