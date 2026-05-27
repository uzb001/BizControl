package uz.bizcontrol.accounting;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only financial reporting over the ledger + manual reversal.
 * Gated by the existing {@code reports.view} permission (financial statements are reports).
 */
@RestController
@RequestMapping("/accounting")
@RequiredArgsConstructor
public class AccountingController {

    private final ChartOfAccountsService chartService;
    private final JournalService journalService;
    private final JournalEntryRepository entryRepository;
    private final PermissionService permissionService;

    @GetMapping("/accounts")
    public ResponseEntity<List<Account>> accounts(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "reports.view");
        return ResponseEntity.ok(chartService.list(p.getCompanyId()));
    }

    @GetMapping("/journal")
    public ResponseEntity<Page<JournalEntry>> journal(
            @AuthenticationPrincipal BizControlPrincipal p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        permissionService.require(p, "reports.view");
        return ResponseEntity.ok(entryRepository.findByCompanyIdOrderByEntryDateDescIdDesc(
                p.getCompanyId(), PageRequest.of(page, size)));
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<Map<String, Object>> trialBalance(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "reports.view");
        var rows = journalService.trialBalance(p.getCompanyId());
        BigDecimal totalDebit = rows.stream().map(JournalService.TrialBalanceRow::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = rows.stream().map(JournalService.TrialBalanceRow::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rows", rows);
        body.put("totalDebit", totalDebit);
        body.put("totalCredit", totalCredit);
        body.put("balanced", totalDebit.compareTo(totalCredit) == 0);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/profit-loss")
    public ResponseEntity<Map<String, Object>> profitLoss(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "reports.view");
        var rows = journalService.trialBalance(p.getCompanyId());
        BigDecimal revenue = sumType(rows, "REVENUE");
        BigDecimal expenses = sumType(rows, "EXPENSE");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revenue", revenue);
        body.put("expenses", expenses);
        body.put("netProfit", revenue.subtract(expenses));
        body.put("lines", rows.stream().filter(r -> r.type().equals("REVENUE") || r.type().equals("EXPENSE")).toList());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<Map<String, Object>> balanceSheet(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "reports.view");
        var rows = journalService.trialBalance(p.getCompanyId());
        BigDecimal assets = sumType(rows, "ASSET");
        BigDecimal liabilities = sumType(rows, "LIABILITY");
        BigDecimal equity = sumType(rows, "EQUITY");
        BigDecimal netProfit = sumType(rows, "REVENUE").subtract(sumType(rows, "EXPENSE"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("assets", assets);
        body.put("liabilities", liabilities);
        body.put("equity", equity);
        body.put("retainedEarnings", netProfit);
        // Accounting identity: Assets = Liabilities + Equity + Net Profit
        body.put("balanced", assets.compareTo(liabilities.add(equity).add(netProfit)) == 0);
        body.put("lines", rows.stream().filter(r ->
                r.type().equals("ASSET") || r.type().equals("LIABILITY") || r.type().equals("EQUITY")).toList());
        return ResponseEntity.ok(body);
    }

    /** Manual reversal of an entry (corrections never destroy history). */
    @PostMapping("/journal/{id}/reverse")
    public ResponseEntity<JournalEntry> reverse(
            @AuthenticationPrincipal BizControlPrincipal p,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        permissionService.require(p, "reports.view");
        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : null;
        return ResponseEntity.ok(journalService.reverse(p.getCompanyId(), p.getUserId(), id, reason));
    }

    private BigDecimal sumType(List<JournalService.TrialBalanceRow> rows, String type) {
        return rows.stream().filter(r -> r.type().equals(type))
                .map(JournalService.TrialBalanceRow::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
