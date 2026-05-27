package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "approver_id")
    private Long approverId;

    /** e.g. SALE_BELOW_COST, HIGH_DISCOUNT, LARGE_STOCK_ADJUST, SALE_CANCEL, LARGE_EXPENSE */
    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** JSON blob with relevant context */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Serialised JSON of the action to execute once this request is approved.
     * Null for advisory-only approvals (e.g. SALE_BELOW_COST that was already posted).
     * Non-null for blocking approvals (LARGE_EXPENSE, LARGE_STOCK_ADJUST, SALE_CANCEL, PURCHASE_CANCEL).
     */
    @Column(name = "pending_action_payload", columnDefinition = "TEXT")
    private String pendingActionPayload;

    /** pending | approved | rejected | cancelled */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
