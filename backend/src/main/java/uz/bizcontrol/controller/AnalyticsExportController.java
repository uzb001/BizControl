package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.accounting.*;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.repository.*;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Additional professional Excel exports (categories, stock movements/transfers,
 * audit, users, roles, daily close, accounting statements, smart reports).
 * Additive — shares the {@code /export} base path with {@link ExportController}
 * and reuses {@link ExcelExportService#genericReport}. Every export is permission
 * gated and writes an access-log entry.
 */
@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class AnalyticsExportController {

    private final ExcelExportService excel;
    private final CompanyService companyService;
    private final PermissionService perm;
    private final AccessLogService accessLog;

    private final CategoryRepository categoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockTransferRepository stockTransferRepository;
    private final WarehouseRepository warehouseRepository;
    private final AuditLogRepository auditLogRepository;
    private final CompanyUserRepository companyUserRepository;
    private final DailyCloseRepository dailyCloseRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final RoleService roleService;
    private final JournalEntryRepository entryRepository;
    private final AccountRepository accountRepository;
    private final JournalService journalService;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── Categories ───────────────────────────────────────────────────────────────
    @GetMapping("/categories")
    public ResponseEntity<byte[]> categories(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "products.export");
        Long cid = p.getCompanyId();
        List<Object[]> rows = new ArrayList<>();
        for (Category c : categoryRepository.findByCompanyId(cid)) {
            rows.add(new Object[]{ c.getName(), c.getParent() != null ? c.getParent().getName() : "—",
                    nvl(c.getDescription()), c.getStatus(), fmt(c.getCreatedAt()) });
        }
        return out(p, "categories", "Categories", "All categories",
                new String[]{"Name", "Parent", "Description", "Status", "Created"}, rows, null, NONE, "products.export");
    }

    // ── Stock movements ──────────────────────────────────────────────────────────
    @GetMapping("/stock-movements")
    public ResponseEntity<byte[]> stockMovements(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "stock.export");
        Long cid = p.getCompanyId();
        List<Object[]> rows = new ArrayList<>();
        for (StockMovement m : stockMovementRepository.findByCompanyId(cid,
                PageRequest.of(0, 10000, Sort.by("createdAt").descending())).getContent()) {
            rows.add(new Object[]{ fmt(m.getCreatedAt()), m.getProduct() != null ? m.getProduct().getName() : "—",
                    whName(cid, m.getWarehouseId()), m.getMovementType(), m.getQuantity(),
                    m.getPreviousStock(), m.getNewStock(), nvl(m.getNote()) });
        }
        return out(p, "stock_movements", "Stock Movements", "Latest 10000",
                new String[]{"Date", "Product", "Warehouse", "Type", "Qty", "Before", "After", "Note"},
                rows, null, NONE, "stock.export");
    }

    // ── Stock transfers ──────────────────────────────────────────────────────────
    @GetMapping("/stock-transfers")
    public ResponseEntity<byte[]> stockTransfers(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "warehouse_stock.export");
        Long cid = p.getCompanyId();
        List<Object[]> rows = new ArrayList<>();
        for (StockTransfer tr : stockTransferRepository.findByCompanyIdOrderByCreatedAtDesc(cid)) {
            rows.add(new Object[]{ fmt(tr.getCreatedAt()), prodName(cid, tr.getProductId()),
                    whName(cid, tr.getFromWarehouseId()), whName(cid, tr.getToWarehouseId()),
                    tr.getQuantity(), tr.getStatus(), nvl(tr.getNote()) });
        }
        return out(p, "stock_transfers", "Stock Transfers", "All transfers",
                new String[]{"Date", "Product", "From", "To", "Qty", "Status", "Note"}, rows, null, NONE, "warehouse_stock.export");
    }

    // ── Audit logs ───────────────────────────────────────────────────────────────
    @GetMapping("/audit-logs")
    public ResponseEntity<byte[]> auditLogs(@AuthenticationPrincipal BizControlPrincipal p,
                                            @RequestParam(required = false) String actionType,
                                            @RequestParam(required = false) String entityType,
                                            @RequestParam(required = false) Long userId) throws Exception {
        perm.require(p, "audit.export");
        Long cid = p.getCompanyId();
        org.springframework.data.jpa.domain.Specification<AuditLog> spec = (root, q, cb) -> {
            var preds = new ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("companyId"), cid));
            if (actionType != null && !actionType.isBlank()) preds.add(cb.equal(root.get("actionType"), actionType));
            if (entityType != null && !entityType.isBlank()) preds.add(cb.equal(root.get("entityType"), entityType));
            if (userId != null) preds.add(cb.equal(root.get("userId"), userId));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<Object[]> rows = new ArrayList<>();
        for (AuditLog a : auditLogRepository.findAll(spec, Sort.by("createdAt").descending())) {
            rows.add(new Object[]{ fmt(a.getCreatedAt()), a.getUserId() != null ? "#" + a.getUserId() : "—",
                    a.getActionType(), a.getEntityType(), a.getEntityId(), nvl(a.getNote()), nvl(a.getIpAddress()) });
        }
        String filter = "Filters: " + (actionType != null ? actionType + " " : "") + (entityType != null ? entityType + " " : "") + (userId != null ? "user#" + userId : "");
        return out(p, "audit_logs", "Audit Logs", filter,
                new String[]{"Date", "User", "Action", "Entity", "Entity ID", "Note", "IP"}, rows, null, NONE, "audit.export");
    }

    // ── Users ────────────────────────────────────────────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<byte[]> users(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "users.export");
        Long cid = p.getCompanyId();
        List<Object[]> rows = new ArrayList<>();
        for (CompanyUser cu : companyUserRepository.findByCompanyId(cid)) {
            User u = cu.getUser();
            rows.add(new Object[]{ u != null ? u.getFullName() : "—", u != null ? nvl(u.getEmail()) : "",
                    u != null ? nvl(u.getPhone()) : "", cu.effectiveRoleCode(), cu.getStatus(), fmt(cu.getJoinedAt()) });
        }
        return out(p, "users", "Users", "Company members",
                new String[]{"Name", "Email", "Phone", "Role", "Status", "Joined"}, rows, null, NONE, "users.export");
    }

    // ── Roles ────────────────────────────────────────────────────────────────────
    @GetMapping("/roles")
    public ResponseEntity<byte[]> roles(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "users.export");
        Long cid = p.getCompanyId();
        List<Object[]> rows = new ArrayList<>();
        for (Role r : roleService.listForCompany(cid)) {
            rows.add(new Object[]{ r.getName(), r.getCode(), nvl(r.getDescription()),
                    r.isSystem() ? "Yes" : "No", r.getPermissions() != null ? r.getPermissions().size() : 0 });
        }
        return out(p, "roles", "Roles", "Company roles",
                new String[]{"Name", "Code", "Description", "System", "Permissions"}, rows, null, NONE, "users.export");
    }

    // ── Daily close ──────────────────────────────────────────────────────────────
    @GetMapping("/daily-close")
    public ResponseEntity<byte[]> dailyClose(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "reports.export");
        Long cid = p.getCompanyId();
        List<Object[]> rows = new ArrayList<>();
        for (DailyClose d : dailyCloseRepository.findByCompanyIdOrderByCloseDateDesc(cid, PageRequest.of(0, 5000)).getContent()) {
            rows.add(new Object[]{ d.getCloseDate() != null ? d.getCloseDate().toString() : "",
                    d.getExpectedCash(), d.getActualCash(), d.getCashDifference(),
                    d.getTotalSales(), d.getTotalExpenses(), d.getTotalProfit(), d.getStatus() });
        }
        return out(p, "daily_close", "Daily Close", "All closes",
                new String[]{"Date", "Expected Cash", "Actual Cash", "Difference", "Sales", "Expenses", "Profit", "Status"},
                rows, null, new int[]{1, 2, 3, 4, 5, 6}, "reports.export");
    }

    // ── Accounting: Journal ──────────────────────────────────────────────────────
    @GetMapping("/accounting/journal")
    public ResponseEntity<byte[]> journal(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "accounting.export");
        Long cid = p.getCompanyId();
        Map<Long, Account> accts = accountMap(cid);
        List<Object[]> rows = new ArrayList<>();
        BigDecimal td = BigDecimal.ZERO, tc = BigDecimal.ZERO;
        for (JournalEntry e : entryRepository.findByCompanyIdOrderByEntryDateDescIdDesc(cid, PageRequest.of(0, 10000)).getContent()) {
            for (JournalLine l : e.getLines()) {
                Account a = accts.get(l.getAccountId());
                rows.add(new Object[]{ "#" + e.getId(), fmt(e.getEntryDate()), nvl(e.getSourceType()),
                        a != null ? a.getCode() + " " + a.getName() : "acct " + l.getAccountId(),
                        l.getDebit(), l.getCredit(), nvl(l.getMemo()), e.getStatus() });
                td = td.add(l.getDebit()); tc = tc.add(l.getCredit());
            }
        }
        Object[] totals = { "TOTAL", null, null, null, td, tc, null, null };
        return out(p, "accounting_journal", "Accounting Journal", "All posted entries",
                new String[]{"Entry", "Date", "Source", "Account", "Debit", "Credit", "Memo", "Status"},
                rows, totals, new int[]{4, 5}, "accounting.export");
    }

    // ── Accounting: General Ledger ───────────────────────────────────────────────
    @GetMapping("/accounting/ledger")
    public ResponseEntity<byte[]> ledger(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "accounting.export");
        Long cid = p.getCompanyId();
        Map<Long, Account> accts = accountMap(cid);
        record Line(String accKey, LocalDateTime date, Long entry, BigDecimal debit, BigDecimal credit, String memo) {}
        List<Line> all = new ArrayList<>();
        for (JournalEntry e : entryRepository.findByCompanyIdOrderByEntryDateDescIdDesc(cid, PageRequest.of(0, 10000)).getContent()) {
            for (JournalLine l : e.getLines()) {
                Account a = accts.get(l.getAccountId());
                String key = a != null ? a.getCode() + " " + a.getName() : "acct " + l.getAccountId();
                all.add(new Line(key, e.getEntryDate(), e.getId(), l.getDebit(), l.getCredit(), nvl(l.getMemo())));
            }
        }
        all.sort(Comparator.comparing(Line::accKey).thenComparing(x -> x.date() == null ? LocalDateTime.MIN : x.date()).thenComparing(Line::entry));
        Map<String, BigDecimal> running = new HashMap<>();
        List<Object[]> rows = new ArrayList<>();
        for (Line ln : all) {
            BigDecimal bal = running.getOrDefault(ln.accKey(), BigDecimal.ZERO).add(ln.debit()).subtract(ln.credit());
            running.put(ln.accKey(), bal);
            rows.add(new Object[]{ ln.accKey(), fmt(ln.date()), "#" + ln.entry(), ln.debit(), ln.credit(), bal, ln.memo() });
        }
        return out(p, "accounting_ledger", "General Ledger", "All accounts",
                new String[]{"Account", "Date", "Entry", "Debit", "Credit", "Balance", "Memo"},
                rows, null, new int[]{3, 4, 5}, "accounting.export");
    }

    // ── Accounting: Trial Balance ────────────────────────────────────────────────
    @GetMapping("/accounting/trial-balance")
    public ResponseEntity<byte[]> trialBalance(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "accounting.export");
        Long cid = p.getCompanyId();
        var tb = journalService.trialBalance(cid);
        List<Object[]> rows = new ArrayList<>();
        BigDecimal td = BigDecimal.ZERO, tc = BigDecimal.ZERO;
        for (var r : tb) {
            rows.add(new Object[]{ r.code(), r.name(), r.type(), r.debit(), r.credit(), r.balance() });
            td = td.add(r.debit()); tc = tc.add(r.credit());
        }
        Object[] totals = { "TOTAL", null, null, td, tc, null };
        return out(p, "trial_balance", "Trial Balance", "All accounts",
                new String[]{"Code", "Account", "Type", "Debit", "Credit", "Balance"},
                rows, totals, new int[]{3, 4, 5}, "accounting.export");
    }

    // ── Accounting: Profit & Loss ────────────────────────────────────────────────
    @GetMapping("/accounting/profit-loss")
    public ResponseEntity<byte[]> profitLoss(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "accounting.export");
        Long cid = p.getCompanyId();
        var tb = journalService.trialBalance(cid);
        BigDecimal revenue = sumType(tb, "REVENUE"), expenses = sumType(tb, "EXPENSE");
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{ "", "— REVENUE —", "", null });
        for (var r : tb) if (r.type().equals("REVENUE")) rows.add(new Object[]{ r.code(), r.name(), r.type(), r.balance() });
        rows.add(new Object[]{ "", "TOTAL REVENUE", "", revenue });
        rows.add(new Object[]{ "", "— EXPENSES —", "", null });
        for (var r : tb) if (r.type().equals("EXPENSE")) rows.add(new Object[]{ r.code(), r.name(), r.type(), r.balance() });
        rows.add(new Object[]{ "", "TOTAL EXPENSES", "", expenses });
        Object[] totals = { "", "NET PROFIT", "", revenue.subtract(expenses) };
        return out(p, "profit_loss", "Profit & Loss", "All-time",
                new String[]{"Code", "Account", "Type", "Amount"}, rows, totals, new int[]{3}, "accounting.export");
    }

    // ── Accounting: Balance Sheet ────────────────────────────────────────────────
    @GetMapping("/accounting/balance-sheet")
    public ResponseEntity<byte[]> balanceSheet(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "accounting.export");
        Long cid = p.getCompanyId();
        var tb = journalService.trialBalance(cid);
        BigDecimal assets = sumType(tb, "ASSET"), liab = sumType(tb, "LIABILITY"), eq = sumType(tb, "EQUITY");
        BigDecimal net = sumType(tb, "REVENUE").subtract(sumType(tb, "EXPENSE"));
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{ "", "— ASSETS —", "", null });
        for (var r : tb) if (r.type().equals("ASSET")) rows.add(new Object[]{ r.code(), r.name(), r.type(), r.balance() });
        rows.add(new Object[]{ "", "TOTAL ASSETS", "", assets });
        rows.add(new Object[]{ "", "— LIABILITIES —", "", null });
        for (var r : tb) if (r.type().equals("LIABILITY")) rows.add(new Object[]{ r.code(), r.name(), r.type(), r.balance() });
        rows.add(new Object[]{ "", "TOTAL LIABILITIES", "", liab });
        rows.add(new Object[]{ "", "— EQUITY —", "", null });
        for (var r : tb) if (r.type().equals("EQUITY")) rows.add(new Object[]{ r.code(), r.name(), r.type(), r.balance() });
        rows.add(new Object[]{ "", "Retained Earnings", "", net });
        Object[] totals = { "", "LIAB + EQUITY + RETAINED", "", liab.add(eq).add(net) };
        return out(p, "balance_sheet", "Balance Sheet", "Assets = " + assets,
                new String[]{"Code", "Account", "Type", "Amount"}, rows, totals, new int[]{3}, "accounting.export");
    }

    // ── Accounting: Cashflow ─────────────────────────────────────────────────────
    @GetMapping("/accounting/cashflow")
    public ResponseEntity<byte[]> cashflow(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "accounting.export");
        Long cid = p.getCompanyId();
        var txns = cashTransactionRepository.findAll(cashSpec(cid, null));
        Map<String, BigDecimal> in = new LinkedHashMap<>(), out = new LinkedHashMap<>();
        for (CashTransaction t : txns) {
            String cat = t.getCategory() != null ? t.getCategory() : "other";
            if ("income".equals(t.getTransactionType())) in.merge(cat, t.getAmount(), BigDecimal::add);
            else out.merge(cat, t.getAmount(), BigDecimal::add);
        }
        BigDecimal inflow = in.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outflow = out.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{ "INFLOW", "— Cash Inflows —", null });
        in.forEach((k, v) -> rows.add(new Object[]{ "INFLOW", k, v }));
        rows.add(new Object[]{ "", "Total Inflows", inflow });
        rows.add(new Object[]{ "OUTFLOW", "— Cash Outflows —", null });
        out.forEach((k, v) -> rows.add(new Object[]{ "OUTFLOW", k, v }));
        rows.add(new Object[]{ "", "Total Outflows", outflow });
        Object[] totals = { "", "NET CASH FLOW", inflow.subtract(outflow) };
        return out(p, "cashflow", "Cash Flow", "All-time",
                new String[]{"Type", "Category", "Amount"}, rows, totals, new int[]{2}, "accounting.export");
    }

    // ── Reports: Dead Stock ──────────────────────────────────────────────────────
    @GetMapping("/reports/dead-stock")
    public ResponseEntity<byte[]> deadStock(@AuthenticationPrincipal BizControlPrincipal p,
                                            @RequestParam(defaultValue = "60") int days) throws Exception {
        perm.require(p, "reports.export");
        Long cid = p.getCompanyId();
        boolean canCost = perm.hasPermission(p, "products.view_purchase_price");
        List<Object[]> rows = new ArrayList<>();
        BigDecimal totalVal = BigDecimal.ZERO;
        for (Product pr : productRepository.findDeadStockSince(cid, LocalDateTime.now().minusDays(days))) {
            BigDecimal val = canCost && pr.getPurchasePrice() != null ? pr.getCurrentStock().multiply(pr.getPurchasePrice()) : null;
            rows.add(new Object[]{ pr.getName(), nvl(pr.getSku()), pr.getCategory() != null ? pr.getCategory().getName() : "—",
                    pr.getCurrentStock(), pr.getUnit(), val });
            if (val != null) totalVal = totalVal.add(val);
        }
        Object[] totals = { "TOTAL", null, null, null, null, canCost ? totalVal : null };
        return out(p, "dead_stock", "Dead Stock (" + days + "d+)", days + " days without a sale",
                new String[]{"Product", "SKU", "Category", "Stock", "Unit", "Value"},
                rows, totals, canCost ? new int[]{5} : NONE, "reports.export");
    }

    // ── Reports: Money Leak ──────────────────────────────────────────────────────
    @GetMapping("/reports/money-leak")
    public ResponseEntity<byte[]> moneyLeak(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "reports.export");
        Long cid = p.getCompanyId();
        var txns = cashTransactionRepository.findAll(cashSpec(cid, "expense"));
        Map<String, BigDecimal> byCat = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CashTransaction t : txns) {
            String cat = t.getCategory() != null ? t.getCategory() : "other";
            byCat.merge(cat, t.getAmount(), BigDecimal::add);
            total = total.add(t.getAmount());
        }
        final BigDecimal ft = total;
        List<Object[]> rows = new ArrayList<>();
        byCat.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).forEach(e -> {
            BigDecimal pct = ft.signum() == 0 ? BigDecimal.ZERO : e.getValue().multiply(BigDecimal.valueOf(100)).divide(ft, 1, RoundingMode.HALF_UP);
            rows.add(new Object[]{ e.getKey(), e.getValue(), pct + "%" });
        });
        Object[] totals = { "TOTAL", total, null };
        return out(p, "money_leak", "Money Leak", "Expense breakdown",
                new String[]{"Category", "Amount", "% of Expenses"}, rows, totals, new int[]{1}, "reports.export");
    }

    // ── Reports: Customer Rating ─────────────────────────────────────────────────
    @GetMapping("/reports/customer-rating")
    public ResponseEntity<byte[]> customerRating(@AuthenticationPrincipal BizControlPrincipal p) throws Exception {
        perm.require(p, "reports.export");
        Long cid = p.getCompanyId();
        boolean canDebt = perm.hasPermission(p, "customers.view_debt");
        List<Object[]> rows = new ArrayList<>();
        for (Customer c : customerRepository.findByCompanyIdAndStatus(cid, "active")) {
            int score = 50;
            BigDecimal debt = c.getCurrentDebt() != null ? c.getCurrentDebt() : BigDecimal.ZERO;
            BigDecimal limit = c.getDebtLimit() != null ? c.getDebtLimit() : BigDecimal.ZERO;
            if (limit.signum() > 0) {
                double ratio = debt.divide(limit, 4, RoundingMode.HALF_UP).doubleValue();
                if (ratio < 0.25) score += 20; else if (ratio < 0.5) score += 10; else if (ratio > 0.9) score -= 20;
            }
            if ("vip".equals(c.getCustomerType())) score += 20;
            else if ("wholesale".equals(c.getCustomerType())) score += 10;
            else if ("risky".equals(c.getCustomerType())) score -= 30;
            if (debt.signum() == 0) score += 15;
            score = Math.max(0, Math.min(100, score));
            String level = score >= 80 ? "HIGH" : score >= 50 ? "MEDIUM" : "LOW";
            rows.add(new Object[]{ c.getName(), nvl(c.getPhone()), nvl(c.getCustomerType()),
                    score, level, canDebt ? debt : null });
        }
        rows.sort((a, b) -> Integer.compare((int) b[3], (int) a[3]));
        return out(p, "customer_rating", "Customer Rating", "Trust scores",
                new String[]{"Customer", "Phone", "Type", "Trust Score", "Trust Level", "Current Debt"},
                rows, null, canDebt ? new int[]{5} : NONE, "reports.export");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static final int[] NONE = new int[]{};

    private ResponseEntity<byte[]> out(BizControlPrincipal p, String file, String title, String filter,
                                       String[] headers, List<Object[]> rows, Object[] totals, int[] money,
                                       String permCode) throws Exception {
        String company = companyService.getById(p.getCompanyId()).getName();
        byte[] data = excel.genericReport(company, title, p.getRole() + " (user #" + p.getUserId() + ")",
                filter, headers, rows, totals, money);
        accessLog.logAllowed(p.getCompanyId(), p.getUserId(), permCode, "export");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.setContentDisposition(ContentDisposition.attachment()
                .filename(file + "_" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + ".xlsx").build());
        h.setContentLength(data.length);
        return ResponseEntity.ok().headers(h).body(data);
    }

    private Map<Long, Account> accountMap(Long cid) {
        Map<Long, Account> m = new HashMap<>();
        for (Account a : accountRepository.findByCompanyIdOrderByCode(cid)) m.put(a.getId(), a);
        return m;
    }

    private org.springframework.data.jpa.domain.Specification<CashTransaction> cashSpec(Long cid, String type) {
        return (root, q, cb) -> {
            var preds = new ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("company").get("id"), cid));
            preds.add(cb.equal(root.get("status"), "active"));
            if (type != null) preds.add(cb.equal(root.get("transactionType"), type));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private BigDecimal sumType(List<JournalService.TrialBalanceRow> rows, String type) {
        return rows.stream().filter(r -> r.type().equals(type)).map(JournalService.TrialBalanceRow::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String whName(Long cid, Long whId) {
        if (whId == null) return "—";
        return warehouseRepository.findByCompanyIdAndId(cid, whId).map(Warehouse::getName).orElse("#" + whId);
    }

    private String prodName(Long cid, Long pid) {
        return productRepository.findByCompanyIdAndId(cid, pid).map(Product::getName).orElse("#" + pid);
    }

    private static String nvl(String s) { return s != null ? s : ""; }
    private static String fmt(LocalDateTime d) { return d != null ? d.format(DT) : ""; }
}
