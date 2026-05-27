package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.accounting.Account;
import uz.bizcontrol.accounting.ChartOfAccounts;
import uz.bizcontrol.accounting.ChartOfAccountsService;
import uz.bizcontrol.accounting.JournalEntry;
import uz.bizcontrol.accounting.JournalLine;
import uz.bizcontrol.accounting.JournalService;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The logistics & landed-cost engine. A {@link LogisticsOrder} represents a real
 * physical movement of inventory from a source warehouse (typically foreign) to a
 * destination warehouse (typically domestic), with proportional capitalisation of
 * shipping / customs / broker / insurance / other expenses across the items.
 *
 * <p><b>Lifecycle</b>: {@code draft} → {@code confirmed} (one-way) or
 * {@code draft} → {@code cancelled} (one-way; only drafts can be cancelled).
 * Confirmed orders are immutable so the ledger remains a faithful audit trail.
 *
 * <p><b>What confirmation does, atomically, inside one transaction</b>:
 * <ol>
 *   <li>Validates the order has items, expenses and source stock for every item.</li>
 *   <li>Calls {@link WarehouseStockService#transfer} per item — source decreases,
 *       destination increases, {@code StockMovement}s + a {@code StockTransfer}
 *       row are written, low-stock alerts fire (existing rails).</li>
 *   <li>Computes the proportional landed-cost allocation per item
 *       ({@code allocated = expensesTotal × itemValue / itemsValue}) and persists
 *       {@link LandedCostAllocation} rows.</li>
 *   <li>For each expense, writes a {@link CashTransaction} (cash or bank) and
 *       decrements the corresponding company balance.</li>
 *   <li>Posts ONE balanced {@link JournalEntry} debiting the matching expense
 *       account (customs / logistics / operating) and crediting cash + bank
 *       split by payment source.</li>
 *   <li>Writes audit logs.</li>
 * </ol>
 *
 * <p><b>What confirmation deliberately does NOT do</b>:
 * <ul>
 *   <li>Touch {@link Supplier#getCurrentDebt()} — supplier debt is only purchases.</li>
 *   <li>Convert currencies — the order keeps its single currency end-to-end.</li>
 *   <li>Mutate {@link Product#getPurchasePrice()} — the global price is left to the
 *       operator; the landed cost is shown as a deliberate, reviewable suggestion.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsService {

    private static final Set<String> VALID_EXPENSE_TYPES =
            Set.of("shipping", "customs", "broker", "insurance", "other");
    private static final Set<String> VALID_PAYMENT_SOURCES = Set.of("cash", "bank");

    private final LogisticsOrderRepository orderRepository;
    private final LogisticsOrderItemRepository itemRepository;
    private final LogisticsExpenseRepository expenseRepository;
    private final LandedCostAllocationRepository allocationRepository;
    private final uz.bizcontrol.repository.LogisticsExpensePaymentRepository paymentRepository;

    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final CashTransactionRepository cashTransactionRepository;

    private final WarehouseStockService warehouseStockService;
    private final CompanyService companyService;
    private final AuditService auditService;
    private final ChartOfAccountsService chartOfAccountsService;
    private final JournalService journalService;

    // ── List & read ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LogisticsOrder getOne(Long companyId, Long id) {
        return orderRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> BusinessException.notFound("LogisticsOrder"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDetail(Long companyId, Long id, boolean canSeeLandedCost) {
        LogisticsOrder order = getOne(companyId, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("order", order);
        out.put("items", itemRepository.findByLogisticsOrderIdOrderByIdAsc(id));
        out.put("expenses", expenseRepository.findByLogisticsOrderIdOrderByIdAsc(id));
        out.put("payments", paymentRepository.findByLogisticsOrderIdOrderByPaidAtAsc(id));
        if (canSeeLandedCost) {
            out.put("allocations", allocationRepository.findByLogisticsOrderIdOrderByIdAsc(id));
        }
        return out;
    }

    // ── Draft CRUD ─────────────────────────────────────────────────────────────

    /**
     * Create a draft logistics order. Items + expenses can be passed inline or added
     * later via dedicated endpoints; both paths converge on {@link #recalculate}.
     */
    @Transactional
    public LogisticsOrder createDraft(Long companyId, Long userId, Map<String, Object> body) {
        Long srcWh = reqLong(body, "sourceWarehouseId");
        Long dstWh = reqLong(body, "destinationWarehouseId");
        if (srcWh.equals(dstWh))
            throw new BusinessException("Source and destination warehouses must differ");
        validateWarehouse(companyId, srcWh, "Source");
        validateWarehouse(companyId, dstWh, "Destination");

        String currency = strDefault(body.get("currency"), "UZS");
        BigDecimal rate = bdOrDefault(body.get("exchangeRate"), BigDecimal.ONE);
        if (rate.signum() <= 0) throw new BusinessException("Exchange rate must be positive");

        LogisticsOrder order = LogisticsOrder.builder()
                .companyId(companyId)
                .orderNumber(generateOrderNumber(companyId))
                .sourceCountryId(optLong(body.get("sourceCountryId")))
                .sourceWarehouseId(srcWh)
                .destinationCountryId(optLong(body.get("destinationCountryId")))
                .destinationWarehouseId(dstWh)
                .supplierId(optLong(body.get("supplierId")))
                .currency(currency)
                .exchangeRate(rate)
                .status("draft")
                .note(str(body.get("note")))
                .createdBy(userId)
                .build();
        order = orderRepository.save(order);

        // Inline items
        Object itemsRaw = body.get("items");
        if (itemsRaw instanceof List<?> itemList) {
            for (Object o : itemList) if (o instanceof Map<?, ?> m) addItemInternal(companyId, order, asStringMap(m));
        }
        // Inline expenses
        Object expRaw = body.get("expenses");
        if (expRaw instanceof List<?> expList) {
            for (Object o : expList) if (o instanceof Map<?, ?> m) addExpenseInternal(companyId, order, asStringMap(m));
        }
        recalculate(order);
        order = orderRepository.save(order);

        auditService.log(companyId, userId, "CREATE", "LogisticsOrder", order.getId(),
                null, "Logistics draft " + order.getOrderNumber());
        return order;
    }

    @Transactional
    public LogisticsOrderItem addItem(Long companyId, Long userId, Long orderId, Map<String, Object> body) {
        LogisticsOrder order = requireDraft(companyId, orderId);
        LogisticsOrderItem item = addItemInternal(companyId, order, body);
        recalculate(order);
        orderRepository.save(order);
        auditService.log(companyId, userId, "ADD_ITEM", "LogisticsOrder", orderId, null,
                "+item product=" + item.getProductId() + " qty=" + item.getQuantity());
        return item;
    }

    @Transactional
    public void removeItem(Long companyId, Long userId, Long orderId, Long itemId) {
        LogisticsOrder order = requireDraft(companyId, orderId);
        LogisticsOrderItem item = itemRepository.findById(itemId)
                .filter(i -> i.getLogisticsOrderId().equals(orderId))
                .orElseThrow(() -> BusinessException.notFound("LogisticsOrderItem"));
        itemRepository.delete(item);
        recalculate(order);
        orderRepository.save(order);
        auditService.log(companyId, userId, "REMOVE_ITEM", "LogisticsOrder", orderId, null,
                "-item product=" + item.getProductId());
    }

    @Transactional
    public LogisticsExpense addExpense(Long companyId, Long userId, Long orderId, Map<String, Object> body) {
        LogisticsOrder order = requireDraft(companyId, orderId);
        LogisticsExpense exp = addExpenseInternal(companyId, order, body);
        recalculate(order);
        orderRepository.save(order);
        auditService.log(companyId, userId, "ADD_EXPENSE", "LogisticsOrder", orderId, null,
                "+expense " + exp.getExpenseType() + " " + exp.getAmount());
        return exp;
    }

    @Transactional
    public void removeExpense(Long companyId, Long userId, Long orderId, Long expenseId) {
        LogisticsOrder order = requireDraft(companyId, orderId);
        LogisticsExpense exp = expenseRepository.findById(expenseId)
                .filter(e -> e.getLogisticsOrderId().equals(orderId))
                .orElseThrow(() -> BusinessException.notFound("LogisticsExpense"));
        expenseRepository.delete(exp);
        recalculate(order);
        orderRepository.save(order);
        auditService.log(companyId, userId, "REMOVE_EXPENSE", "LogisticsOrder", orderId, null,
                "-expense " + exp.getExpenseType());
    }

    @Transactional
    public void cancelDraft(Long companyId, Long userId, Long orderId) {
        LogisticsOrder order = requireDraft(companyId, orderId);
        order.setStatus("cancelled");
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(userId);
        orderRepository.save(order);
        auditService.log(companyId, userId, "CANCEL", "LogisticsOrder", orderId, "draft", "cancelled");
    }

    /**
     * Settle (pay) an outstanding logistics expense.
     *
     * <p>Three supported scenarios — all balance the journal exactly:</p>
     * <ol>
     *   <li><b>Same currency, same rate</b> — clean clearance, no FX line.</li>
     *   <li><b>Same currency, different rate</b> — DR LogisticsPayable at booked rate,
     *       CR Cash/Bank at payment rate, FX_DIFFERENCE picks up the delta
     *       (DR on loss / CR on gain).</li>
     *   <li><b>Different currency (cross-currency, V20+)</b> — payment rate is X→base;
     *       both legs use the payment rate so the journal balances; the equivalent
     *       expense-currency outstanding is reduced via {@code payBase / exp.exchangeRate}.</li>
     * </ol>
     *
     * <p>Body shape (all amounts in the {@code currency} field's units):</p>
     * <pre>{@code
     *   { "amount": 60.00, "currency": "USD" (optional),
     *     "exchangeRate": 0.14 (optional; required if currency differs from order currency),
     *     "paymentSource": "cash"|"bank", "note": "..." }
     * }</pre>
     */
    @Transactional
    public uz.bizcontrol.entity.LogisticsExpensePayment payExpense(
            Long companyId, Long userId, Long orderId, Long expenseId, Map<String, Object> body) {

        LogisticsOrder order = getOne(companyId, orderId);
        if (!"confirmed".equals(order.getStatus()))
            throw new BusinessException("Only confirmed orders' expenses can be settled");

        LogisticsExpense exp = expenseRepository.findById(expenseId)
                .filter(e -> e.getLogisticsOrderId().equals(orderId))
                .orElseThrow(() -> BusinessException.notFound("LogisticsExpense"));
        if ("paid".equals(exp.getPaymentStatus()))
            throw new BusinessException("Expense " + expenseId + " is already fully paid");

        BigDecimal pay = bd(body.get("amount")).setScale(2, RoundingMode.HALF_UP);
        if (pay.signum() <= 0) throw new BusinessException("Payment amount must be positive");

        String source = strDefault(body.get("paymentSource"), "cash");
        if (!VALID_PAYMENT_SOURCES.contains(source))
            throw new BusinessException("Invalid paymentSource. Valid: " + VALID_PAYMENT_SOURCES);

        // Resolve payment currency + rate-to-base.
        String payCurrency = strDefault(body.get("currency"), exp.getCurrency()).toUpperCase();
        boolean crossCurrency = !payCurrency.equals(exp.getCurrency());
        BigDecimal paymentRate;
        if (payCurrency.equals(order.getCurrency())) {
            paymentRate = BigDecimal.ONE; // paying in the order currency
        } else if (!crossCurrency) {
            // Same currency as the expense → caller can override the rate (FX revaluation)
            paymentRate = bdOrDefault(body.get("exchangeRate"), exp.getExchangeRate());
        } else {
            // Cross-currency payment → exchangeRate is REQUIRED (no silent inference)
            paymentRate = bdOrDefault(body.get("exchangeRate"), null);
            if (paymentRate == null || paymentRate.signum() <= 0)
                throw new BusinessException("exchangeRate is required when payment currency ("
                        + payCurrency + ") differs from expense currency (" + exp.getCurrency() + ")");
        }

        // What this payment clears from the expense, expressed in the expense's own currency.
        BigDecimal payBase = pay.multiply(paymentRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal clearedInExpenseCurrency = crossCurrency
                ? payBase.divide(exp.getExchangeRate(), 2, RoundingMode.HALF_UP)
                : pay;
        BigDecimal outstanding = exp.getAmount().subtract(exp.getPaidAmount());
        if (clearedInExpenseCurrency.compareTo(outstanding) > 0)
            throw new BusinessException("Payment (≈ " + clearedInExpenseCurrency + " "
                    + exp.getCurrency() + ") exceeds outstanding " + outstanding + " " + exp.getCurrency());

        // Booked clearance in BASE currency: what the books say we're clearing.
        BigDecimal clearedInBase = clearedInExpenseCurrency.multiply(exp.getExchangeRate())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal fxDelta = payBase.subtract(clearedInBase).setScale(2, RoundingMode.HALF_UP);
        // For same-currency: fxDelta = pay × (paymentRate − originalRate)
        // For cross-currency: fxDelta = 0 (math cancels — both legs use paymentRate)

        // 1. Cash transaction in the payment currency
        Company company = companyService.getById(companyId);
        CashTransaction ct = CashTransaction.builder()
                .company(company)
                .transactionType("expense")
                .paymentSource(source)
                .amount(pay)
                .currency(payCurrency)
                .category("logistics_payable_payment")
                .transactionDate(LocalDateTime.now())
                .note("Logistics " + order.getOrderNumber() + " — payable " + exp.getExpenseType()
                        + (crossCurrency ? " (cross-currency " + payCurrency + "→" + exp.getCurrency() + ")" : ""))
                .status("active")
                .createdBy(userId)
                .build();
        ct = cashTransactionRepository.save(ct);
        if ("bank".equals(source)) company.setBankBalance(company.getBankBalance().subtract(pay));
        else                        company.setCashBalance(company.getCashBalance().subtract(pay));
        companyService.save(company);

        // 2. Balanced journal entry — DR Payable at booked rate, CR Cash at payment rate,
        //    FX line plugs the gap when same-currency rates differ.
        Account payable = chartOfAccountsService.require(companyId, ChartOfAccounts.LOGISTICS_PAYABLE);
        Account cashAcc = chartOfAccountsService.require(companyId,
                "bank".equals(source) ? ChartOfAccounts.BANK : ChartOfAccounts.CASH);
        String memo = "Logistics payable payment " + order.getOrderNumber();

        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalService.debit(payable.getId(), clearedInBase, memo + " payable"));
        lines.add(JournalService.credit(cashAcc.getId(), payBase, memo + " " + source));
        if (fxDelta.signum() != 0) {
            Account fx = chartOfAccountsService.require(companyId, ChartOfAccounts.FX_DIFFERENCE);
            if (fxDelta.signum() > 0) {
                // LOSS — paid more than booked
                lines.add(JournalService.debit(fx.getId(), fxDelta, memo + " FX loss"));
            } else {
                // GAIN — paid less than booked
                lines.add(JournalService.credit(fx.getId(), fxDelta.abs(), memo + " FX gain"));
            }
        }
        var entry = journalService.post(companyId, userId, LocalDateTime.now(),
                "LOGISTICS_PAYMENT", exp.getId(), memo, lines);

        // 3. Reduce the expense outstanding (in expense currency) + flip status
        BigDecimal newPaid = exp.getPaidAmount().add(clearedInExpenseCurrency);
        exp.setPaidAmount(newPaid);
        exp.setPaymentStatus(newPaid.compareTo(exp.getAmount()) >= 0 ? "paid" : "partial");
        expenseRepository.save(exp);

        // 4. Record the settlement
        uz.bizcontrol.entity.LogisticsExpensePayment pmt = uz.bizcontrol.entity.LogisticsExpensePayment.builder()
                .companyId(companyId)
                .logisticsOrderId(orderId)
                .logisticsExpenseId(expenseId)
                .amount(pay)
                .currency(payCurrency)
                .exchangeRate(paymentRate)
                .convertedAmount(payBase)
                .fxDelta(fxDelta)
                .paymentSource(source)
                .cashTransactionId(ct.getId())
                .journalEntryId(entry.getId())
                .note(str(body.get("note")))
                .paidBy(userId)
                .build();
        pmt = paymentRepository.save(pmt);

        auditService.log(companyId, userId, "PAY_PAYABLE", "LogisticsExpense", expenseId,
                exp.getPaymentStatus(),
                "paid " + pay + " " + payCurrency
                        + (fxDelta.signum() != 0 ? " (FX " + (fxDelta.signum() > 0 ? "loss" : "gain")
                                + " " + fxDelta.abs() + " " + order.getCurrency() + ")" : ""));
        log.info("[Logistics] expense #{} settled +{} {} (fxDelta={} {}, status={})",
                expenseId, pay, payCurrency, fxDelta, order.getCurrency(), exp.getPaymentStatus());
        return pmt;
    }

    /**
     * Reverse a confirmed logistics order. Original document stays in the DB
     * (immutable history); the status flips to {@code reversed} and explicit
     * reversal metadata is recorded. Symmetric undo of {@link #confirm}:
     * <ol>
     *   <li>Transfer stock back from destination → source per item (fails clearly
     *       if any unit has already been sold/transferred away from destination).</li>
     *   <li>Mark the cash transactions created at confirmation as {@code reversed}
     *       and credit back the company cash/bank balances.</li>
     *   <li>{@link JournalService#reverseBySource} posts the mirror journal entry.</li>
     *   <li>Audit log captures the reversal.</li>
     * </ol>
     * Idempotent: a second call throws — only one reversal per order.
     */
    @Transactional
    public LogisticsOrder reverse(Long companyId, Long userId, Long orderId, String reason) {
        LogisticsOrder order = getOne(companyId, orderId);
        if (!"confirmed".equals(order.getStatus()))
            throw new BusinessException("Only confirmed orders can be reversed (status: " + order.getStatus() + ")");
        if (order.getReversedAt() != null)
            throw new BusinessException("Order " + order.getOrderNumber() + " is already reversed");

        // 1. Reverse stock per item (destination → source). Insufficient stock at
        //    destination (e.g. sold downstream) blocks the reversal cleanly.
        List<LogisticsOrderItem> items = itemRepository.findByLogisticsOrderIdOrderByIdAsc(orderId);
        for (LogisticsOrderItem it : items) {
            try {
                warehouseStockService.transfer(companyId, userId,
                        it.getProductId(),
                        order.getDestinationWarehouseId(),   // FROM destination
                        order.getSourceWarehouseId(),         // BACK TO source
                        it.getQuantity(),
                        "Logistics REVERSAL " + order.getOrderNumber());
            } catch (BusinessException e) {
                throw new BusinessException("Cannot reverse logistics order — " + e.getMessage()
                        + " (product #" + it.getProductId() + "). "
                        + "Some units may have been sold or transferred away from destination.");
            }
        }

        // 2. Mark confirmation cash transactions as reversed and restore the
        //    balance that was deducted at confirm time (= paid portion only;
        //    unpaid portion never touched cash).
        Company company = companyService.getById(companyId);
        boolean balanceTouched = false;
        for (LogisticsExpense exp : expenseRepository.findByLogisticsOrderIdOrderByIdAsc(orderId)) {
            // Block reversal if any post-confirm settlements landed against this expense.
            // Operator should reverse those payments individually first.
            if (!paymentRepository.findByLogisticsExpenseIdOrderByPaidAtAsc(exp.getId()).isEmpty())
                throw new BusinessException("Cannot reverse order — expense " + exp.getId()
                        + " has post-confirm payments. Reverse those first.");

            if (exp.getCashTransactionId() == null) continue;
            cashTransactionRepository.findById(exp.getCashTransactionId()).ifPresent(ct -> {
                if (!"reversed".equals(ct.getStatus())) {
                    ct.setStatus("reversed");
                    cashTransactionRepository.save(ct);
                }
            });
            BigDecimal paidNative = exp.getPaidAmount() != null ? exp.getPaidAmount() : exp.getAmount();
            if ("bank".equals(exp.getPaymentSource())) {
                company.setBankBalance(company.getBankBalance().add(paidNative));
            } else {
                company.setCashBalance(company.getCashBalance().add(paidNative));
            }
            balanceTouched = true;
        }
        if (balanceTouched) companyService.save(company);

        // 3. Reverse the consolidated journal entry (idempotent on the journal side).
        journalService.reverseBySource(companyId, userId, "LOGISTICS", orderId,
                reason != null ? reason : "Logistics order reversal");

        // 4. Mark the order itself reversed.
        order.setStatus("reversed");
        order.setReversedAt(LocalDateTime.now());
        order.setReversedBy(userId);
        if (reason != null && !reason.isBlank()) order.setReversalReason(reason);
        order = orderRepository.save(order);

        auditService.log(companyId, userId, "REVERSE", "LogisticsOrder", orderId, "confirmed", "reversed");
        log.info("[Logistics] order #{} reversed by user {} (reason: {})", orderId, userId, reason);
        return order;
    }

    // ── Confirmation ───────────────────────────────────────────────────────────

    /**
     * Confirm a draft: transfer all items, post the consolidated expense journal,
     * record cash transactions, persist landed-cost allocations. Atomic — any
     * validation or stock-shortage error rolls everything back.
     */
    @Transactional
    public LogisticsOrder confirm(Long companyId, Long userId, Long orderId) {
        LogisticsOrder order = requireDraft(companyId, orderId);
        List<LogisticsOrderItem> items = itemRepository.findByLogisticsOrderIdOrderByIdAsc(orderId);
        if (items.isEmpty()) throw new BusinessException("Add at least one item before confirming");

        List<LogisticsExpense> expenses = expenseRepository.findByLogisticsOrderIdOrderByIdAsc(orderId);
        // Multi-currency is allowed but only with an explicit exchange rate (set
        // at addExpense time; we re-validate here to defend against direct DB tampering).
        for (LogisticsExpense e : expenses) {
            if (!order.getCurrency().equals(e.getCurrency())
                    && (e.getExchangeRate() == null || e.getExchangeRate().signum() <= 0))
                throw new BusinessException("Expense " + e.getId() + " in " + e.getCurrency()
                        + " has no exchange rate to order currency " + order.getCurrency());
        }

        // 1. Move stock: src → dst, per item. Real, audited, with low-stock alerts.
        Map<Long, BigDecimal> transferredByProduct = new LinkedHashMap<>();
        for (LogisticsOrderItem it : items) {
            warehouseStockService.transfer(companyId, userId,
                    it.getProductId(),
                    order.getSourceWarehouseId(),
                    order.getDestinationWarehouseId(),
                    it.getQuantity(),
                    "Logistics " + order.getOrderNumber());
            transferredByProduct.merge(it.getProductId(), it.getQuantity(), BigDecimal::add);
        }

        // 2. Compute & persist landed-cost allocations (proportional by item value).
        //    Allocations are always in the ORDER (base) currency → use convertedAmount.
        BigDecimal itemsValue = items.stream()
                .map(LogisticsOrderItem::getItemValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expensesTotal = expenses.stream()
                .map(LogisticsExpense::getConvertedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LandedCostAllocation> allocs = allocate(companyId, order, items, itemsValue, expensesTotal);
        allocationRepository.saveAll(allocs);

        // 3. Per-expense bookkeeping. Each expense splits into (paid, unpaid):
        //      • paid     → CashTransaction in the expense's native currency +
        //                   CR Cash/Bank in base currency in the journal.
        //      • unpaid   → no CashTransaction, CR LOGISTICS_PAYABLE in the journal.
        //    Same one consolidated journal entry per order. AP is a separate account
        //    (2300) from supplier debt (2000), so the two never mix.
        Company company = companyService.getById(companyId);
        Map<String, BigDecimal> sourceTotalsBase = new LinkedHashMap<>(); // cash/bank → base amount (CR)
        Map<String, BigDecimal> accountTotals    = new LinkedHashMap<>(); // expense code → base amount (DR)
        BigDecimal payableTotalBase = BigDecimal.ZERO;

        for (LogisticsExpense exp : expenses) {
            // Paid portion in the expense's own currency / converted to base
            BigDecimal paidNative = exp.getPaidAmount() != null ? exp.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal paidBase   = paidNative.multiply(exp.getExchangeRate()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal unpaidBase = exp.getConvertedAmount().subtract(paidBase);

            if (paidNative.signum() > 0) {
                String source = exp.getPaymentSource();
                CashTransaction ct = CashTransaction.builder()
                        .company(company)
                        .transactionType("expense")
                        .paymentSource(source)
                        .amount(paidNative)
                        .currency(exp.getCurrency())
                        .category("logistics_" + exp.getExpenseType())
                        .transactionDate(LocalDateTime.now())
                        .note("Logistics " + order.getOrderNumber()
                                + " — " + exp.getExpenseType()
                                + (exp.getNote() != null ? " (" + exp.getNote() + ")" : ""))
                        .status("active")
                        .createdBy(userId)
                        .build();
                ct = cashTransactionRepository.save(ct);
                exp.setCashTransactionId(ct.getId());

                // Legacy single-currency company balance roll-up (same pattern as CashboxController)
                if ("bank".equals(source)) company.setBankBalance(company.getBankBalance().subtract(paidNative));
                else                       company.setCashBalance(company.getCashBalance().subtract(paidNative));

                sourceTotalsBase.merge(source, paidBase, BigDecimal::add);
            }
            expenseRepository.save(exp); // persist cashTransactionId (or no-op)

            // Full expense (paid + unpaid) hits the expense account in base
            accountTotals.merge(expenseAccountCode(exp.getExpenseType()),
                    exp.getConvertedAmount(), BigDecimal::add);
            if (unpaidBase.signum() > 0) payableTotalBase = payableTotalBase.add(unpaidBase);
        }
        if (!expenses.isEmpty()) companyService.save(company);

        if (!expenses.isEmpty()) {
            String memo = "Logistics " + order.getOrderNumber();
            List<JournalLine> lines = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> e : accountTotals.entrySet()) {
                Account acc = chartOfAccountsService.require(companyId, e.getKey());
                lines.add(JournalService.debit(acc.getId(), e.getValue(), memo + " expense"));
            }
            for (Map.Entry<String, BigDecimal> e : sourceTotalsBase.entrySet()) {
                String code = "bank".equals(e.getKey()) ? ChartOfAccounts.BANK : ChartOfAccounts.CASH;
                Account acc = chartOfAccountsService.require(companyId, code);
                lines.add(JournalService.credit(acc.getId(), e.getValue(), memo + " " + e.getKey()));
            }
            if (payableTotalBase.signum() > 0) {
                Account payable = chartOfAccountsService.require(companyId, ChartOfAccounts.LOGISTICS_PAYABLE);
                lines.add(JournalService.credit(payable.getId(), payableTotalBase, memo + " unpaid → payable"));
            }
            // Only post if there's at least one debit + credit (a 0-expense order would have 0 lines)
            if (lines.size() >= 2) {
                journalService.post(companyId, userId, LocalDateTime.now(),
                        "LOGISTICS", order.getId(), memo, lines);
            }
        }

        // 5. Finalize order
        order.setItemsValue(itemsValue);
        order.setExpensesTotal(expensesTotal);
        order.setLandedTotal(itemsValue.add(expensesTotal));
        order.setStatus("confirmed");
        order.setConfirmedAt(LocalDateTime.now());
        order.setConfirmedBy(userId);
        order = orderRepository.save(order);

        auditService.log(companyId, userId, "CONFIRM", "LogisticsOrder", orderId, "draft", "confirmed");
        log.info("[Logistics] order #{} confirmed: itemsValue={} expensesTotal={} landedTotal={}",
                orderId, itemsValue, expensesTotal, order.getLandedTotal());
        return order;
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private LogisticsOrderItem addItemInternal(Long companyId, LogisticsOrder order, Map<String, Object> body) {
        if (!"draft".equals(order.getStatus()))
            throw new BusinessException("Only draft orders can be edited");
        Long productId = reqLong(body, "productId");
        BigDecimal qty = bd(body.get("quantity"));
        if (qty.signum() <= 0) throw new BusinessException("Item quantity must be positive");

        Product product = productRepository.findByCompanyIdAndId(companyId, productId)
                .orElseThrow(() -> BusinessException.notFound("Product"));

        BigDecimal unitCost = bdOrDefault(body.get("unitCost"),
                product.getPurchasePrice() != null ? product.getPurchasePrice() : BigDecimal.ZERO);
        if (unitCost.signum() < 0) throw new BusinessException("Unit cost cannot be negative");

        LogisticsOrderItem it = LogisticsOrderItem.builder()
                .companyId(companyId)
                .logisticsOrderId(order.getId())
                .productId(productId)
                .quantity(qty.setScale(4, RoundingMode.HALF_UP))
                .unitCost(unitCost.setScale(2, RoundingMode.HALF_UP))
                .itemValue(qty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP))
                .build();
        return itemRepository.save(it);
    }

    private LogisticsExpense addExpenseInternal(Long companyId, LogisticsOrder order, Map<String, Object> body) {
        if (!"draft".equals(order.getStatus()))
            throw new BusinessException("Only draft orders can be edited");
        String type = str(body.get("expenseType"));
        if (type == null || !VALID_EXPENSE_TYPES.contains(type))
            throw new BusinessException("Invalid expenseType. Valid: " + VALID_EXPENSE_TYPES);
        BigDecimal amt = bd(body.get("amount"));
        if (amt.signum() <= 0) throw new BusinessException("Expense amount must be positive");

        // Multi-currency: if not the order currency, exchangeRate is mandatory (no silent conversion).
        String currency = strDefault(body.get("currency"), order.getCurrency()).toUpperCase();
        BigDecimal rate;
        if (order.getCurrency().equals(currency)) {
            rate = BigDecimal.ONE;
        } else {
            rate = bdOrDefault(body.get("exchangeRate"), null);
            if (rate == null || rate.signum() <= 0)
                throw new BusinessException("exchangeRate is required when expense currency ("
                        + currency + ") differs from order currency (" + order.getCurrency() + ")");
        }
        BigDecimal amount       = amt.setScale(2, RoundingMode.HALF_UP);
        BigDecimal convertedAmt = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        String src = strDefault(body.get("paymentSource"), "cash");
        if (!VALID_PAYMENT_SOURCES.contains(src))
            throw new BusinessException("Invalid paymentSource. Valid: " + VALID_PAYMENT_SOURCES);

        // paidAmount semantics (in expense's own currency):
        //   - missing      → full (= amount) → status 'paid'
        //   - 0            → 'unpaid'
        //   - 0 < x < amt  → 'partial'
        //   - == amt       → 'paid'
        BigDecimal paid = bdOrDefault(body.get("paidAmount"), amount).setScale(2, RoundingMode.HALF_UP);
        if (paid.signum() < 0) throw new BusinessException("paidAmount cannot be negative");
        if (paid.compareTo(amount) > 0) throw new BusinessException("paidAmount cannot exceed amount");
        String status = paid.signum() == 0 ? "unpaid"
                       : (paid.compareTo(amount) == 0 ? "paid" : "partial");

        LogisticsExpense e = LogisticsExpense.builder()
                .companyId(companyId)
                .logisticsOrderId(order.getId())
                .expenseType(type)
                .amount(amount)
                .currency(currency)
                .exchangeRate(rate)
                .convertedAmount(convertedAmt)
                .paymentSource(src)
                .paidAmount(paid)
                .paymentStatus(status)
                .note(str(body.get("note")))
                .build();
        return expenseRepository.save(e);
    }

    private void recalculate(LogisticsOrder order) {
        List<LogisticsOrderItem> items = itemRepository.findByLogisticsOrderIdOrderByIdAsc(order.getId());
        BigDecimal iv = items.stream().map(LogisticsOrderItem::getItemValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        // Expenses total is reported in the order's base currency, so always use
        // the converted amount (preserves landed-cost math under multi-currency).
        BigDecimal ex = expenseRepository.findByLogisticsOrderIdOrderByIdAsc(order.getId()).stream()
                .map(LogisticsExpense::getConvertedAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        order.setItemsValue(iv);
        order.setExpensesTotal(ex);
        order.setLandedTotal(iv.add(ex));
    }

    /**
     * Proportional allocation with largest-remainder rounding so the allocated
     * cents sum exactly to {@code expensesTotal} (no off-by-one cent across items).
     */
    private List<LandedCostAllocation> allocate(Long companyId, LogisticsOrder order,
                                                List<LogisticsOrderItem> items,
                                                BigDecimal itemsValue, BigDecimal expensesTotal) {
        List<LandedCostAllocation> result = new ArrayList<>(items.size());
        if (items.isEmpty() || expensesTotal.signum() == 0 || itemsValue.signum() == 0) {
            for (LogisticsOrderItem it : items) {
                BigDecimal unit = it.getUnitCost();
                result.add(LandedCostAllocation.builder()
                        .companyId(companyId)
                        .logisticsOrderId(order.getId())
                        .logisticsOrderItemId(it.getId())
                        .productId(it.getProductId())
                        .quantity(it.getQuantity())
                        .itemValue(it.getItemValue())
                        .allocatedAmount(BigDecimal.ZERO.setScale(2))
                        .unitLandedCost(unit.setScale(4, RoundingMode.HALF_UP))
                        .build());
            }
            return result;
        }

        // First pass: exact allocations with 2dp, track remainder.
        BigDecimal[] raw = new BigDecimal[items.size()];
        BigDecimal[] rounded = new BigDecimal[items.size()];
        BigDecimal allocatedSoFar = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            raw[i] = items.get(i).getItemValue()
                    .multiply(expensesTotal)
                    .divide(itemsValue, 10, RoundingMode.HALF_UP);
            rounded[i] = raw[i].setScale(2, RoundingMode.HALF_UP);
            allocatedSoFar = allocatedSoFar.add(rounded[i]);
        }
        // Cent-true correction: nudge the items with the largest fractional remainders.
        BigDecimal diff = expensesTotal.subtract(allocatedSoFar).setScale(2, RoundingMode.HALF_UP);
        if (diff.signum() != 0) {
            Integer[] order2 = new Integer[items.size()];
            for (int i = 0; i < order2.length; i++) order2[i] = i;
            Arrays.sort(order2, (a, b) ->
                    raw[b].subtract(rounded[b]).abs().compareTo(raw[a].subtract(rounded[a]).abs()));
            BigDecimal step = diff.signum() > 0 ? new BigDecimal("0.01") : new BigDecimal("-0.01");
            int idx = 0;
            BigDecimal absDiff = diff.abs();
            while (absDiff.signum() > 0) {
                rounded[order2[idx % order2.length]] = rounded[order2[idx % order2.length]].add(step);
                absDiff = absDiff.subtract(new BigDecimal("0.01"));
                idx++;
            }
        }

        for (int i = 0; i < items.size(); i++) {
            LogisticsOrderItem it = items.get(i);
            BigDecimal allocated = rounded[i];
            BigDecimal unit = it.getItemValue().add(allocated)
                    .divide(it.getQuantity(), 4, RoundingMode.HALF_UP);
            result.add(LandedCostAllocation.builder()
                    .companyId(companyId)
                    .logisticsOrderId(order.getId())
                    .logisticsOrderItemId(it.getId())
                    .productId(it.getProductId())
                    .quantity(it.getQuantity())
                    .itemValue(it.getItemValue())
                    .allocatedAmount(allocated)
                    .unitLandedCost(unit)
                    .build());
        }
        return result;
    }

    /** Map an expense type to its accounting expense account. */
    private static String expenseAccountCode(String type) {
        return switch (type) {
            case "customs"   -> ChartOfAccounts.CUSTOMS_EXPENSES;
            case "shipping",
                 "broker",
                 "insurance" -> ChartOfAccounts.LOGISTICS_EXPENSES;
            default          -> ChartOfAccounts.OPERATING_EXPENSES;
        };
    }

    private LogisticsOrder requireDraft(Long companyId, Long orderId) {
        LogisticsOrder o = getOne(companyId, orderId);
        if (!"draft".equals(o.getStatus()))
            throw new BusinessException("Only draft orders can be modified (status: " + o.getStatus() + ")");
        return o;
    }

    private void validateWarehouse(Long companyId, Long warehouseId, String label) {
        Warehouse w = warehouseRepository.findByCompanyIdAndId(companyId, warehouseId)
                .orElseThrow(() -> BusinessException.notFound(label + " warehouse"));
        if ("archived".equals(w.getStatus()))
            throw new BusinessException(label + " warehouse '" + w.getName() + "' is archived");
    }

    private String generateOrderNumber(Long companyId) {
        long n = orderRepository.countByCompanyId(companyId) + 1;
        return String.format("LOG-%05d-%d", n, System.currentTimeMillis() % 100000);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static String str(Object o) { return o != null ? o.toString() : null; }
    private static String strDefault(Object o, String d) {
        String s = str(o); return (s == null || s.isBlank()) ? d : s;
    }
    private static Long reqLong(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) throw new BusinessException(key + " is required");
        return Long.parseLong(v.toString());
    }
    private static Long optLong(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return (s.isEmpty() || "null".equalsIgnoreCase(s)) ? null : Long.parseLong(s);
    }
    private static BigDecimal bd(Object o) {
        if (o == null) throw new BusinessException("amount/quantity is required");
        return new BigDecimal(o.toString());
    }
    private static BigDecimal bdOrDefault(Object o, BigDecimal d) {
        if (o == null || o.toString().isBlank()) return d;
        return new BigDecimal(o.toString());
    }
}
