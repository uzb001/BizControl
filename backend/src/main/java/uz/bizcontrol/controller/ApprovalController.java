package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.ApprovalRequest;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.ApprovalService;
import uz.bizcontrol.service.DeferredActionService;
import uz.bizcontrol.service.PermissionService;
import uz.bizcontrol.repository.ApprovalRequestRepository;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;
    private final DeferredActionService deferredActionService;
    private final ApprovalRequestRepository approvalRepository;
    private final PermissionService permissionService;

    /** GET /approvals — list all (paginated) with optional status filter */
    @GetMapping
    public ResponseEntity<Page<ApprovalRequest>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        permissionService.require(principal, "approvals.view");
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ApprovalRequest> result = status != null && !status.isBlank()
                ? approvalRepository.findByCompanyIdAndStatusOrderByCreatedAtDesc(principal.getCompanyId(), status, pageable)
                : approvalRepository.findByCompanyIdOrderByCreatedAtDesc(principal.getCompanyId(), pageable);
        return ResponseEntity.ok(result);
    }

    /** GET /approvals/pending — quick list of pending items (for badge counter) */
    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalRequest>> pending(
            @AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "approvals.view");
        return ResponseEntity.ok(approvalService.getPending(principal.getCompanyId()));
    }

    /** GET /approvals/count — pending count for badge */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count(
            @AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "approvals.view");
        return ResponseEntity.ok(Map.of("count", approvalService.countPending(principal.getCompanyId())));
    }

    /** POST /approvals/{id}/approve */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApprovalRequest> approve(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        permissionService.require(principal, "approvals.decide");
        String note = body != null ? body.getOrDefault("note", "").toString() : "";
        ApprovalRequest result = approvalService.decide(principal, id, true, note);
        // Execute deferred action if a payload is stored
        deferredActionService.dispatch(result);
        return ResponseEntity.ok(result);
    }

    /** POST /approvals/{id}/reject */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApprovalRequest> reject(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        permissionService.require(principal, "approvals.decide");
        String note = body != null ? body.getOrDefault("note", "Rejected").toString() : "Rejected";
        return ResponseEntity.ok(approvalService.decide(principal, id, false, note));
    }

    /** POST /approvals/{id}/cancel */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApprovalRequest> cancel(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        // Requester can cancel their own; approver can cancel any
        permissionService.requireAny(principal, "approvals.request", "approvals.decide");
        return ResponseEntity.ok(approvalService.cancel(principal, id));
    }
}
