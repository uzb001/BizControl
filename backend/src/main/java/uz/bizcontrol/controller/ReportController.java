package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.repository.*;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.AccessLogService;
import uz.bizcontrol.service.PermissionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final DebtRepository debtRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final PermissionService permissionService;
    private final AccessLogService accessLogService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(defaultValue = "this_month") String period) {

        permissionService.require(principal, "reports.view");
        LocalDateTime[] range = getRange(period);
        Long cid = principal.getCompanyId();

        BigDecimal totalSales = saleRepository.sumTotalByCompanyAndDateRange(cid, range[0], range[1]);
        BigDecimal totalPurchases = purchaseRepository.sumTotalByCompanyAndDateRange(cid, range[0], range[1]);
        BigDecimal totalCustomerDebt = customerRepository.sumTotalDebtByCompany(cid);
        BigDecimal totalSupplierDebt = supplierRepository.sumTotalDebtByCompany(cid);
        BigDecimal stockValue = productRepository.sumStockValue(cid);
        long lowStockCount = productRepository.countLowStockByCompany(cid);
        long unpaidSales = saleRepository.countUnpaidSalesByCompany(cid);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSales", totalSales);
        result.put("totalPurchases", totalPurchases);
        result.put("totalCustomerDebt", totalCustomerDebt);
        result.put("totalSupplierDebt", totalSupplierDebt);
        result.put("stockValue", stockValue);
        result.put("lowStockCount", lowStockCount);
        result.put("unpaidSales", unpaidSales);
        result.put("period", period);

        // Profit data only if permitted
        if (permissionService.hasPermission(principal, "reports.view_profit")) {
            accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "reports.view_profit", "reports");
            BigDecimal totalProfit = saleRepository.sumProfitByCompanyAndDateRange(cid, range[0], range[1]);
            result.put("totalProfit", totalProfit);
            result.put("grossMargin", calcMargin(totalSales, totalProfit));

            // Daily trend: last 7 days
            List<Map<String, Object>> dailyTrend = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate day = LocalDate.now().minusDays(i);
                LocalDateTime s = day.atStartOfDay();
                LocalDateTime e = day.atTime(LocalTime.MAX);
                BigDecimal daySales = saleRepository.sumTotalByCompanyAndDateRange(cid, s, e);
                BigDecimal dayProfit = saleRepository.sumProfitByCompanyAndDateRange(cid, s, e);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("date", day.toString());
                entry.put("sales", daySales);
                entry.put("profit", dayProfit);
                dailyTrend.add(entry);
            }
            result.put("dailyTrend", dailyTrend);
        } else {
            // Still show sales trend without profit
            List<Map<String, Object>> dailyTrend = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate day = LocalDate.now().minusDays(i);
                LocalDateTime s = day.atStartOfDay();
                LocalDateTime e = day.atTime(LocalTime.MAX);
                BigDecimal daySales = saleRepository.sumTotalByCompanyAndDateRange(cid, s, e);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("date", day.toString());
                entry.put("sales", daySales);
                dailyTrend.add(entry);
            }
            result.put("dailyTrend", dailyTrend);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/stock")
    public ResponseEntity<?> stockReport(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String stockStatus) {
        permissionService.require(principal, "reports.view");
        var spec = uz.bizcontrol.util.ProductSpec.build(null, "active", categoryId, null, stockStatus);
        var withCompany = org.springframework.data.jpa.domain.Specification.where(
                (org.springframework.data.jpa.domain.Specification<Product>)
                        (root, q, cb) -> cb.equal(root.get("company").get("id"), principal.getCompanyId())
        ).and(spec);
        List<Product> products = productRepository.findAll(withCompany);
        // Mask purchase price
        if (!permissionService.hasPermission(principal, "products.view_purchase_price")) {
            products.forEach(p -> p.setPurchasePrice(null));
        }
        return ResponseEntity.ok(products);
    }

    @GetMapping("/low-stock")
    public ResponseEntity<?> lowStockReport(@AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "reports.view");
        List<Product> products = productRepository.findLowStockProducts(principal.getCompanyId());
        if (!permissionService.hasPermission(principal, "products.view_purchase_price")) {
            products.forEach(p -> p.setPurchasePrice(null));
        }
        return ResponseEntity.ok(products);
    }

    // ─── SMART FEATURES ──────────────────────────────────────────────────────────

    @GetMapping("/dead-stock")
    public ResponseEntity<?> deadStock(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(defaultValue = "60") int days) {
        permissionService.require(principal, "reports.view_dead_stock");
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Product> dead = productRepository.findDeadStockSince(principal.getCompanyId(), since);

        boolean canSeeCost = permissionService.hasPermission(principal, "products.view_purchase_price");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Product p : dead) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("sku", p.getSku());
            row.put("category", p.getCategory() != null ? p.getCategory().getName() : null);
            row.put("currentStock", p.getCurrentStock());
            row.put("unit", p.getUnit());
            if (canSeeCost) {
                row.put("purchasePrice", p.getPurchasePrice());
                row.put("stockValue", p.getCurrentStock().multiply(p.getPurchasePrice()));
            }
            row.put("daysSinceLastSale", days + "+");
            result.add(row);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("products", result);
        response.put("totalProducts", result.size());
        response.put("daysThreshold", days);
        if (canSeeCost) {
            response.put("totalValue", result.stream()
                    .filter(r -> r.get("stockValue") != null)
                    .map(r -> (BigDecimal) r.get("stockValue"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/customer-scores")
    public ResponseEntity<?> customerScores(@AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "reports.view_customer_scores");
        Long cid = principal.getCompanyId();
        var customers = customerRepository.findByCompanyIdAndStatus(cid, "active");
        List<Map<String, Object>> result = new ArrayList<>();

        boolean canViewDebt = permissionService.hasPermission(principal, "customers.view_debt");

        for (Customer c : customers) {
            int score = 50;
            BigDecimal debt = c.getCurrentDebt();
            BigDecimal limit = c.getDebtLimit() != null ? c.getDebtLimit() : BigDecimal.ZERO;

            if (limit.compareTo(BigDecimal.ZERO) > 0) {
                double ratio = debt.divide(limit, 4, RoundingMode.HALF_UP).doubleValue();
                if (ratio < 0.25) score += 20;
                else if (ratio < 0.5) score += 10;
                else if (ratio > 0.9) score -= 20;
            }

            if ("vip".equals(c.getCustomerType())) score += 20;
            else if ("wholesale".equals(c.getCustomerType())) score += 10;
            else if ("risky".equals(c.getCustomerType())) score -= 30;

            if (debt.compareTo(BigDecimal.ZERO) == 0) score += 15;

            String trustLevel = score >= 80 ? "HIGH" : score >= 50 ? "MEDIUM" : "LOW";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("name", c.getName());
            row.put("phone", c.getPhone());
            row.put("customerType", c.getCustomerType());
            if (canViewDebt) {
                row.put("currentDebt", debt);
                row.put("debtLimit", limit);
            }
            row.put("trustScore", Math.max(0, Math.min(100, score)));
            row.put("trustLevel", trustLevel);
            result.add(row);
        }

        result.sort(Comparator.comparingInt(r -> -(Integer) r.get("trustScore")));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/money-leak")
    public ResponseEntity<?> moneyLeak(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(defaultValue = "this_month") String period) {
        permissionService.require(principal, "reports.view_money_leak");
        LocalDateTime[] range = getRange(period);
        Long cid = principal.getCompanyId();

        var spec = buildCashSpec(cid, "expense", null, range[0], range[1]);
        var transactions = cashTransactionRepository.findAll(spec);

        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (CashTransaction t : transactions) {
            String cat = t.getCategory() != null ? t.getCategory() : "other";
            byCategory.merge(cat, t.getAmount(), BigDecimal::add);
            totalExpense = totalExpense.add(t.getAmount());
        }

        List<Map<String, Object>> categories = new ArrayList<>();
        final BigDecimal finalTotal = totalExpense;
        byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("category", e.getKey());
                    cat.put("amount", e.getValue());
                    cat.put("percentage", finalTotal.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                            e.getValue().multiply(BigDecimal.valueOf(100)).divide(finalTotal, 1, RoundingMode.HALF_UP));
                    categories.add(cat);
                });

        BigDecimal totalSales = saleRepository.sumTotalByCompanyAndDateRange(cid, range[0], range[1]);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("categories", categories);
        response.put("totalExpense", totalExpense);
        response.put("totalSales", totalSales);
        response.put("expenseToSalesRatio", totalSales.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                totalExpense.multiply(BigDecimal.valueOf(100)).divide(totalSales, 1, RoundingMode.HALF_UP));
        response.put("period", period);

        if (permissionService.hasPermission(principal, "reports.view_profit")) {
            BigDecimal totalProfit = saleRepository.sumProfitByCompanyAndDateRange(cid, range[0], range[1]);
            response.put("totalProfit", totalProfit);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/monthly-trend")
    public ResponseEntity<?> monthlyTrend(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(defaultValue = "6") int months) {
        permissionService.require(principal, "reports.view");
        Long cid = principal.getCompanyId();
        boolean canProfit = permissionService.hasPermission(principal, "reports.view_profit");

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDate start = LocalDate.now().minusMonths(i).withDayOfMonth(1);
            LocalDate end = start.plusMonths(1).minusDays(1);
            LocalDateTime s = start.atStartOfDay();
            LocalDateTime e = end.atTime(LocalTime.MAX);
            BigDecimal sales = saleRepository.sumTotalByCompanyAndDateRange(cid, s, e);
            BigDecimal purchases = purchaseRepository.sumTotalByCompanyAndDateRange(cid, s, e);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("month", start.toString().substring(0, 7));
            entry.put("sales", sales);
            entry.put("purchases", purchases);
            if (canProfit) {
                entry.put("profit", saleRepository.sumProfitByCompanyAndDateRange(cid, s, e));
            }
            trend.add(entry);
        }
        return ResponseEntity.ok(trend);
    }

    @GetMapping("/profit-breakdown")
    public ResponseEntity<?> profitBreakdown(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(defaultValue = "this_month") String period) {
        permissionService.require(principal, "reports.view_profit");
        accessLogService.logAllowed(principal.getCompanyId(), principal.getUserId(), "reports.view_profit", "reports");
        LocalDateTime[] range = getRange(period);
        Long cid = principal.getCompanyId();

        BigDecimal totalSales = saleRepository.sumTotalByCompanyAndDateRange(cid, range[0], range[1]);
        BigDecimal totalProfit = saleRepository.sumProfitByCompanyAndDateRange(cid, range[0], range[1]);
        BigDecimal totalPurchases = purchaseRepository.sumTotalByCompanyAndDateRange(cid, range[0], range[1]);
        BigDecimal customerDebt = customerRepository.sumTotalDebtByCompany(cid);
        BigDecimal supplierDebt = supplierRepository.sumTotalDebtByCompany(cid);

        return ResponseEntity.ok(Map.of(
                "totalSales", totalSales,
                "totalPurchases", totalPurchases,
                "grossProfit", totalProfit,
                "customerDebt", customerDebt,
                "supplierDebt", supplierDebt,
                "grossMargin", calcMargin(totalSales, totalProfit),
                "period", period
        ));
    }

    // ─── Smart Price Assistant ────────────────────────────────────────────────────

    @GetMapping("/smart-price/{productId}")
    public ResponseEntity<?> smartPrice(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long productId,
            @RequestParam(defaultValue = "30") double targetMarginPct) {

        permissionService.require(principal, "products.view_purchase_price");
        Product p = productRepository.findByCompanyIdAndId(principal.getCompanyId(), productId)
                .orElseThrow(() -> new uz.bizcontrol.exception.BusinessException("Product not found"));

        BigDecimal cost = p.getPurchasePrice();
        BigDecimal currentPrice = p.getSellingPrice();

        BigDecimal minSafePrice = cost.multiply(BigDecimal.valueOf(1.01))
                .setScale(0, RoundingMode.CEILING);
        BigDecimal recommended = cost.multiply(BigDecimal.valueOf(1 + targetMarginPct / 100.0))
                .setScale(0, RoundingMode.CEILING);

        double currentMargin = cost.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                currentPrice.subtract(cost).multiply(BigDecimal.valueOf(100))
                        .divide(cost, 1, RoundingMode.HALF_UP).doubleValue();

        String warning = null;
        if (currentPrice.compareTo(cost) < 0) {
            warning = "SELLING BELOW COST — you are losing money on every sale";
        } else if (currentMargin < 10) {
            warning = "Very low margin (" + currentMargin + "%) — consider raising price";
        } else if (currentMargin < 20) {
            warning = "Low margin (" + currentMargin + "%) — monitor carefully";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", p.getId());
        result.put("productName", p.getName());
        result.put("purchaseCost", cost);
        result.put("currentSellingPrice", currentPrice);
        result.put("currentMarginPct", currentMargin);
        result.put("minSafePrice", minSafePrice);
        result.put("recommendedPrice", recommended);
        result.put("targetMarginPct", targetMarginPct);
        result.put("warning", warning);
        result.put("isBelowCost", currentPrice.compareTo(cost) < 0);
        return ResponseEntity.ok(result);
    }

    // ─── Business Health Score ────────────────────────────────────────────────────

    @GetMapping("/health-score")
    public ResponseEntity<?> healthScore(@AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "reports.view");
        Long cid = principal.getCompanyId();

        BigDecimal stockValue = productRepository.sumStockValue(cid);
        long lowStockCount = productRepository.countLowStockByCompany(cid);
        long totalProducts = productRepository.countByCompanyIdAndStatus(cid, "active");

        BigDecimal customerDebt = debtRepository.sumRemainingByCompanyAndType(cid, "customer");
        BigDecimal supplierDebt = debtRepository.sumRemainingByCompanyAndType(cid, "supplier");
        long unpaidSales = saleRepository.countUnpaidSalesByCompany(cid);

        LocalDateTime now = LocalDateTime.now();
        BigDecimal recentProfit = saleRepository.sumProfitByCompanyAndDateRange(cid, now.minusDays(30), now);
        BigDecimal prevProfit   = saleRepository.sumProfitByCompanyAndDateRange(cid, now.minusDays(60), now.minusDays(30));
        boolean profitDeclining = prevProfit.compareTo(BigDecimal.ZERO) > 0
                && recentProfit.compareTo(prevProfit) < 0;

        LocalDateTime since60 = now.minusDays(60);
        int deadStockCount = productRepository.findDeadStockSince(cid, since60).size();

        int score = 100;
        List<Map<String, Object>> issues = new ArrayList<>();

        BigDecimal recentSales = saleRepository.sumTotalByCompanyAndDateRange(cid, now.minusDays(30), now);
        if (recentSales.compareTo(BigDecimal.ZERO) > 0) {
            double debtRatio = customerDebt.divide(recentSales, 4, RoundingMode.HALF_UP).doubleValue();
            if (debtRatio > 1.5) { score -= 20; issues.add(issue("HIGH", "Dangerous customer debt", "Customer debt is " + String.format("%.0f", debtRatio * 100) + "% of recent sales")); }
            else if (debtRatio > 0.8) { score -= 12; issues.add(issue("MEDIUM", "High customer debt", "Customer debt is " + String.format("%.0f", debtRatio * 100) + "% of recent sales")); }
            else if (debtRatio > 0.4) { score -= 5; issues.add(issue("LOW", "Elevated customer debt", null)); }
        }

        if (totalProducts > 0) {
            double lowPct = (double) lowStockCount / totalProducts;
            if (lowPct > 0.3) { score -= 15; issues.add(issue("HIGH", lowStockCount + " products low/out of stock", "Consider restocking")); }
            else if (lowPct > 0.1) { score -= 7; issues.add(issue("MEDIUM", lowStockCount + " products low/out of stock", null)); }
        }

        if (totalProducts > 0) {
            double deadPct = (double) deadStockCount / totalProducts;
            if (deadPct > 0.2) { score -= 15; issues.add(issue("HIGH", deadStockCount + " dead stock items (60+ days)", "Capital locked in unsold inventory")); }
            else if (deadPct > 0.1) { score -= 7; issues.add(issue("MEDIUM", deadStockCount + " dead stock items", null)); }
        }

        if (profitDeclining) {
            double decline = prevProfit.compareTo(BigDecimal.ZERO) == 0 ? 100 :
                    prevProfit.subtract(recentProfit).divide(prevProfit, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
            if (decline > 30) { score -= 20; issues.add(issue("HIGH", "Profit down " + String.format("%.0f", decline) + "% vs last month", null)); }
            else if (decline > 10) { score -= 10; issues.add(issue("MEDIUM", "Profit declining " + String.format("%.0f", decline) + "% vs last month", null)); }
        }

        if (supplierDebt.compareTo(BigDecimal.ZERO) > 0 && recentSales.compareTo(BigDecimal.ZERO) > 0) {
            double supRatio = supplierDebt.divide(recentSales, 4, RoundingMode.HALF_UP).doubleValue();
            if (supRatio > 1.0) { score -= 10; issues.add(issue("MEDIUM", "High supplier debt", "Supplier debt is " + String.format("%.0f", supRatio * 100) + "% of sales")); }
        }

        score = Math.max(0, Math.min(100, score));
        String grade = score >= 80 ? "A" : score >= 65 ? "B" : score >= 50 ? "C" : score >= 35 ? "D" : "F";
        String label = score >= 80 ? "Excellent" : score >= 65 ? "Good" : score >= 50 ? "Fair" : score >= 35 ? "Poor" : "Critical";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("grade", grade);
        result.put("label", label);
        result.put("issues", issues);
        result.put("signals", Map.of(
                "lowStockCount", lowStockCount,
                "deadStockCount", deadStockCount,
                "unpaidSales", unpaidSales,
                "profitDeclining", profitDeclining,
                "customerDebt", customerDebt,
                "supplierDebt", supplierDebt
        ));
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> issue(String severity, String title, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", severity);
        m.put("title", title);
        m.put("description", description);
        return m;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private double calcMargin(BigDecimal sales, BigDecimal profit) {
        if (sales == null || sales.compareTo(BigDecimal.ZERO) == 0) return 0;
        return profit.multiply(BigDecimal.valueOf(100)).divide(sales, 1, RoundingMode.HALF_UP).doubleValue();
    }

    private LocalDateTime[] getRange(String period) {
        LocalDate today = LocalDate.now();
        return switch (period != null ? period : "this_month") {
            case "today" -> new LocalDateTime[]{today.atStartOfDay(), today.atTime(LocalTime.MAX)};
            case "yesterday" -> new LocalDateTime[]{today.minusDays(1).atStartOfDay(), today.minusDays(1).atTime(LocalTime.MAX)};
            case "this_week" -> new LocalDateTime[]{today.with(DayOfWeek.MONDAY).atStartOfDay(), today.atTime(LocalTime.MAX)};
            case "last_month" -> new LocalDateTime[]{today.minusMonths(1).withDayOfMonth(1).atStartOfDay(), today.withDayOfMonth(1).atStartOfDay()};
            case "last_3_months" -> new LocalDateTime[]{today.minusMonths(3).atStartOfDay(), today.atTime(LocalTime.MAX)};
            case "this_year" -> new LocalDateTime[]{today.withDayOfYear(1).atStartOfDay(), today.atTime(LocalTime.MAX)};
            default -> new LocalDateTime[]{today.withDayOfMonth(1).atStartOfDay(), today.atTime(LocalTime.MAX)};
        };
    }

    private org.springframework.data.jpa.domain.Specification<CashTransaction> buildCashSpec(
            Long companyId, String type, String source, LocalDateTime from, LocalDateTime to) {
        return (root, q, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("company").get("id"), companyId));
            preds.add(cb.equal(root.get("status"), "active"));
            if (type != null) preds.add(cb.equal(root.get("transactionType"), type));
            if (source != null) preds.add(cb.equal(root.get("paymentSource"), source));
            if (from != null) preds.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), from));
            if (to != null) preds.add(cb.lessThanOrEqualTo(root.get("transactionDate"), to));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
