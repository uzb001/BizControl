package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.AuditLog;
import uz.bizcontrol.repository.AuditLogRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final PermissionService  permissionService;

    @GetMapping
    public ResponseEntity<Page<AuditLog>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        permissionService.require(principal, "audit.view");

        Long companyId = principal.getCompanyId();
        return ResponseEntity.ok(auditLogRepository.findAll(
                buildSpec(companyId, userId, actionType, entityType),
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    private org.springframework.data.jpa.domain.Specification<AuditLog> buildSpec(
            Long companyId, Long userId, String actionType, String entityType) {
        return (root, q, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("companyId"), companyId));
            if (userId != null) preds.add(cb.equal(root.get("userId"), userId));
            if (actionType != null && !actionType.isBlank()) preds.add(cb.equal(root.get("actionType"), actionType));
            if (entityType != null && !entityType.isBlank()) preds.add(cb.equal(root.get("entityType"), entityType));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
