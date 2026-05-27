package uz.bizcontrol.exception;

/**
 * Thrown when an action is blocked because it requires manager/owner approval.
 * The global exception handler maps this to HTTP 202 Accepted.
 * The transaction that saved the ApprovalRequest was committed with REQUIRES_NEW
 * propagation BEFORE this exception is thrown, so the approval IS persisted.
 */
public class PendingApprovalException extends RuntimeException {

    private final Long approvalId;

    public PendingApprovalException(String message, Long approvalId) {
        super(message);
        this.approvalId = approvalId;
    }

    public Long getApprovalId() {
        return approvalId;
    }
}
