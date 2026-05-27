package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.ApprovalRequest;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.ApprovalRequestRepository;
import uz.bizcontrol.security.BizControlPrincipal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Manages approval workflows for sensitive operations.
 *
 * <p>Trigger types and their thresholds:
 * <ul>
 *   <li>SALE_BELOW_COST — selling a product below its purchase price</li>
 *   <li>HIGH_DISCOUNT — discount exceeds 30% of total</li>
 *   <li>LARGE_STOCK_ADJUST — quantity adjustment > 100 or negative value > 50</li>
 *   <li>SALE_CANCEL — cancelling a confirmed sale</li>
 *   <li>PURCHASE_CANCEL — cancelling a confirmed purchase</li>
 *   <li>LARGE_EXPENSE — manual expense > 5,000,000 UZS</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRequestRepository approvalRepository;
    private final AuditService auditService;
    private final PermissionService permissionService;

    // ── Thresholds ────────────────────────────────────────────────────────────

    /** Discount percentage that triggers approval (30%) */
    public static final double DISCOUNT_THRESHOLD_PCT = 30.0;

    /** Stock adjustment quantity that triggers approval */
    public static final double STOCK_ADJUST_THRESHOLD = 100.0;

    /** Cash expense amount (UZS) that triggers approval */
    public static final BigDecimal LARGE_EXPENSE_THRESHOLD = new BigDecimal("5000000");

    // ── Request creation ─────────────────────────────────────────────────────

    @Transactional
    public ApprovalRequest request(
            Long companyId,
            Long requesterId,
            String triggerType,
            String entityType,
            Long entityId,
            String description,
            String metadataJson) {

        ApprovalRequest req = ApprovalRequest.builder()
                .companyId(companyId)
                .requesterId(requesterId)
                .triggerType(triggerType)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .metadata(metadataJson)
                .status("pending")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        approvalRepository.save(req);
        log.info("[Approval] {} requested by userId={} for {}#{}", triggerType, requesterId, entityType, entityId);
        auditService.log(companyId, requesterId, "APPROVAL_REQUESTED", entityType, entityId,
                null, triggerType + ": " + description);
        return req;
    }

    /**
     * Create a <em>blocking</em> approval request that carries a deferred action payload.
     * Uses {@code REQUIRES_NEW} propagation so it commits independently from the caller's
     * transaction — the caller can safely throw {@link uz.bizcontrol.exception.PendingApprovalException}
     * to abort the outer transaction without losing the approval record.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApprovalRequest requestDeferred(
            Long companyId,
            Long requesterId,
            String triggerType,
            String entityType,
            Long entityId,
            String description,
            String metadataJson,
            String pendingActionPayload) {

        ApprovalRequest req = ApprovalRequest.builder()
                .companyId(companyId)
                .requesterId(requesterId)
                .triggerType(triggerType)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .metadata(metadataJson)
                .pendingActionPayload(pendingActionPayload)
                .status("pending")
                .expiresAt(LocalDateTime.now().plusHours(48))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        approvalRepository.save(req);
        log.info("[ApprovalDeferred] {} requested by userId={} for {}#{} — action deferred until approved",
                triggerType, requesterId, entityType, entityId);
        auditService.log(companyId, requesterId, "APPROVAL_REQUESTED_BLOCKING",
                entityType, entityId, null, triggerType + ": " + description);
        return req;
    }

    // ── Decision ─────────────────────────────────────────────────────────────

    @Transactional
    public ApprovalRequest decide(
            BizControlPrincipal approver,
            Long requestId,
            boolean approved,
            String note) {

        ApprovalRequest req = approvalRepository.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("ApprovalRequest"));

        if (!req.getCompanyId().equals(approver.getCompanyId()))
            throw BusinessException.forbidden("Not your company");

        if (!"pending".equals(req.getStatus()))
            throw new BusinessException("Request is already " + req.getStatus());

        if (LocalDateTime.now().isAfter(req.getExpiresAt()))
            throw new BusinessException("Approval request has expired");

        req.setStatus(approved ? "approved" : "rejected");
        req.setApproverId(approver.getUserId());
        req.setDecisionNote(note);
        req.setDecidedAt(LocalDateTime.now());
        approvalRepository.save(req);

        auditService.log(approver.getCompanyId(), approver.getUserId(),
                approved ? "APPROVAL_APPROVED" : "APPROVAL_REJECTED",
                req.getEntityType(), req.getEntityId(),
                "pending", req.getStatus());

        return req;
    }

    @Transactional
    public ApprovalRequest cancel(BizControlPrincipal principal, Long requestId) {
        ApprovalRequest req = approvalRepository.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("ApprovalRequest"));

        if (!req.getCompanyId().equals(principal.getCompanyId()))
            throw BusinessException.forbidden("Not your company");

        // Allow: original requester with approvals.request, OR anyone with approvals.decide
        boolean isRequester = req.getRequesterId().equals(principal.getUserId());
        boolean canDecide = permissionService.hasPermission(principal, "approvals.decide");
        boolean canRequest = permissionService.hasPermission(principal, "approvals.request");

        if (!(isRequester && canRequest) && !canDecide)
            throw BusinessException.forbidden("Only the requester (with approvals.request) or a decision-maker (approvals.decide) can cancel");

        if (!"pending".equals(req.getStatus()))
            throw new BusinessException("Only pending requests can be cancelled");

        req.setStatus("cancelled");
        req.setApproverId(principal.getUserId());
        req.setDecidedAt(LocalDateTime.now());
        approvalRepository.save(req);

        auditService.log(principal.getCompanyId(), principal.getUserId(),
                "APPROVAL_CANCELLED",
                req.getEntityType(), req.getEntityId(),
                "pending", "cancelled");

        return req;
    }

    // ── Query helpers ─────────────────────────────────────────────────────────

    public List<ApprovalRequest> getPending(Long companyId) {
        return approvalRepository.findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, "pending");
    }

    public long countPending(Long companyId) {
        return approvalRepository.countPending(companyId);
    }

    public boolean hasPendingFor(Long companyId, String entityType, Long entityId) {
        return !approvalRepository.findPendingByEntity(companyId, entityType, entityId).isEmpty();
    }

    // ── Trigger checks ───────────────────────────────────────────────────────

    /**
     * Returns true if the sale requires approval due to below-cost or large-discount scenario.
     * Call from SaleService before creating the sale.
     */
    public boolean isSaleBelowCost(BigDecimal totalAmount, BigDecimal totalCost) {
        return totalAmount != null && totalCost != null &&
               totalAmount.compareTo(totalCost) < 0;
    }

    public boolean isHighDiscount(BigDecimal discountAmount, BigDecimal subtotal) {
        if (discountAmount == null || subtotal == null ||
                subtotal.compareTo(BigDecimal.ZERO) == 0) return false;
        double pct = discountAmount.multiply(BigDecimal.valueOf(100))
                .divide(subtotal, 2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
        return pct >= DISCOUNT_THRESHOLD_PCT;
    }

    public boolean isLargeStockAdjust(BigDecimal quantity) {
        if (quantity == null) return false;
        return quantity.abs().compareTo(BigDecimal.valueOf(STOCK_ADJUST_THRESHOLD)) >= 0;
    }

    public boolean isLargeExpense(BigDecimal amount) {
        return amount != null && amount.compareTo(LARGE_EXPENSE_THRESHOLD) >= 0;
    }

    /** Build a simple JSON metadata string from a map. */
    public String buildMetadata(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        data.forEach((k, v) -> {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"").append(k).append("\":\"").append(v).append("\"");
        });
        sb.append("}");
        return sb.toString();
    }
}
