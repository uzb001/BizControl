package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.dto.request.SaleRequest;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.entity.ApprovalRequest;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.exception.PendingApprovalException;
import uz.bizcontrol.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final StockMovementRepository stockMovementRepository;
    private final DebtRepository debtRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final CompanyService companyService;
    private final AuditService auditService;
    private final AlertService alertService;
    private final ApprovalService approvalService;
    private final DailyCloseRepository dailyCloseRepository;
    private final WarehouseStockService warehouseStockService;
    private final uz.bizcontrol.accounting.AccountingService accountingService;

    // ─── Create ──────────────────────────────────────────────────────────────

    @Transactional
    public Sale create(Long companyId, Long userId, SaleRequest req) {
        Company company = companyService.getById(companyId);

        BigDecimal headerDiscount = req.getDiscountAmount() != null ? req.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal paidAmount     = req.getPaidAmount()     != null ? req.getPaidAmount()     : BigDecimal.ZERO;
        String currency       = req.getCurrency()       != null ? req.getCurrency()       : "UZS";
        String paymentMethod  = req.getPaymentMethod()  != null ? req.getPaymentMethod()  : "cash";
        LocalDateTime saleDate = req.getSaleDate()      != null ? req.getSaleDate()       : LocalDateTime.now();

        if (headerDiscount.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("Discount cannot be negative");
        if (paidAmount.compareTo(BigDecimal.ZERO) < 0)     throw new BusinessException("Paid amount cannot be negative");

        // Daily close lock
        checkDailyClose(companyId, userId, saleDate, "Sale create", null);

        String saleNumber = generateSaleNumber(companyId);

        // ── Step 1: Save draft skeleton ───────────────────────────────────────
        Sale sale = Sale.builder()
                .company(company)
                .saleNumber(saleNumber)
                .saleDate(saleDate)
                .discountAmount(headerDiscount)
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

        if (req.getCustomerId() != null) {
            customerRepository.findByCompanyIdAndId(companyId, req.getCustomerId())
                    .ifPresent(sale::setCustomer);
        }
        sale.setWarehouseId(warehouseStockService.resolveWarehouseId(companyId, req.getWarehouseId()));
        sale = saleRepository.save(sale);
        Long savedSaleId = sale.getId();

        // ── Step 2: Validate items (stock, quantities) ────────────────────────
        List<SaleItem> items  = new ArrayList<>();
        BigDecimal subtotal   = BigDecimal.ZERO;
        BigDecimal totalCost  = BigDecimal.ZERO;

        for (SaleRequest.SaleItemRequest itemReq : req.getItems()) {
            BigDecimal itemDiscount = itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO;

            Product product = productRepository.findByCompanyIdAndId(companyId, itemReq.getProductId())
                    .orElseThrow(() -> BusinessException.notFound("Product " + itemReq.getProductId()));

            if (itemReq.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("Quantity must be greater than zero for: " + product.getName());

            if (product.getCurrentStock().compareTo(itemReq.getQuantity()) < 0)
                throw new BusinessException("Insufficient stock for: " + product.getName()
                        + ". Available: " + product.getCurrentStock());

            // When a specific source warehouse is chosen, it must hold enough too.
            if (req.getWarehouseId() != null) {
                BigDecimal whAvail = warehouseStockService.availableInWarehouse(
                        companyId, sale.getWarehouseId(), product.getId());
                if (whAvail.compareTo(itemReq.getQuantity()) < 0)
                    throw new BusinessException("Insufficient stock in selected warehouse for: "
                            + product.getName() + ". Available there: " + whAvail);
            }

            BigDecimal lineTotal  = itemReq.getSellingPrice().multiply(itemReq.getQuantity()).subtract(itemDiscount);
            BigDecimal costOfGoods = product.getPurchasePrice().multiply(itemReq.getQuantity());
            BigDecimal profit      = lineTotal.subtract(costOfGoods);

            SaleItem item = SaleItem.builder()
                    .sale(sale)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .sellingPrice(itemReq.getSellingPrice())
                    .purchaseCost(product.getPurchasePrice())
                    .discountAmount(itemDiscount)
                    .totalAmount(lineTotal)
                    .profitAmount(profit)
                    .build();
            items.add(item);
            subtotal  = subtotal.add(lineTotal);
            totalCost = totalCost.add(costOfGoods);
        }

        BigDecimal total = subtotal.subtract(headerDiscount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;
        if (paidAmount.compareTo(total) > 0) throw new BusinessException("Paid amount exceeds total");

        sale.setTotalAmount(total);
        sale.setPaidAmount(paidAmount);
        sale.setUnpaidAmount(total.subtract(paidAmount));
        sale = saleRepository.save(sale);

        final Sale savedSale = sale;
        items.forEach(i -> i.setSale(savedSale));
        saleItemRepository.saveAll(items);

        // ── Step 3: Approval checks ───────────────────────────────────────────
        boolean needsApproval = false;
        String approvalReason = null;

        if (approvalService.isSaleBelowCost(total, totalCost)) {
            needsApproval = true;
            approvalReason = "SALE_BELOW_COST";
        } else if (approvalService.isHighDiscount(headerDiscount, subtotal)) {
            needsApproval = true;
            approvalReason = "HIGH_DISCOUNT";
        }

        if (needsApproval) {
            ApprovalRequest ar = approvalService.request(
                    companyId, userId, approvalReason, "Sale", savedSaleId,
                    approvalReason + " on sale " + saleNumber + " total=" + total,
                    approvalService.buildMetadata(Map.of(
                            "saleId", savedSaleId, "total", total, "cost", totalCost,
                            "discount", headerDiscount, "saleNumber", saleNumber)));
            sale.setDocStatus("pending_approval");
            sale.setPendingApprovalId(ar.getId());
            return saleRepository.save(sale);
        }

        // ── Step 4: Post immediately (no approval needed) ─────────────────────
        return postInternal(companyId, userId, sale, items, company, paymentMethod, currency,
                paidAmount, total, saleNumber, savedSaleId);
    }

    // ─── Post (approve-and-apply side effects) ────────────────────────────────

    @Transactional
    public Sale post(Long companyId, Long userId, Long saleId) {
        Sale sale = getOne(companyId, saleId);
        if (sale.isLocked()) throw new BusinessException("Sale is locked");
        if ("pending_approval".equals(sale.getDocStatus()))
            throw new BusinessException("Sale is pending approval and cannot be manually posted. It will be posted automatically once approved.");
        if ("posted".equals(sale.getDocStatus()))   throw new BusinessException("Sale already posted");
        if ("cancelled".equals(sale.getDocStatus())) throw new BusinessException("Sale is cancelled");

        // Reload items with products
        List<SaleItem> items = saleItemRepository.findBySaleId(saleId);
        Company company = companyService.getById(companyId);

        return postInternal(companyId, userId, sale, items, company,
                sale.getPaymentMethod(), sale.getCurrency(),
                sale.getPaidAmount(), sale.getTotalAmount(), sale.getSaleNumber(), saleId);
    }

    private Sale postInternal(Long companyId, Long userId, Sale sale, List<SaleItem> items,
                               Company company, String paymentMethod, String currency,
                               BigDecimal paidAmount, BigDecimal total, String saleNumber, Long savedSaleId) {

        BigDecimal unpaid = total.subtract(paidAmount);

        if (unpaid.compareTo(BigDecimal.ZERO) > 0 && sale.getCustomer() == null)
            throw new BusinessException("A customer must be selected when the sale has an unpaid balance.");

        // Apply stock changes (product is EAGER on SaleItem, always loaded)
        for (SaleItem item : items) {
            Product product = item.getProduct();
            if (product == null) continue;  // safety guard — should never happen

            BigDecimal prev = product.getCurrentStock();
            BigDecimal newStock = prev.subtract(item.getQuantity());
            product.setCurrentStock(newStock);
            productRepository.save(product);

            StockMovement movement = StockMovement.builder()
                    .company(company)
                    .product(product)
                    .warehouseId(sale.getWarehouseId())
                    .movementType("sale")
                    .quantity(item.getQuantity().negate())
                    .previousStock(prev)
                    .newStock(newStock)
                    .referenceId(savedSaleId)
                    .referenceType("sale")
                    .note("Sale " + saleNumber)
                    .createdBy(userId)
                    .build();
            stockMovementRepository.save(movement);
            warehouseStockService.syncWarehouseDelta(companyId, sale.getWarehouseId(),
                    product.getId(), item.getQuantity().negate());

            if (newStock.compareTo(BigDecimal.ZERO) == 0) {
                alertService.create(companyId, "out_of_stock", "Out of stock: " + product.getName(),
                        "Product out of stock after sale", "Product", product.getId());
            } else if (product.getMinStockLevel() != null && newStock.compareTo(product.getMinStockLevel()) <= 0) {
                alertService.create(companyId, "low_stock", "Low stock: " + product.getName(),
                        "Stock " + newStock + " " + product.getUnit() + " after sale", "Product", product.getId());
            }
        }

        // Cash transaction for paid portion
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            String source = isBank(paymentMethod) ? "bank" : paymentMethod;
            createMoneyTransaction(company, userId, "income", source, paidAmount, currency,
                    "sale_payment", savedSaleId, sale.getCustomer() != null ? sale.getCustomer().getId() : null,
                    "Payment for sale " + saleNumber);
            updateBalance(company, source, paidAmount, true);
        }

        // Debt for unpaid portion
        if (unpaid.compareTo(BigDecimal.ZERO) > 0 && sale.getCustomer() != null) {
            Debt debt = Debt.builder()
                    .company(company)
                    .debtType("customer")
                    .customer(sale.getCustomer())
                    .relatedSaleId(savedSaleId)
                    .originalAmount(unpaid)
                    .paidAmount(BigDecimal.ZERO)
                    .remainingAmount(unpaid)
                    .currency(currency)
                    .status("open")
                    .createdBy(userId)
                    .build();
            debtRepository.save(debt);
            Customer cust = sale.getCustomer();
            cust.setCurrentDebt(cust.getCurrentDebt().add(unpaid));
            customerRepository.save(cust);
        }

        sale.setDocStatus("posted");
        sale.setPendingApprovalId(null);
        sale = saleRepository.save(sale);

        // Double-entry ledger: record the sale as a balanced journal entry (atomic).
        accountingService.recordSale(companyId, userId, sale, items, total, paidAmount);

        auditService.log(companyId, userId, "CREATE", "Sale", savedSaleId, null,
                "Sale " + saleNumber + " total=" + total);
        return sale;
    }

    // ─── Cancel ──────────────────────────────────────────────────────────────

    /**
     * Public cancel endpoint — gates posted-sale cancellations behind an approval workflow.
     * Draft/pending_approval sales are cancelled immediately (no side effects to reverse).
     * <p>
     * For posted sales this method creates a deferred {@code SALE_CANCEL} approval request
     * (committed in a separate {@code REQUIRES_NEW} transaction) and then throws
     * {@link PendingApprovalException}, which the global handler maps to HTTP 202.
     * The actual reversal happens via {@link #cancelAndReverse(Long, Long, Long)} once approved.
     */
    @Transactional
    public Sale cancel(Long companyId, Long userId, Long id) {
        Sale sale = getOne(companyId, id);
        if (sale.isLocked()) throw new BusinessException("Sale is locked and cannot be cancelled");
        if ("cancelled".equals(sale.getStatus())) throw new BusinessException("Sale already cancelled");
        if ("draft".equals(sale.getDocStatus()) || "pending_approval".equals(sale.getDocStatus())) {
            // Draft/pending: cancel immediately, nothing to reverse
            sale.setStatus("cancelled");
            sale.setDocStatus("cancelled");
            sale.setUpdatedBy(userId);
            auditService.log(companyId, userId, "CANCEL", "Sale", id, "draft", "cancelled");
            return saleRepository.save(sale);
        }

        // Daily close lock
        checkDailyClose(companyId, userId, sale.getSaleDate(), "Sale cancel", id);

        // Posted sale: require approval (guard against duplicate pending requests)
        if (approvalService.hasPendingFor(companyId, "Sale", id)) {
            throw new BusinessException("A cancellation approval is already pending for this sale. Check the approvals list.");
        }

        String payload = "{\"action\":\"SALE_CANCEL\""
                + ",\"companyId\":\"" + companyId + "\""
                + ",\"requesterId\":\"" + userId + "\""
                + ",\"saleId\":\"" + id + "\"}";

        ApprovalRequest ar = approvalService.requestDeferred(
                companyId, userId, "SALE_CANCEL", "Sale", id,
                "Cancellation of posted sale " + sale.getSaleNumber() + " (total: " + sale.getTotalAmount() + ")",
                approvalService.buildMetadata(Map.of(
                        "saleNumber", sale.getSaleNumber(),
                        "total", sale.getTotalAmount())),
                payload);

        throw new PendingApprovalException(
                "Cancellation of sale " + sale.getSaleNumber() + " requires manager/owner approval",
                ar.getId());
    }

    /**
     * Performs the actual posted-sale reversal.
     * Called by {@link DeferredActionService} after approval, and internally by
     * {@link #edit(Long, Long, Long, SaleRequest)} (edit = cancel + recreate).
     * Does NOT create an additional approval request.
     */
    @Transactional
    public Sale cancelAndReverse(Long companyId, Long userId, Long id) {
        Sale sale = getOne(companyId, id);
        if (sale.isLocked()) throw new BusinessException("Sale is locked and cannot be cancelled");
        if ("cancelled".equals(sale.getStatus())) throw new BusinessException("Sale already cancelled");

        // Return stock for each item
        for (SaleItem item : sale.getItems()) {
            Product product = item.getProduct();
            BigDecimal prev = product.getCurrentStock();
            BigDecimal newStock = prev.add(item.getQuantity());
            product.setCurrentStock(newStock);
            productRepository.save(product);

            StockMovement m = StockMovement.builder()
                    .company(sale.getCompany())
                    .product(product)
                    .warehouseId(sale.getWarehouseId())
                    .movementType("return")
                    .quantity(item.getQuantity())
                    .previousStock(prev)
                    .newStock(newStock)
                    .referenceId(id)
                    .referenceType("sale_cancel")
                    .note("Sale cancelled: " + sale.getSaleNumber())
                    .createdBy(userId)
                    .build();
            stockMovementRepository.save(m);
            warehouseStockService.syncWarehouseDelta(companyId, sale.getWarehouseId(),
                    product.getId(), item.getQuantity());
        }

        // Close customer debt
        final Sale cancelledSale = sale;
        debtRepository.findByCompanyIdAndRelatedSaleIdAndDebtType(companyId, id, "customer")
                .ifPresent(d -> {
                    if (cancelledSale.getCustomer() != null && d.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
                        Customer c = cancelledSale.getCustomer();
                        c.setCurrentDebt(c.getCurrentDebt().subtract(d.getRemainingAmount()).max(BigDecimal.ZERO));
                        customerRepository.save(c);
                    }
                    d.setStatus("closed");
                    debtRepository.save(d);
                });

        // Reverse cash transactions
        Company company = companyService.getById(companyId);
        List<CashTransaction> paid = cashTransactionRepository
                .findBySaleIdAndStatusAndType(companyId, id, "active", "income");
        for (CashTransaction tx : paid) {
            tx.setStatus("reversed");
            cashTransactionRepository.save(tx);
            if (isBank(tx.getPaymentSource()))
                company.setBankBalance(company.getBankBalance().subtract(tx.getAmount()).max(BigDecimal.ZERO));
            else
                company.setCashBalance(company.getCashBalance().subtract(tx.getAmount()).max(BigDecimal.ZERO));
        }
        companyService.save(company);

        sale.setStatus("cancelled");
        sale.setDocStatus("cancelled");
        sale.setUpdatedBy(userId);
        sale = saleRepository.save(sale);

        // Reverse the sale's ledger entry so the books stay consistent (no double counting).
        accountingService.reverseSale(companyId, userId, id, "Sale " + sale.getSaleNumber() + " cancelled");

        auditService.log(companyId, userId, "CANCEL", "Sale", id, "active", "cancelled");
        return sale;
    }

    // ─── Safe Edit ───────────────────────────────────────────────────────────

    @Transactional
    public Sale edit(Long companyId, Long userId, Long saleId, SaleRequest req) {
        Sale original = getOne(companyId, saleId);
        if (original.isLocked()) throw new BusinessException("Sale is locked");
        if (!"posted".equals(original.getDocStatus()))
            throw new BusinessException("Only posted sales can be edited. Current status: " + original.getDocStatus());

        String oldDesc = "Sale " + original.getSaleNumber() + " total=" + original.getTotalAmount();

        // Step 1: Reverse all side effects without triggering the approval gate
        cancelAndReverse(companyId, userId, saleId);

        // Step 2: Reload the cancelled sale and reset to draft for re-creation
        Sale cancelled = getOne(companyId, saleId);
        cancelled.setStatus("active");
        cancelled.setDocStatus("draft");
        cancelled = saleRepository.save(cancelled);

        // Step 3: Re-apply new items (delete old items first)
        saleItemRepository.deleteAll(saleItemRepository.findBySaleId(saleId));

        // Step 4: Rebuild using create logic (reuse the same saleId)
        Company company = companyService.getById(companyId);

        BigDecimal headerDiscount = req.getDiscountAmount() != null ? req.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal paidAmount     = req.getPaidAmount()     != null ? req.getPaidAmount()     : BigDecimal.ZERO;
        String currency       = req.getCurrency()       != null ? req.getCurrency()       : cancelled.getCurrency();
        String paymentMethod  = req.getPaymentMethod()  != null ? req.getPaymentMethod()  : cancelled.getPaymentMethod();

        cancelled.setDiscountAmount(headerDiscount);
        cancelled.setPaidAmount(BigDecimal.ZERO);
        cancelled.setUnpaidAmount(BigDecimal.ZERO);
        cancelled.setTotalAmount(BigDecimal.ZERO);
        cancelled.setNote(req.getNote());
        cancelled.setPaymentMethod(paymentMethod);
        cancelled.setCurrency(currency);
        cancelled.setUpdatedBy(userId);

        if (req.getCustomerId() != null) {
            customerRepository.findByCompanyIdAndId(companyId, req.getCustomerId())
                    .ifPresent(cancelled::setCustomer);
        }
        cancelled.setWarehouseId(warehouseStockService.resolveWarehouseId(companyId, req.getWarehouseId()));
        cancelled = saleRepository.save(cancelled);

        List<SaleItem> newItems = new ArrayList<>();
        BigDecimal subtotal  = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (SaleRequest.SaleItemRequest itemReq : req.getItems()) {
            BigDecimal itemDiscount = itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO;
            Product product = productRepository.findByCompanyIdAndId(companyId, itemReq.getProductId())
                    .orElseThrow(() -> BusinessException.notFound("Product " + itemReq.getProductId()));
            if (itemReq.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("Quantity must be > 0 for " + product.getName());
            if (product.getCurrentStock().compareTo(itemReq.getQuantity()) < 0)
                throw new BusinessException("Insufficient stock for: " + product.getName());
            if (req.getWarehouseId() != null) {
                BigDecimal whAvail = warehouseStockService.availableInWarehouse(
                        companyId, cancelled.getWarehouseId(), product.getId());
                if (whAvail.compareTo(itemReq.getQuantity()) < 0)
                    throw new BusinessException("Insufficient stock in selected warehouse for: "
                            + product.getName() + ". Available there: " + whAvail);
            }

            BigDecimal lineTotal  = itemReq.getSellingPrice().multiply(itemReq.getQuantity()).subtract(itemDiscount);
            BigDecimal costOfGoods = product.getPurchasePrice().multiply(itemReq.getQuantity());
            BigDecimal profit      = lineTotal.subtract(costOfGoods);

            SaleItem item = SaleItem.builder()
                    .sale(cancelled)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .sellingPrice(itemReq.getSellingPrice())
                    .purchaseCost(product.getPurchasePrice())
                    .discountAmount(itemDiscount)
                    .totalAmount(lineTotal)
                    .profitAmount(profit)
                    .build();
            newItems.add(item);
            subtotal  = subtotal.add(lineTotal);
            totalCost = totalCost.add(costOfGoods);
        }

        BigDecimal total = subtotal.subtract(headerDiscount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;
        if (paidAmount.compareTo(total) > 0) throw new BusinessException("Paid amount exceeds total");

        cancelled.setTotalAmount(total);
        cancelled.setPaidAmount(paidAmount);
        cancelled.setUnpaidAmount(total.subtract(paidAmount));
        cancelled = saleRepository.save(cancelled);

        final Sale savedSale = cancelled;
        newItems.forEach(i -> i.setSale(savedSale));
        saleItemRepository.saveAll(newItems);

        Sale result = postInternal(companyId, userId, cancelled, newItems, company,
                paymentMethod, currency, paidAmount, total, cancelled.getSaleNumber(), saleId);

        auditService.log(companyId, userId, "EDIT", "Sale", saleId, oldDesc,
                "Sale " + result.getSaleNumber() + " total=" + result.getTotalAmount());
        return result;
    }

    // ─── AddPayment ──────────────────────────────────────────────────────────

    @Transactional
    public Sale addPayment(Long companyId, Long userId, Long saleId,
                           BigDecimal amount, String method, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("Payment amount must be greater than zero");
        Sale sale = getOne(companyId, saleId);
        if ("cancelled".equals(sale.getStatus())) throw new BusinessException("Cannot pay a cancelled sale");
        if (!"posted".equals(sale.getDocStatus()))
            throw new BusinessException("Cannot add payment to a " + sale.getDocStatus() + " sale");
        if (amount.compareTo(sale.getUnpaidAmount()) > 0)
            throw new BusinessException("Payment exceeds unpaid amount (" + sale.getUnpaidAmount() + ")");

        sale.setPaidAmount(sale.getPaidAmount().add(amount));
        sale.setUnpaidAmount(sale.getUnpaidAmount().subtract(amount));

        Debt debt = debtRepository.findByCompanyIdAndRelatedSaleIdAndDebtType(companyId, saleId, "customer").orElse(null);
        if (debt != null) {
            debt.setPaidAmount(debt.getPaidAmount().add(amount));
            debt.setRemainingAmount(debt.getRemainingAmount().subtract(amount));
            debt.setStatus(debt.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0 ? "closed" : "partial");
            debtRepository.save(debt);
            if (sale.getCustomer() != null) {
                Customer c = sale.getCustomer();
                c.setCurrentDebt(c.getCurrentDebt().subtract(amount).max(BigDecimal.ZERO));
                customerRepository.save(c);
            }
        }

        Company company = companyService.getById(companyId);
        String source = isBank(method) ? "bank" : method;
        createMoneyTransaction(company, userId, "income", source, amount, sale.getCurrency(),
                "sale_payment", saleId, sale.getCustomer() != null ? sale.getCustomer().getId() : null,
                note != null && !note.isBlank() ? note : "Payment for sale " + sale.getSaleNumber());
        updateBalance(company, source, amount, true);

        auditService.log(companyId, userId, "PAYMENT", "Sale", saleId, null, "paid=" + amount);
        return saleRepository.save(sale);
    }

    // ─── Query ───────────────────────────────────────────────────────────────

    public Page<Sale> list(Long companyId, Specification<Sale> spec, Pageable pageable) {
        Specification<Sale> withCompany = Specification.<Sale>where(
                (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId)
        ).and(spec);
        return saleRepository.findAll(withCompany, pageable);
    }

    public Sale getOne(Long companyId, Long id) {
        return saleRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> BusinessException.notFound("Sale"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String generateSaleNumber(Long companyId) {
        String date = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
        long count = saleRepository.countByCompanyId(companyId) + 1;
        String number = "SL-" + date + "-" + String.format("%04d", count);
        // Retry on collision (extremely rare — concurrent requests on same millisecond)
        while (saleRepository.existsByCompanyIdAndSaleNumber(companyId, number)) {
            count++;
            number = "SL-" + date + "-" + String.format("%04d", count);
        }
        return number;
    }

    private boolean isBank(String method) {
        return method != null && (method.equals("bank") || method.equals("bank_transfer"));
    }

    private void createMoneyTransaction(Company company, Long userId, String type, String source,
                                        BigDecimal amount, String currency, String category,
                                        Long saleId, Long customerId, String note) {
        CashTransaction ct = CashTransaction.builder()
                .company(company)
                .transactionType(type)
                .paymentSource(source)
                .amount(amount)
                .currency(currency)
                .category(category)
                .relatedSaleId(saleId)
                .relatedCustomerId(customerId)
                .transactionDate(LocalDateTime.now())
                .note(note)
                .createdBy(userId)
                .build();
        cashTransactionRepository.save(ct);
    }

    private void updateBalance(Company company, String source, BigDecimal amount, boolean isIncome) {
        if (isBank(source) || "bank".equals(source)) {
            company.setBankBalance(isIncome
                    ? company.getBankBalance().add(amount)
                    : company.getBankBalance().subtract(amount));
        } else {
            company.setCashBalance(isIncome
                    ? company.getCashBalance().add(amount)
                    : company.getCashBalance().subtract(amount));
        }
        companyService.save(company);
    }

    private void checkDailyClose(Long companyId, Long userId, LocalDateTime dateTime, String operation, Long entityId) {
        if (dateTime == null) return;
        LocalDate date = dateTime.toLocalDate();
        if (dailyCloseRepository.existsByCompanyIdAndCloseDateAndStatus(companyId, date, "closed")) {
            auditService.log(companyId, userId, "BLOCKED_DAILY_CLOSE", "Sale", entityId,
                    null, operation + " blocked: date " + date + " is closed");
            throw new BusinessException("Date " + date + " is closed for editing. A correction requires owner approval.");
        }
    }
}
