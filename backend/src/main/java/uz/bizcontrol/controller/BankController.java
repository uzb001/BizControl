package uz.bizcontrol.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.dto.request.MoneyTransactionRequest;
import uz.bizcontrol.entity.CashTransaction;
import uz.bizcontrol.entity.Company;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.CashTransactionRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.AccessLogService;
import uz.bizcontrol.service.AuditService;
import uz.bizcontrol.service.CompanyService;
import uz.bizcontrol.service.PermissionService;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Bank transactions endpoint — backed by money_transactions with paymentSource='bank'.
 */
@RestController
@RequestMapping("/bank-transactions")
@RequiredArgsConstructor
public class BankController {

    private final CashTransactionRepository cashTransactionRepository;
    private final CompanyService companyService;
    private final PermissionService permissionService;
    private final AccessLogService accessLogService;
    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<Page<CashTransaction>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        permissionService.require(principal, "bank.view");
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "bank.view", "bank");
        return ResponseEntity.ok(cashTransactionRepository.findAll(
                buildSpec(principal.getCompanyId(), transactionType, fromDate, toDate),
                PageRequest.of(page, size, Sort.by("transactionDate").descending())));
    }

    @PostMapping
    public ResponseEntity<CashTransaction> create(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @Valid @RequestBody MoneyTransactionRequest req) {

        permissionService.require(principal, "bank.create");
        Company company = companyService.getById(principal.getCompanyId());

        CashTransaction transaction = CashTransaction.builder()
                .company(company)
                .transactionType(req.getTransactionType())
                .paymentSource("bank")                           // always bank
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
                .createdBy(principal.getUserId())
                .build();

        transaction = cashTransactionRepository.save(transaction);

        // Update bank balance
        if ("income".equals(transaction.getTransactionType()))
            company.setBankBalance(company.getBankBalance().add(transaction.getAmount()));
        else
            company.setBankBalance(company.getBankBalance().subtract(transaction.getAmount()));
        companyService.save(company);

        auditService.log(principal.getCompanyId(), principal.getUserId(),
                "CREATE", "BankTransaction", transaction.getId(), null,
                transaction.getTransactionType() + " " + transaction.getAmount());
        return ResponseEntity.ok(transaction);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "bank.create");
        CashTransaction tx = cashTransactionRepository.findById(id)
                .filter(t -> t.getCompany().getId().equals(principal.getCompanyId()))
                .filter(t -> "bank".equals(t.getPaymentSource()))
                .orElseThrow(() -> BusinessException.notFound("BankTransaction"));
        if (!"active".equals(tx.getStatus()))
            throw new BusinessException("Transaction already " + tx.getStatus());

        // Reverse the balance effect
        Company company = companyService.getById(principal.getCompanyId());
        if ("income".equals(tx.getTransactionType()))
            company.setBankBalance(company.getBankBalance().subtract(tx.getAmount()));
        else
            company.setBankBalance(company.getBankBalance().add(tx.getAmount()));
        companyService.save(company);

        tx.setStatus("cancelled");
        cashTransactionRepository.save(tx);
        auditService.log(principal.getCompanyId(), principal.getUserId(),
                "CANCEL", "BankTransaction", id, "active", "cancelled");
        return ResponseEntity.ok(Map.of("message", "Cancelled"));
    }

    private Specification<CashTransaction> buildSpec(Long companyId, String type, String fromDate, String toDate) {
        return (root, q, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("company").get("id"), companyId));
            preds.add(cb.equal(root.get("paymentSource"), "bank"));
            preds.add(cb.equal(root.get("status"), "active"));
            if (type != null && !type.isBlank())
                preds.add(cb.equal(root.get("transactionType"), type));
            if (fromDate != null && !fromDate.isBlank())
                preds.add(cb.greaterThanOrEqualTo(root.get("transactionDate"),
                        LocalDateTime.parse(fromDate + "T00:00:00")));
            if (toDate != null && !toDate.isBlank())
                preds.add(cb.lessThanOrEqualTo(root.get("transactionDate"),
                        LocalDateTime.parse(toDate + "T23:59:59")));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
