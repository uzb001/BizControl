package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.*;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.AuditService;
import uz.bizcontrol.service.CompanyService;
import uz.bizcontrol.service.PermissionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/debts")
@RequiredArgsConstructor
public class DebtController {

    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final CompanyService companyService;
    private final AuditService auditService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Page<Debt>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String debtType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        // Require at least one debt view permission based on filter
        if ("supplier".equals(debtType)) {
            permissionService.require(principal, "debts.view_supplier");
        } else if ("customer".equals(debtType)) {
            permissionService.require(principal, "debts.view_customer");
        } else {
            // No filter — require at least one
            permissionService.requireAny(principal, "debts.view_customer", "debts.view_supplier");
        }

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Long companyId = principal.getCompanyId();

        // Build spec respecting permissions — filter out debt types the user cannot see
        boolean canCustomer = permissionService.hasPermission(principal, "debts.view_customer");
        boolean canSupplier = permissionService.hasPermission(principal, "debts.view_supplier");

        return ResponseEntity.ok(debtRepository.findAll(
                buildSpec(companyId, debtType, status, customerId, supplierId, canCustomer, canSupplier),
                PageRequest.of(page, size, sort)));
    }

    @PostMapping("/{id}/payment")
    public ResponseEntity<?> addPayment(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        permissionService.require(principal, "debts.add_payment");

        Debt debt = debtRepository.findById(id)
                .filter(d -> d.getCompany().getId().equals(principal.getCompanyId()))
                .orElseThrow(() -> BusinessException.notFound("Debt"));

        if (body.get("amount") == null) throw new BusinessException("amount is required");
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String method = body.getOrDefault("paymentMethod", "cash").toString();
        String note = body.getOrDefault("note", "").toString();

        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("Payment amount must be greater than zero");
        if (amount.compareTo(debt.getRemainingAmount()) > 0) {
            throw new BusinessException("Payment exceeds remaining debt");
        }

        Company company = companyService.getById(principal.getCompanyId());

        DebtPayment payment = DebtPayment.builder()
                .debt(debt)
                .company(company)
                .amount(amount)
                .paymentMethod(method)
                .currency(debt.getCurrency())
                .paymentDate(LocalDateTime.now())
                .note(note)
                .createdBy(principal.getUserId())
                .build();
        debtPaymentRepository.save(payment);

        debt.setPaidAmount(debt.getPaidAmount().add(amount));
        debt.setRemainingAmount(debt.getRemainingAmount().subtract(amount));
        if (debt.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            debt.setStatus("closed");
        } else {
            debt.setStatus("partial");
        }
        debtRepository.save(debt);

        // Update customer/supplier debt balance
        if ("customer".equals(debt.getDebtType()) && debt.getCustomer() != null) {
            Customer c = debt.getCustomer();
            c.setCurrentDebt(c.getCurrentDebt().subtract(amount));
            customerRepository.save(c);
        } else if ("supplier".equals(debt.getDebtType()) && debt.getSupplier() != null) {
            Supplier s = debt.getSupplier();
            s.setCurrentDebt(s.getCurrentDebt().subtract(amount));
            supplierRepository.save(s);
        }

        // Create cash transaction
        String txType = "supplier".equals(debt.getDebtType()) ? "expense" : "income";
        String source = "bank".equals(method) ? "bank" : "cash";
        CashTransaction ct = CashTransaction.builder()
                .company(company)
                .transactionType(txType)
                .paymentSource(source)
                .amount(amount)
                .currency(debt.getCurrency())
                .category(txType.equals("expense") ? "supplier_payment" : "customer_debt_payment")
                .transactionDate(LocalDateTime.now())
                .note(note.isEmpty() ? "Debt payment" : note)
                .createdBy(principal.getUserId())
                .build();
        cashTransactionRepository.save(ct);

        // Update balance
        if ("income".equals(txType)) {
            if ("bank".equals(source)) company.setBankBalance(company.getBankBalance().add(amount));
            else company.setCashBalance(company.getCashBalance().add(amount));
        } else {
            if ("bank".equals(source)) company.setBankBalance(company.getBankBalance().subtract(amount));
            else company.setCashBalance(company.getCashBalance().subtract(amount));
        }
        companyService.save(company);

        auditService.log(principal.getCompanyId(), principal.getUserId(), "PAYMENT", "Debt", id, null, "paid=" + amount);
        return ResponseEntity.ok(debt);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> history(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        Debt debt = debtRepository.findById(id)
                .filter(d -> d.getCompany().getId().equals(principal.getCompanyId()))
                .orElseThrow(() -> BusinessException.notFound("Debt"));

        // Check permission based on debt type
        if ("supplier".equals(debt.getDebtType())) {
            permissionService.require(principal, "debts.view_supplier");
        } else {
            permissionService.require(principal, "debts.view_customer");
        }
        return ResponseEntity.ok(debtPaymentRepository.findByDebtId(id));
    }

    private org.springframework.data.jpa.domain.Specification<Debt> buildSpec(
            Long companyId, String debtType, String status,
            Long customerId, Long supplierId,
            boolean canCustomer, boolean canSupplier) {
        return (root, q, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("company").get("id"), companyId));

            // Enforce visibility based on permissions
            if (!canCustomer && canSupplier) {
                preds.add(cb.equal(root.get("debtType"), "supplier"));
            } else if (canCustomer && !canSupplier) {
                preds.add(cb.equal(root.get("debtType"), "customer"));
            }
            // If both permitted, apply user filter if provided
            if (debtType != null && !debtType.isBlank()) preds.add(cb.equal(root.get("debtType"), debtType));

            if (status != null && !status.isBlank()) preds.add(cb.equal(root.get("status"), status));
            else preds.add(cb.notEqual(root.get("status"), "closed"));
            if (customerId != null) preds.add(cb.equal(root.get("customer").get("id"), customerId));
            if (supplierId != null) preds.add(cb.equal(root.get("supplier").get("id"), supplierId));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
