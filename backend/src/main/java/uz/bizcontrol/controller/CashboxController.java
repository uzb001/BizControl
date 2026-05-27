package uz.bizcontrol.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.dto.request.MoneyTransactionRequest;
import uz.bizcontrol.entity.ApprovalRequest;
import uz.bizcontrol.entity.CashTransaction;
import uz.bizcontrol.entity.Company;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.exception.PendingApprovalException;
import uz.bizcontrol.repository.CashTransactionRepository;
import uz.bizcontrol.repository.DailyCloseRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.AccessLogService;
import uz.bizcontrol.service.ApprovalService;
import uz.bizcontrol.service.AuditService;
import uz.bizcontrol.service.CompanyService;
import uz.bizcontrol.service.PermissionService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/cash-transactions")
@RequiredArgsConstructor
public class CashboxController {

    private final CashTransactionRepository cashTransactionRepository;
    private final CompanyService companyService;
    private final AuditService auditService;
    private final PermissionService permissionService;
    private final AccessLogService accessLogService;
    private final ApprovalService approvalService;
    private final DailyCloseRepository dailyCloseRepository;

    @GetMapping
    public ResponseEntity<Page<CashTransaction>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String paymentSource,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "transactionDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        permissionService.require(principal, "cashbox.view");
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "cashbox.view", "cashbox");
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        return ResponseEntity.ok(cashTransactionRepository.findAll(
                buildSpec(principal.getCompanyId(), transactionType, paymentSource, fromDate, toDate, category),
                PageRequest.of(page, size, sort)));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @Valid @RequestBody MoneyTransactionRequest req) {

        if ("income".equals(req.getTransactionType())) {
            permissionService.require(principal, "cashbox.create_income");
        } else {
            permissionService.require(principal, "cashbox.create_expense");
        }

        // Daily close lock: cannot record transactions on a closed date
        LocalDate txDate = req.getTransactionDate() != null
                ? req.getTransactionDate().toLocalDate()
                : LocalDate.now();
        if (dailyCloseRepository.existsByCompanyIdAndCloseDateAndStatus(
                principal.getCompanyId(), txDate, "closed")) {
            auditService.log(principal.getCompanyId(), principal.getUserId(),
                    "BLOCKED_DAILY_CLOSE", "CashTransaction", null,
                    null, "Attempted transaction on closed date " + txDate);
            throw new BusinessException("Date " + txDate + " is closed. Corrections require owner approval.");
        }

        // Large expense → block and create deferred approval
        if ("expense".equals(req.getTransactionType())
                && approvalService.isLargeExpense(req.getAmount())) {

            String payload = buildExpensePayload(principal.getCompanyId(), principal.getUserId(), req);
            ApprovalRequest ar = approvalService.requestDeferred(
                    principal.getCompanyId(), principal.getUserId(),
                    "LARGE_EXPENSE", "CashTransaction", null,
                    "Large expense of " + req.getAmount() + " " + currencyOf(req),
                    approvalService.buildMetadata(Map.of(
                            "amount",  req.getAmount(),
                            "source",  req.getPaymentSource() != null ? req.getPaymentSource() : "cash",
                            "note",    req.getNote() != null ? req.getNote() : "")),
                    payload);

            // Do NOT update balance — throw to return 202
            throw new PendingApprovalException(
                    "Expense of " + req.getAmount() + " " + currencyOf(req)
                            + " exceeds threshold and requires manager/owner approval",
                    ar.getId());
        }

        // Normal flow: save transaction and update balance
        Company company = companyService.getById(principal.getCompanyId());
        CashTransaction transaction = toEntity(req, company, principal.getUserId());
        transaction = cashTransactionRepository.save(transaction);

        updateBalance(company, req.getPaymentSource(), req.getAmount(),
                "income".equals(req.getTransactionType()));
        companyService.save(company);

        auditService.log(principal.getCompanyId(), principal.getUserId(),
                "CREATE", "CashTransaction", transaction.getId(), null,
                transaction.getTransactionType() + " " + transaction.getAmount());
        return ResponseEntity.ok(transaction);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CashTransaction> update(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @RequestBody MoneyTransactionRequest updates) {
        permissionService.require(principal, "cashbox.edit");
        CashTransaction tx = cashTransactionRepository.findById(id)
                .filter(t -> t.getCompany().getId().equals(principal.getCompanyId()))
                .orElseThrow(() -> BusinessException.notFound("Transaction"));
        if (updates.getNote() != null)     tx.setNote(updates.getNote());
        if (updates.getCategory() != null) tx.setCategory(updates.getCategory());
        tx.setUpdatedBy(principal.getUserId());
        return ResponseEntity.ok(cashTransactionRepository.save(tx));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "cashbox.cancel");
        CashTransaction tx = cashTransactionRepository.findById(id)
                .filter(t -> t.getCompany().getId().equals(principal.getCompanyId()))
                .orElseThrow(() -> BusinessException.notFound("Transaction"));
        if (!"active".equals(tx.getStatus()))
            throw new BusinessException("Transaction already " + tx.getStatus());

        // Reverse balance effect
        Company company = companyService.getById(principal.getCompanyId());
        boolean reverseIncome = "income".equals(tx.getTransactionType());
        updateBalance(company, tx.getPaymentSource(), tx.getAmount(), !reverseIncome); // reverse
        companyService.save(company);

        tx.setStatus("cancelled");
        cashTransactionRepository.save(tx);
        auditService.log(principal.getCompanyId(), principal.getUserId(),
                "CANCEL", "CashTransaction", id, "active", "cancelled");
        return ResponseEntity.ok(Map.of("message", "Cancelled"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CashTransaction toEntity(MoneyTransactionRequest req, Company company, Long userId) {
        return CashTransaction.builder()
                .company(company)
                .transactionType(req.getTransactionType())
                .paymentSource(req.getPaymentSource() != null ? req.getPaymentSource() : "cash")
                .amount(req.getAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : "UZS")
                .category(req.getCategory())
                .relatedSaleId(req.getRelatedSaleId())
                .relatedPurchaseId(req.getRelatedPurchaseId())
                .relatedCustomerId(req.getRelatedCustomerId())
                .relatedSupplierId(req.getRelatedSupplierId())
                .transactionDate(req.getTransactionDate() != null ? req.getTransactionDate() : LocalDateTime.now())
                .note(req.getNote())
                .status("active")
                .createdBy(userId)
                .build();
    }

    private void updateBalance(Company company, String source, java.math.BigDecimal amount, boolean add) {
        if (isBank(source)) {
            company.setBankBalance(add
                    ? company.getBankBalance().add(amount)
                    : company.getBankBalance().subtract(amount));
        } else {
            company.setCashBalance(add
                    ? company.getCashBalance().add(amount)
                    : company.getCashBalance().subtract(amount));
        }
    }

    private boolean isBank(String source) {
        return source != null && (source.equals("bank") || source.equals("bank_transfer"));
    }

    private String currencyOf(MoneyTransactionRequest req) {
        return req.getCurrency() != null ? req.getCurrency() : "UZS";
    }

    private String buildExpensePayload(Long companyId, Long userId, MoneyTransactionRequest req) {
        return "{\"action\":\"LARGE_EXPENSE\""
                + ",\"companyId\":\"" + companyId + "\""
                + ",\"requesterId\":\"" + userId + "\""
                + ",\"transactionType\":\"" + req.getTransactionType() + "\""
                + ",\"paymentSource\":\"" + (req.getPaymentSource() != null ? req.getPaymentSource() : "cash") + "\""
                + ",\"amount\":\"" + req.getAmount().toPlainString() + "\""
                + ",\"currency\":\"" + currencyOf(req) + "\""
                + ",\"category\":\"" + (req.getCategory() != null ? req.getCategory() : "") + "\""
                + ",\"note\":\"" + (req.getNote() != null ? req.getNote().replace("\"", "'") : "") + "\""
                + "}";
    }

    private org.springframework.data.jpa.domain.Specification<CashTransaction> buildSpec(
            Long companyId, String type, String source, String fromDate, String toDate, String category) {
        return (root, q, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("company").get("id"), companyId));
            preds.add(cb.equal(root.get("status"), "active"));
            // Exclude bank transactions from cashbox view (they're in /bank-transactions)
            preds.add(cb.notEqual(root.get("paymentSource"), "bank"));
            if (type   != null && !type.isBlank())    preds.add(cb.equal(root.get("transactionType"), type));
            if (source != null && !source.isBlank())  preds.add(cb.equal(root.get("paymentSource"), source));
            if (category != null && !category.isBlank()) preds.add(cb.equal(root.get("category"), category));
            if (fromDate != null && !fromDate.isBlank())
                preds.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), LocalDateTime.parse(fromDate + "T00:00:00")));
            if (toDate != null && !toDate.isBlank())
                preds.add(cb.lessThanOrEqualTo(root.get("transactionDate"), LocalDateTime.parse(toDate + "T23:59:59")));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
