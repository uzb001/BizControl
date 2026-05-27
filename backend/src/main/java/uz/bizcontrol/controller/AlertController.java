package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.Alert;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.AlertService;
import uz.bizcontrol.service.PermissionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<Alert>> getNew(@AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "dashboard.view");
        return ResponseEntity.ok(alertService.getNew(principal.getCompanyId()));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count(@AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "dashboard.view");
        return ResponseEntity.ok(Map.of("count", alertService.countNew(principal.getCompanyId())));
    }

    @PatchMapping("/{id}/seen")
    public ResponseEntity<Alert> seen(@AuthenticationPrincipal BizControlPrincipal principal, @PathVariable Long id) {
        permissionService.require(principal, "dashboard.view");
        return ResponseEntity.ok(alertService.markSeen(principal.getCompanyId(), id));
    }

    @PatchMapping("/{id}/resolved")
    public ResponseEntity<Alert> resolved(@AuthenticationPrincipal BizControlPrincipal principal, @PathVariable Long id) {
        permissionService.require(principal, "dashboard.view");
        return ResponseEntity.ok(alertService.markResolved(principal.getCompanyId(), id));
    }
}
