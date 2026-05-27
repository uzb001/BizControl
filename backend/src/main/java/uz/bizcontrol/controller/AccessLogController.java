package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.AccessLog;
import uz.bizcontrol.repository.AccessLogRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/access-logs")
@RequiredArgsConstructor
public class AccessLogController {

    private final AccessLogRepository accessLogRepository;
    private final PermissionService permissionService;

    /** GET /access-logs — paginated, filterable by result / userId / module */
    @GetMapping
    public ResponseEntity<Page<AccessLog>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String result,   // ALLOWED | DENIED
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        permissionService.require(principal, "audit.view");

        Long companyId = principal.getCompanyId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Full page with optional in-memory filters applied via JPA Specification
        Page<AccessLog> data = accessLogRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);

        return ResponseEntity.ok(data);
    }

    /** GET /access-logs/summary — DENIED count vs ALLOWED count (last 7 days) */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> summary(
            @AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "audit.view");
        Long companyId = principal.getCompanyId();

        long denied  = accessLogRepository.findByCompanyIdAndResultOrderByCreatedAtDesc(companyId, "DENIED").size();
        long allowed = accessLogRepository.findByCompanyIdAndResultOrderByCreatedAtDesc(companyId, "ALLOWED").size();

        return ResponseEntity.ok(Map.of(
            "denied",  denied,
            "allowed", allowed,
            "total",   denied + allowed
        ));
    }

    /** GET /access-logs/user/{userId} — all access events for a specific user */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AccessLog>> forUser(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long userId) {
        permissionService.require(principal, "audit.view");
        return ResponseEntity.ok(
            accessLogRepository.findByCompanyIdAndUserIdOrderByCreatedAtDesc(
                principal.getCompanyId(), userId)
        );
    }
}
