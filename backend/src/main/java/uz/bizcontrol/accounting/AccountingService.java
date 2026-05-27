package uz.bizcontrol.accounting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.Purchase;
import uz.bizcontrol.entity.Sale;
import uz.bizcontrol.entity.SaleItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges business documents to the double-entry ledger. Each method builds a
 * single balanced journal entry and posts it through {@link JournalService}.
 *
 * <p>Sale revenue recognition:
 * <pre>
 *   Dr Cash/Bank   (amount paid now)
 *   Dr A/R         (amount on credit)
 *       Cr Sales Revenue   (total)
 *   Dr COGS        (cost of goods)
 *       Cr Inventory       (cost of goods)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingService {

    private final ChartOfAccountsService chart;
    private final JournalService journal;

    private static boolean isBank(String method) {
        return method != null && (method.equals("bank") || method.equals("bank_transfer"));
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /**
     * Record a posted sale into the ledger. Additive and self-contained: a no-op
     * for a zero-value sale. Throws only on a genuine accounting integrity error.
     */
    @Transactional
    public JournalEntry recordSale(Long companyId, Long userId, Sale sale,
                                   List<SaleItem> items, BigDecimal total, BigDecimal paidAmount) {
        total = nz(total);
        if (total.signum() <= 0) return null;

        chart.ensureSeeded(companyId);
        BigDecimal paid = nz(paidAmount).min(total);
        BigDecimal unpaid = total.subtract(paid).max(BigDecimal.ZERO);

        BigDecimal cogs = BigDecimal.ZERO;
        if (items != null) {
            for (SaleItem it : items) {
                cogs = cogs.add(nz(it.getPurchaseCost()).multiply(nz(it.getQuantity())));
            }
        }

        Long cashOrBank = isBank(sale.getPaymentMethod())
                ? chart.require(companyId, ChartOfAccounts.BANK).getId()
                : chart.require(companyId, ChartOfAccounts.CASH).getId();
        Long ar       = chart.require(companyId, ChartOfAccounts.ACCOUNTS_RECEIVABLE).getId();
        Long revenue  = chart.require(companyId, ChartOfAccounts.SALES_REVENUE).getId();
        Long cogsAcc  = chart.require(companyId, ChartOfAccounts.COGS).getId();
        Long inventory = chart.require(companyId, ChartOfAccounts.INVENTORY).getId();

        String memo = "Sale " + sale.getSaleNumber();
        List<JournalLine> lines = new ArrayList<>();
        if (paid.signum() > 0)   lines.add(JournalService.debit(cashOrBank, paid, memo));
        if (unpaid.signum() > 0) lines.add(JournalService.debit(ar, unpaid, memo + " (on credit)"));
        lines.add(JournalService.credit(revenue, total, memo));
        if (cogs.signum() > 0) {
            lines.add(JournalService.debit(cogsAcc, cogs, "COGS " + sale.getSaleNumber()));
            lines.add(JournalService.credit(inventory, cogs, "Inventory out " + sale.getSaleNumber()));
        }

        return journal.post(companyId, userId, sale.getSaleDate(), "SALE", sale.getId(), memo, lines);
    }

    /**
     * Reverse the ledger impact of a sale when it is cancelled or edited, keeping
     * the ledger consistent with operational state (no double-counted revenue).
     */
    @Transactional
    public void reverseSale(Long companyId, Long userId, Long saleId, String reason) {
        journal.reverseBySource(companyId, userId, "SALE", saleId, reason);
    }

    /**
     * Record a posted purchase (goods received) into the ledger.
     * <pre>
     *   Dr Inventory   (landed cost = purchase total)
     *       Cr Cash/Bank   (amount paid now)
     *       Cr Accounts Payable  (amount on credit)
     * </pre>
     */
    @Transactional
    public JournalEntry recordPurchase(Long companyId, Long userId, Purchase purchase,
                                       BigDecimal total, BigDecimal paidAmount) {
        total = nz(total);
        if (total.signum() <= 0) return null;

        chart.ensureSeeded(companyId);
        BigDecimal paid = nz(paidAmount).min(total);
        BigDecimal unpaid = total.subtract(paid).max(BigDecimal.ZERO);

        Long inventory  = chart.require(companyId, ChartOfAccounts.INVENTORY).getId();
        Long cashOrBank = isBank(purchase.getPaymentMethod())
                ? chart.require(companyId, ChartOfAccounts.BANK).getId()
                : chart.require(companyId, ChartOfAccounts.CASH).getId();
        Long ap = chart.require(companyId, ChartOfAccounts.ACCOUNTS_PAYABLE).getId();

        String memo = "Purchase " + purchase.getPurchaseNumber();
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalService.debit(inventory, total, memo));
        if (paid.signum() > 0)   lines.add(JournalService.credit(cashOrBank, paid, memo));
        if (unpaid.signum() > 0) lines.add(JournalService.credit(ap, unpaid, memo + " (on credit)"));

        return journal.post(companyId, userId, purchase.getPurchaseDate(), "PURCHASE", purchase.getId(), memo, lines);
    }

    /** Reverse a purchase's ledger impact on cancel/edit. */
    @Transactional
    public void reversePurchase(Long companyId, Long userId, Long purchaseId, String reason) {
        journal.reverseBySource(companyId, userId, "PURCHASE", purchaseId, reason);
    }
}
