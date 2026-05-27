package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.BusinessAdvisorService;
import uz.bizcontrol.service.PermissionService;

import java.util.List;
import java.util.Map;

/**
 * AI / Heuristic Business Advisor endpoints.
 *
 * GET /advisor/insights     — actionable recommendations
 * GET /advisor/forecasts    — stock runout + cashflow forecasts
 * GET /advisor/anomalies    — detected anomalies and unusual patterns
 */
@RestController
@RequestMapping("/advisor")
@RequiredArgsConstructor
public class AdvisorController {

    private final BusinessAdvisorService advisorService;
    private final PermissionService permissionService;

    @GetMapping("/insights")
    public ResponseEntity<List<Map<String, Object>>> insights(
            @AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "reports.view");
        return ResponseEntity.ok(advisorService.getInsights(principal.getCompanyId()));
    }

    @GetMapping("/forecasts")
    public ResponseEntity<Map<String, Object>> forecasts(
            @AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "reports.view");
        return ResponseEntity.ok(advisorService.getForecasts(principal.getCompanyId()));
    }

    @GetMapping("/anomalies")
    public ResponseEntity<List<Map<String, Object>>> anomalies(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(defaultValue = "7") int days) {
        permissionService.require(principal, "reports.view");
        return ResponseEntity.ok(advisorService.getAnomalies(principal.getCompanyId(), days));
    }
}
