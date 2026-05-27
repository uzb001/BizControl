package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.dto.request.PurchaseRequest;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.entity.ApprovalRequest;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.exception.PendingApprovalException;
import uz.bizcontrol.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementRepository stockMovementRepository;
    private final DebtRepository debtRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final CompanyService companyService;
    private final AuditService auditService;
    private final ApprovalService approvalService;
    private final DailyCloseRepository dailyCloseRepository;
    private final WarehouseStockService warehouseStockService;
    private final uz.bizcontrol.accounting.AccountingService accountingService;

    // ─── Create ──────────────────────────────────────────────────────────────

    @Transactional
    public Purchase create(Long companyId, Long userId, PurchaseRequest req) {
        Company company = companyService.getById(companyId);

        BigDecimal headerDiscount = req.getDiscountAmount()  != null ? req.getDiscountAmount()  : BigDecimal.ZERO;
        BigDecimal additionalCost = req.getAdditionalCost()  != null ? req.getAdditionalCost()  : BigDecimal.ZERO;
        BigDecimal paidAmount     = req.getPaidAmount()      != null ? req.getPaidAmount()      : BigDecimal.ZERO;
        String currency       = req.getCurrency()        != null ? req.getCurrency()        : "UZS";
        String paymentMethod  = req.getPaymentMethod()   != null ? req.getPaymentMethod()   : "cash";
        LocalDateTime purchaseDate = req.getPurchaseDate() != null ? req.getPurchaseDate()  : LocalDateTime.now();

        if (headerDiscount.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("Discount cannot be negative");
        if (additionalCost.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("Additional cost cannot be negative");
        if (paidAmount.compareTo(BigDecimal.ZERO) < 0)     throw new BusinessException("Paid amount cannot be negative");

        // Daily close lock
        checkDailyClose(companyId, userId, purchaseDate, "Purchase create", null);

        String purchaseNumber = generatePurchaseNumber(companyId);

        Purchase purchase = Purchase.builder()
                .company(company)
                .purchaseNumber(purchaseNumber)
                .purchaseDate(purchaseDate)
                .discountAmount(headerDiscount)
                .additionalCost(additionalCost)
                .paymentMethod(paymentMethod)
                .currency(currency)
                .note(req.getNote())
                .status("active")
                .docStatus("draft")
                .totalAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .unpaidAmount(BigDecimal.ZERO)
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        if (req.getSupplierId() != null) {
            supplierRepository.findByCompanyIdAndId(companyId, req.getSupplierId())
                    .ifPresent(purchase::setSupplier);
        }
        purchase.setWarehouseId(warehouseStockService.resolveWarehouseId(companyId, req.getWarehouseId()));
        purchase = purchaseRepository.save(purchase);
        Long savedId = purchase.getId();

        List<PurchaseItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (PurchaseRequest.PurchaseItemRequest itemReq : req.getItems()) {
            BigDecimal itemDiscount = itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO;
            Product product = productRepository.findByCompanyIdAndId(companyId, itemReq.getProductId())
                    .orElseThrow(() -> BusinessException.notFound("Product " + itemReq.getProductId()));
            if (itemReq.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("Quantity must be > 0 for " + product.getName());

            BigDecimal lineTotal = itemReq.getPurchasePrice()
                    .multiply(itemReq.getQuantity()).subtract(itemDiscount);
            PurchaseItem item = PurchaseItem.builder()
                    .purchase(purchase)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .purchasePrice(itemReq.getPurchasePrice())
                    .discountAmount(itemDiscount)
                    .totalAmount(lineTotal)
                    .build();
            items.add(item);
            subtotal = subtotal.add(lineTotal);
        }

        BigDecimal total = subtotal.add(additionalCost).subtract(headerDiscount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;
        if (paidAmount.compareTo(total) > 0) throw new BusinessException("Paid amount exceeds total");

        purchase.setTotalAmount(total);
        purchase.setPaidAmount(paidAmount);
        purchase.setUnpaidAmount(total.subtract(paidAmount));
        purchase = purchaseRepository.save(purchase);

        final Purchase savedPurchase = purchase;
        items.forEach(i -> i.setPurchase(savedPurchase));
        purchaseItemRepository.saveAll(items);

        // No approval triggers for purchases currently; post immediately
        return postInternal(companyId, userId, purchase, items, company,
                paymentMethod, currency, paidAmount, total, purchaseNumber, savedId);
    }

    // ─── Post ────────────────────────────────────────────────────────────────

    @Transactional
    public Purchase post(Long companyId, Long userId, Long purchaseId) {
        Purchase purchase = getOne(companyId, purchaseId);
        if (purchase.isLocked()) throw new BusinessException("Purchase is locked");
        if ("pending_approval".equals(purchase.getDocStatus()))
            throw new BusinessException("Purchase is pending approval and cannot be manually posted. It will be posted automatically once approved.");
        if ("posted".equals(purchase.getDocStatus())) throw new BusinessException("Purchase already posted");
        if ("cancelled".equals(purchase.getDocStatus())) throw new BusinessException("Purchase is cancelled");

        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseId(purchaseId);
        Company company = companyService.getById(companyId);
        return postInternal(companyId, userId, purchase, items, company,
                purchase.getPaymentMethod(), purchase.getCurrency(),
                purchase.getPaidAmount(), purchase.getTotalAmount(), purchase.getPurchaseNumber(), purchaseId);
    }

    private Purchase postInternal(Long companyId, Long userId, Purchase purchase, List<PurchaseItem> items,
                                   Company company, String paymentMethod, String currency,
                                   BigDecimal paidAmount, BigDecimal total, String purchaseNumber, Long savedId) {

        BigDecimal unpaid = total.subtract(paidAmount);

        if (unpaid.compareTo(BigDecimal.ZERO) > 0 && purchase.getSupplier() == null)
            throw new BusinessException("A supplier must be selected when the purchase has an unpaid balance.");

        // Apply stock changes
        for (PurchaseItem item : items) {
            Product product = item.getProduct();
            if (product == null) continue;
            BigDecimal prev = product.getCurrentStock();
            BigDecimal newStock = prev.add(item.getQuantity());
            product.setCurrentStock(newStock);
            product.setPurchasePrice(item.getPurchasePrice()); // update latest cost
            productRepository.save(product);

            StockMovement movement = StockMovement.builder()
                    .company(company)
                    .product(product)
                    .warehouseId(purchase.getWarehouseId())
                    .movementType("purchase")
                    .quantity(item.getQuantity())
                    .previousStock(prev)
                    .newStock(newStock)
                    .referenceId(savedId)
                    .referenceType("purchase")
                    .note("Purchase " + purchaseNumber)
                    .createdBy(userId)
                    .build();
            stockMovementRepository.save(movement);
            warehouseStockService.syncWarehouseDelta(companyId, purchase.getWarehouseId(),
                    product.getId(), item.getQuantity());
        }

        // Cash transaction for paid portion
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            String source = isBank(paymentMethod) ? "bank" : paymentMethod;
            CashTransaction ct = CashTransaction.builder()
                    .company(company)
                    .transactionType("expense")
                    .paymentSource(source)
                    .amount(paidAmount)
                    .currency(currency)
                    .category("purchase_payment")
                    .relatedPurchaseId(savedId)
                    .relatedSupplierId(purchase.getSupplier() != null ? purchase.getSupplier().getId() : null)
                    .transactionDate(LocalDateTime.now())
                    .note("Payment for purchase " + purchaseNumber)
                    .createdBy(userId)
                    .build();
            cashTransactionRepository.save(ct);

            if (isBank(source)) company.setBankBalance(company.getBankBalance().subtract(paidAmount));
            else company.setCashBalance(company.getCashBalance().subtract(paidAmount));
            companyService.save(company);
        }

        // Supplier debt for unpaid portion
        if (unpaid.compareTo(BigDecimal.ZERO) > 0 && purchase.getSupplier() != null) {
            Debt debt = Debt.builder()
                    .company(company)
                    .debtType("supplier")
                    .supplier(purchase.getSupplier())
                    .relatedPurchaseId(savedId)
                    .originalAmount(unpaid)
                    .paidAmount(BigDecimal.ZERO)
                    .remainingAmount(unpaid)
                    .currency(currency)
                    .status("open")
                    .createdBy(userId)
                    .build();
            debtRepository.save(debt);
            Supplier s = purchase.getSupplier();
            s.setCurrentDebt(s.getCurrentDebt().add(unpaid));
            supplierRepository.save(s);
        }

        purchase.setDocStatus("posted");
        purchase.setPendingApprovalId(null);
        purchase = purchaseRepository.save(purchase);

        // Double-entry ledger: record the purchase as a balanced journal entry (atomic).
        accountingService.recordPurchase(companyId, userId, purchase, total, paidAmount);

        auditService.log(companyId, userId, "CREATE", "Purchase", savedId, null,
                "Purchase " + purchaseNumber + " total=" + total);
        return purchase;
    }

    // ─── Cancel ──────────────────────────────────────────────────────────────

    /**
     * Public cancel endpoint — gates posted purchases behind an approval workflow.
     * Draft/pending_approval purchases are cancelled immediately.
     */
    @Transactional
    public Purchase cancel(Long companyId, Long userId, Long id) {
        Purchase purchase = getOne(companyId, id);
        if (purchase.isLocked()) throw new BusinessException("Purchase is locked");
        if ("cancelled".equals(purchase.getStatus())) throw new BusinessException("Purchase already cancelled");
        if ("draft".equals(purchase.getDocStatus()) || "pending_approval".equals(purchase.getDocStatus())) {
            purchase.setStatus("cancelled");
            purchase.setDocStatus("cancelled");
            purchase.setUpdatedBy(userId);
            auditService.log(companyId, userId, "CANCEL", "Purchase", id, "draft", "cancelled");
            return purchaseRepository.save(purchase);
        }

        // Daily close lock
        checkDailyClose(companyId, userId, purchase.getPurchaseDate(), "Purchase cancel", id);

        // Posted: require approval
        if (approvalService.hasPendingFor(companyId, "Purchase", id)) {
            throw new BusinessException("A cancellation approval is already pending for this purchase.");
        }

        String payload = "{\"action\":\"PURCHASE_CANCEL\""
                + ",\"companyId\":\"" + companyId + "\""
                + ",\"requesterId\":\"" + userId + "\""
                + ",\"purchaseId\":\"" + id + "\"}";

        ApprovalRequest ar = approvalService.requestDeferred(
                companyId, userId, "PURCHASE_CANCEL", "Purchase", id,
                "Cancellation of posted purchase " + purchase.getPurchaseNumber() + " (total: " + purchase.getTotalAmount() + ")",
                approvalService.buildMetadata(Map.of(
                        "purchaseNumber", purchase.getPurchaseNumber(),
                        "total", purchase.getTotalAmount())),
                payload);

        throw new PendingApprovalException(
                "Cancellation of purchase " + purchase.getPurchaseNumber() + " requires manager/owner approval",
                ar.getId());
    }

    /**
     * Performs the actual posted-purchase reversal.
     * Called by {@link DeferredActionService} after approval, and internally by
     * {@link #edit(Long, Long, Long, PurchaseRequest)}.
     */
    @Transactional
    public Purchase cancelAndReverse(Long companyId, Long userId, Long id) {
        Purchase purchase = getOne(companyId, id);
        if (purchase.isLocked()) throw new BusinessException("Purchase is locked");
        if ("cancelled".equals(purchase.getStatus())) throw new BusinessException("Purchase already cancelled");

        // Validate: enough stock to deduct
        for (PurchaseItem item : purchase.getItems()) {
            Product product = item.getProduct();
            BigDecimal available = product.getCurrentStock();
            if (available.compareTo(item.getQuantity()) < 0)
                throw new BusinessException("Cannot cancel: product '" + product.getName()
                        + "' has only " + available + " in stock, but " + item.getQuantity() + " would be removed.");
        }

        // Remove stock
        for (PurchaseItem item : purchase.getItems()) {
            Product product = item.getProduct();
            BigDecimal prev = product.getCurrentStock();
            BigDecimal newStock = prev.subtract(item.getQuantity());
            product.setCurrentStock(newStock);
            productRepository.save(product);

            StockMovement m = StockMovement.builder()
                    .company(purchase.getCompany())
                    .product(product)
                    .warehouseId(purchase.getWarehouseId())
                    .movementType("adjustment")
                    .quantity(item.getQuantity().negate())
                    .previousStock(prev)
                    .newStock(newStock)
                    .referenceId(id)
                    .referenceType("purchase_cancel")
                    .note("Purchase cancelled: " + purchase.getPurchaseNumber())
                    .createdBy(userId)
                    .build();
            stockMovementRepository.save(m);
            warehouseStockService.syncWarehouseDelta(companyId, purchase.getWarehouseId(),
                    product.getId(), item.getQuantity().negate());
        }

        // Close supplier debt
        final Purchase cancelledPurchase = purchase;
        debtRepository.findByCompanyIdAndRelatedPurchaseIdAndDebtType(companyId, id, "supplier")
                .ifPresent(d -> {
                    if (cancelledPurchase.getSupplier() != null && d.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
                        Supplier s = cancelledPurchase.getSupplier();
                        s.setCurrentDebt(s.getCurrentDebt().subtract(d.getRemainingAmount()).max(BigDecimal.ZERO));
                        supplierRepository.save(s);
                    }
                    d.setStatus("closed");
                    debtRepository.save(d);
                });

        // Reverse cash transactions
        Company company = companyService.getById(companyId);
        List<CashTransaction> paid = cashTransactionRepository
                .findByPurchaseIdAndStatusAndType(companyId, id, "active", "expense");
        for (CashTransaction tx : paid) {
            tx.setStatus("reversed");
            cashTransactionRepository.save(tx);
            if (isBank(tx.getPaymentSource()))
                company.setBankBalance(company.getBankBalance().add(tx.getAmount()));
            else
                company.setCashBalance(company.getCashBalance().add(tx.getAmount()));
        }
        companyService.save(company);

        purchase.setStatus("cancelled");
        purchase.setDocStatus("cancelled");
        purchase.setUpdatedBy(userId);
        purchase = purchaseRepository.save(purchase);

        // Reverse the purchase's ledger entry so the books stay consistent.
        accountingService.reversePurchase(companyId, userId, id, "Purchase " + purchase.getPurchaseNumber() + " cancelled");

        auditService.log(companyId, userId, "CANCEL", "Purchase", id, "active", "cancelled");
        return purchase;
    }

    // ─── Safe Edit ───────────────────────────────────────────────────────────

    @Transactional
    public Purchase edit(Long companyId, Long userId, Long purchaseId, PurchaseRequest req) {
        Purchase original = getOne(companyId, purchaseId);
        if (original.isLocked()) throw new BusinessException("Purchase is locked");
        if (!"posted".equals(original.getDocStatus()))
            throw new BusinessException("Only posted purchases can be edited");

        String oldDesc = "Purchase " + original.getPurchaseNumber() + " total=" + original.getTotalAmount();
        cancelAndReverse(companyId, userId, purchaseId);

        Purchase cancelled = getOne(companyId, purchaseId);
        cancelled.setStatus("active");
        cancelled.setDocStatus("draft");
        purchaseItemRepository.deleteAll(purchaseItemRepository.findByPurchaseId(purchaseId));

        Company company = companyService.getById(companyId);
        BigDecimal headerDiscount = req.getDiscountAmount() != null ? req.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal additionalCost = req.getAdditionalCost() != null ? req.getAdditionalCost() : BigDecimal.ZERO;
        BigDecimal paidAmount     = req.getPaidAmount()     != null ? req.getPaidAmount()     : BigDecimal.ZERO;
        String currency       = req.getCurrency()       != null ? req.getCurrency()       : cancelled.getCurrency();
        String paymentMethod  = req.getPaymentMethod()  != null ? req.getPaymentMethod()  : cancelled.getPaymentMethod();

        cancelled.setDiscountAmount(headerDiscount);
        cancelled.setAdditionalCost(additionalCost);
        cancelled.setPaidAmount(BigDecimal.ZERO);
        cancelled.setUnpaidAmount(BigDecimal.ZERO);
        cancelled.setTotalAmount(BigDecimal.ZERO);
        cancelled.setNote(req.getNote());
        cancelled.setPaymentMethod(paymentMethod);
        cancelled.setCurrency(currency);
        cancelled.setUpdatedBy(userId);

        if (req.getSupplierId() != null) {
            supplierRepository.findByCompanyIdAndId(companyId, req.getSupplierId())
                    .ifPresent(cancelled::setSupplier);
        }
        cancelled.setWarehouseId(warehouseStockService.resolveWarehouseId(companyId, req.getWarehouseId()));
        cancelled = purchaseRepository.save(cancelled);

        List<PurchaseItem> newItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (PurchaseRequest.PurchaseItemRequest itemReq : req.getItems()) {
            BigDecimal itemDiscount = itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO;
            Product product = productRepository.findByCompanyIdAndId(companyId, itemReq.getProductId())
                    .orElseThrow(() -> BusinessException.notFound("Product " + itemReq.getProductId()));
            if (itemReq.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("Quantity must be > 0 for " + product.getName());

            BigDecimal lineTotal = itemReq.getPurchasePrice().multiply(itemReq.getQuantity()).subtract(itemDiscount);
            PurchaseItem item = PurchaseItem.builder()
                    .purchase(cancelled)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .purchasePrice(itemReq.getPurchasePrice())
                    .discountAmount(itemDiscount)
                    .totalAmount(lineTotal)
                    .build();
            newItems.add(item);
            subtotal = subtotal.add(lineTotal);
        }

        BigDecimal total = subtotal.add(additionalCost).subtract(headerDiscount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;
        if (paidAmount.compareTo(total) > 0) throw new BusinessException("Paid amount exceeds total");

        cancelled.setTotalAmount(total);
        cancelled.setPaidAmount(paidAmount);
        cancelled.setUnpaidAmount(total.subtract(paidAmount));
        cancelled = purchaseRepository.save(cancelled);

        final Purchase fp = cancelled;
        newItems.forEach(i -> i.setPurchase(fp));
        purchaseItemRepository.saveAll(newItems);

        Purchase result = postInternal(companyId, userId, cancelled, newItems, company,
                paymentMethod, currency, paidAmount, total, cancelled.getPurchaseNumber(), purchaseId);
        auditService.log(companyId, userId, "EDIT", "Purchase", purchaseId, oldDesc,
                "Purchase " + result.getPurchaseNumber() + " total=" + result.getTotalAmount());
        return result;
    }

    // ─── AddPayment ──────────────────────────────────────────────────────────

    @Transactional
    public Purchase addPayment(Long companyId, Long userId, Long purchaseId,
                               BigDecimal amount, String method, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("Payment amount must be greater than zero");
        Purchase purchase = getOne(companyId, purchaseId);
        if ("cancelled".equals(purchase.getStatus())) throw new BusinessException("Cannot pay a cancelled purchase");
        if (!"posted".equals(purchase.getDocStatus()))
            throw new BusinessException("Cannot add payment to a " + purchase.getDocStatus() + " purchase");
        if (amount.compareTo(purchase.getUnpaidAmount()) > 0)
            throw new BusinessException("Payment exceeds unpaid amount (" + purchase.getUnpaidAmount() + ")");

        purchase.setPaidAmount(purchase.getPaidAmount().add(amount));
        purchase.setUnpaidAmount(purchase.getUnpaidAmount().subtract(amount));

        Debt debt = debtRepository.findByCompanyIdAndRelatedPurchaseIdAndDebtType(companyId, purchaseId, "supplier").orElse(null);
        if (debt != null) {
            debt.setPaidAmount(debt.getPaidAmount().add(amount));
            debt.setRemainingAmount(debt.getRemainingAmount().subtract(amount));
            debt.setStatus(debt.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0 ? "closed" : "partial");
            debtRepository.save(debt);
            if (purchase.getSupplier() != null) {
                Supplier s = purchase.getSupplier();
                s.setCurrentDebt(s.getCurrentDebt().subtract(amount).max(BigDecimal.ZERO));
                supplierRepository.save(s);
            }
        }

        Company company = companyService.getById(companyId);
        String source = isBank(method) ? "bank" : method;
        CashTransaction ct = CashTransaction.builder()
                .company(company)
                .transactionType("expense")
                .paymentSource(source)
                .amount(amount)
                .currency(purchase.getCurrency())
                .category("supplier_payment")
                .relatedPurchaseId(purchaseId)
                .relatedSupplierId(purchase.getSupplier() != null ? purchase.getSupplier().getId() : null)
                .transactionDate(LocalDateTime.now())
                .note(note != null && !note.isBlank() ? note : "Payment for purchase " + purchase.getPurchaseNumber())
                .createdBy(userId)
                .build();
        cashTransactionRepository.save(ct);
        if (isBank(source)) company.setBankBalance(company.getBankBalance().subtract(amount));
        else company.setCashBalance(company.getCashBalance().subtract(amount));
        companyService.save(company);

        auditService.log(companyId, userId, "PAYMENT", "Purchase", purchaseId, null, "paid=" + amount);
        return purchaseRepository.save(purchase);
    }

    // ─── Query ───────────────────────────────────────────────────────────────

    public Page<Purchase> list(Long companyId, Specification<Purchase> spec, Pageable pageable) {
        Specification<Purchase> withCompany = Specification.<Purchase>where(
                (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId)
        ).and(spec);
        return purchaseRepository.findAll(withCompany, pageable);
    }

    public Purchase getOne(Long companyId, Long id) {
        return purchaseRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> BusinessException.notFound("Purchase"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String generatePurchaseNumber(Long companyId) {
        String date = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
        long count = purchaseRepository.countByCompanyId(companyId) + 1;
        String number = "PO-" + date + "-" + String.format("%04d", count);
        while (purchaseRepository.existsByCompanyIdAndPurchaseNumber(companyId, number)) {
            count++;
            number = "PO-" + date + "-" + String.format("%04d", count);
        }
        return number;
    }

    private boolean isBank(String method) {
        return method != null && (method.equals("bank") || method.equals("bank_transfer"));
    }

    private void checkDailyClose(Long companyId, Long userId, LocalDateTime dateTime, String operation, Long entityId) {
        if (dateTime == null) return;
        LocalDate date = dateTime.toLocalDate();
        if (dailyCloseRepository.existsByCompanyIdAndCloseDateAndStatus(companyId, date, "closed")) {
            auditService.log(companyId, userId, "BLOCKED_DAILY_CLOSE", "Purchase", entityId,
                    null, operation + " blocked: date " + date + " is closed");
            throw new BusinessException("Date " + date + " is closed for editing. A correction requires owner approval.");
        }
    }
}
