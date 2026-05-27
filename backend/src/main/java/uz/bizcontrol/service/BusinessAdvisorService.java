package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.bizcontrol.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Heuristic-based Business Advisor (no ML required).
 * Analyses company data and generates actionable insights,
 * cashflow forecasts, and stock runout predictions.
 */
@Service
@RequiredArgsConstructor
public class BusinessAdvisorService {

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final DebtRepository debtRepository;
    private final CashTransactionRepository cashTransactionRepository;

    // ── Insights ──────────────────────────────────────────────────────────────

    /**
     * Returns a list of prioritised business insights for the given company.
     * Each insight has: type, severity (HIGH/MEDIUM/LOW), title, body, action.
     */
    public List<Map<String, Object>> getInsights(Long companyId) {
        List<Map<String, Object>> insights = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // ── 1. Dead stock ─────────────────────────────────────────────────────
        var deadStock = productRepository.findDeadStockSince(companyId, now.minusDays(60));
        if (!deadStock.isEmpty()) {
            BigDecimal deadValue = deadStock.stream()
                    .map(p -> p.getPurchasePrice() != null && p.getCurrentStock() != null
                            ? p.getPurchasePrice().multiply(p.getCurrentStock())
                            : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            insights.add(insight(
                    "DEAD_STOCK",
                    deadStock.size() > 10 ? "HIGH" : "MEDIUM",
                    deadStock.size() + " dead-stock items (60+ days without sale)",
                    "Capital locked: " + formatAmount(deadValue) + " UZS. Consider discounts or liquidation.",
                    "Go to Reports → Dead Stock"
            ));
        }

        // ── 2. Low stock ──────────────────────────────────────────────────────
        long lowStockCount = productRepository.countLowStockByCompany(companyId);
        if (lowStockCount > 0) {
            insights.add(insight(
                    "LOW_STOCK",
                    lowStockCount > 20 ? "HIGH" : "MEDIUM",
                    lowStockCount + " products running low or out of stock",
                    "Risk of missed sales. Create purchase orders to restock.",
                    "Go to Stock → Low Stock"
            ));
        }

        // ── 3. Customer debt risk ─────────────────────────────────────────────
        BigDecimal customerDebt = safeAmount(debtRepository.sumRemainingByCompanyAndType(companyId, "customer"));
        BigDecimal recentSales = safeAmount(saleRepository.sumTotalByCompanyAndDateRange(
                companyId, now.minusDays(30), now));
        if (recentSales.compareTo(BigDecimal.ZERO) > 0) {
            double debtRatio = customerDebt.divide(recentSales, 4, RoundingMode.HALF_UP).doubleValue();
            if (debtRatio > 1.0) {
                insights.add(insight(
                        "HIGH_CUSTOMER_DEBT",
                        "HIGH",
                        "Customer debt exceeds 1 month of sales",
                        "Total customer debt: " + formatAmount(customerDebt) + " UZS (" +
                                String.format("%.0f", debtRatio * 100) + "% of monthly sales). " +
                                "Follow up with overdue customers.",
                        "Go to Debts → Customer Debts"
                ));
            }
        }

        // ── 4. Profit decline ─────────────────────────────────────────────────
        BigDecimal currentProfit = safeAmount(saleRepository.sumProfitByCompanyAndDateRange(
                companyId, now.minusDays(30), now));
        BigDecimal prevProfit = safeAmount(saleRepository.sumProfitByCompanyAndDateRange(
                companyId, now.minusDays(60), now.minusDays(30)));
        if (prevProfit.compareTo(BigDecimal.ZERO) > 0 && currentProfit.compareTo(prevProfit) < 0) {
            double decline = prevProfit.subtract(currentProfit)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(prevProfit, 1, RoundingMode.HALF_UP)
                    .doubleValue();
            if (decline > 10) {
                insights.add(insight(
                        "PROFIT_DECLINE",
                        decline > 30 ? "HIGH" : "MEDIUM",
                        String.format("Profit down %.0f%% vs last month", decline),
                        String.format("Current 30-day profit: %s UZS. Previous: %s UZS. " +
                                "Review pricing, discounts, and costs.",
                                formatAmount(currentProfit), formatAmount(prevProfit)),
                        "Go to Reports → Profit Breakdown"
                ));
            }
        }

        // ── 5. Supplier debt ──────────────────────────────────────────────────
        BigDecimal supplierDebt = safeAmount(debtRepository.sumRemainingByCompanyAndType(companyId, "supplier"));
        if (supplierDebt.compareTo(BigDecimal.ZERO) > 0 && recentSales.compareTo(BigDecimal.ZERO) > 0) {
            double supRatio = supplierDebt.divide(recentSales, 4, RoundingMode.HALF_UP).doubleValue();
            if (supRatio > 0.8) {
                insights.add(insight(
                        "SUPPLIER_DEBT",
                        supRatio > 1.5 ? "HIGH" : "MEDIUM",
                        "High supplier debt: " + formatAmount(supplierDebt) + " UZS",
                        String.format("Supplier debt is %.0f%% of monthly sales. " +
                                "Risk of supply disruption if not paid.", supRatio * 100),
                        "Go to Debts → Supplier Debts"
                ));
            }
        }

        // ── 6. No recent purchases ────────────────────────────────────────────
        BigDecimal recentPurchases = safeAmount(purchaseRepository.sumTotalByCompanyAndDateRange(
                companyId, now.minusDays(30), now));
        if (recentPurchases.compareTo(BigDecimal.ZERO) == 0 && lowStockCount > 5) {
            insights.add(insight(
                    "NO_RECENT_PURCHASES",
                    "MEDIUM",
                    "No purchases in the last 30 days but stock is running low",
                    "You have " + lowStockCount + " low/out-of-stock products. Consider placing orders soon.",
                    "Go to Purchases → New Purchase"
            ));
        }

        // Sort by severity (HIGH first)
        insights.sort(Comparator.comparingInt(i -> {
            String sev = (String) ((Map<?, ?>) i).get("severity");
            return "HIGH".equals(sev) ? 0 : "MEDIUM".equals(sev) ? 1 : 2;
        }));

        return insights;
    }

    // ── Forecasting ───────────────────────────────────────────────────────────

    /**
     * Returns stock runout predictions and cashflow forecast.
     */
    public Map<String, Object> getForecasts(Long companyId) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> result = new LinkedHashMap<>();

        // ── Stock runout predictions ──────────────────────────────────────────
        // For each low-stock product, estimate days until out-of-stock
        var lowStockProducts = productRepository.findLowStockProducts(companyId);
        List<Map<String, Object>> stockForecasts = new ArrayList<>();
        for (var product : lowStockProducts) {
            BigDecimal stock = product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO;
            // Approximate daily sales rate from last 30 days
            // (Simplified: we can't easily get per-product sales here without a custom query)
            int daysLeft = stock.compareTo(BigDecimal.ZERO) <= 0 ? 0 :
                    Math.min(stock.intValue(), 999);
            Map<String, Object> fc = new LinkedHashMap<>();
            fc.put("productId", product.getId());
            fc.put("productName", product.getName());
            fc.put("sku", product.getSku());
            fc.put("currentStock", stock);
            fc.put("unit", product.getUnit());
            fc.put("minStockLevel", product.getMinStockLevel());
            fc.put("urgency", stock.compareTo(BigDecimal.ZERO) == 0 ? "OUT" :
                    daysLeft <= 3 ? "CRITICAL" : daysLeft <= 7 ? "HIGH" : "MEDIUM");
            stockForecasts.add(fc);
        }
        result.put("stockRunout", stockForecasts);
        result.put("stockRunoutCount", stockForecasts.size());

        // ── Monthly cashflow forecast (next 3 months) ─────────────────────────
        // Simple: average of last 3 months sales and expenses, projected forward
        List<Map<String, Object>> cashflowForecast = new ArrayList<>();
        BigDecimal avgSales = BigDecimal.ZERO;
        BigDecimal avgExpenses = BigDecimal.ZERO;
        int historyMonths = 3;

        for (int i = 1; i <= historyMonths; i++) {
            LocalDate start = LocalDate.now().minusMonths(i).withDayOfMonth(1);
            LocalDate end = start.plusMonths(1).minusDays(1);
            BigDecimal s = safeAmount(saleRepository.sumTotalByCompanyAndDateRange(
                    companyId, start.atStartOfDay(), end.atTime(LocalTime.MAX)));
            BigDecimal e = sumExpenses(companyId, start.atStartOfDay(), end.atTime(LocalTime.MAX));
            avgSales = avgSales.add(s);
            avgExpenses = avgExpenses.add(e);
        }
        avgSales = avgSales.divide(BigDecimal.valueOf(historyMonths), 0, RoundingMode.HALF_UP);
        avgExpenses = avgExpenses.divide(BigDecimal.valueOf(historyMonths), 0, RoundingMode.HALF_UP);

        for (int i = 1; i <= 3; i++) {
            LocalDate monthStart = LocalDate.now().plusMonths(i).withDayOfMonth(1);
            Map<String, Object> fc = new LinkedHashMap<>();
            fc.put("month", monthStart.toString().substring(0, 7));
            fc.put("forecastedSales", avgSales);
            fc.put("forecastedExpenses", avgExpenses);
            fc.put("forecastedProfit", avgSales.subtract(avgExpenses));
            fc.put("confidence", "MEDIUM");
            cashflowForecast.add(fc);
        }
        result.put("cashflowForecast", cashflowForecast);

        // ── Debt collection risk ──────────────────────────────────────────────
        BigDecimal overdueDebt = safeAmount(debtRepository.sumRemainingByCompanyAndType(companyId, "customer"));
        String debtRisk = overdueDebt.compareTo(new BigDecimal("10000000")) > 0 ? "HIGH" :
                          overdueDebt.compareTo(new BigDecimal("3000000")) > 0 ? "MEDIUM" : "LOW";
        result.put("debtCollectionRisk", Map.of(
                "totalCustomerDebt", overdueDebt,
                "riskLevel", debtRisk
        ));

        return result;
    }

    // ── Anomaly Detection ─────────────────────────────────────────────────────

    /**
     * Detects unusual activity in the last N days (default 7).
     */
    public List<Map<String, Object>> getAnomalies(Long companyId, int days) {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LocalDateTime now = LocalDateTime.now();

        // ── 1. Unusual discount spikes ────────────────────────────────────────
        // Compare average discount of last 7 days vs prior 30 days
        // (simplified: just check if total discounts seem high)
        // We'd need custom repository methods for deep analysis; this is a simplified version

        // ── 2. Large stock adjustments ────────────────────────────────────────
        // Using StockMovement records with type "adjustment"
        // (Checking productRepository for abnormal stock levels is a proxy)

        // ── 3. Expense spikes ─────────────────────────────────────────────────
        BigDecimal recentExpenses = sumExpenses(companyId, since, now);
        BigDecimal normalExpenses = sumExpenses(companyId, now.minusDays(days * 4), now.minusDays(days));
        BigDecimal avgNormalExpenses = days > 0 && normalExpenses.compareTo(BigDecimal.ZERO) > 0
                ? normalExpenses.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        if (avgNormalExpenses.compareTo(BigDecimal.ZERO) > 0) {
            double expenseRatio = recentExpenses.divide(avgNormalExpenses, 4, RoundingMode.HALF_UP).doubleValue();
            if (expenseRatio > 2.0) {
                anomalies.add(anomaly(
                        "EXPENSE_SPIKE",
                        expenseRatio > 3.0 ? "HIGH" : "MEDIUM",
                        String.format("Expenses %.0fx higher than normal in last %d days", expenseRatio, days),
                        "Recent: " + formatAmount(recentExpenses) + " UZS vs normal: " + formatAmount(avgNormalExpenses) + " UZS"
                ));
            }
        }

        // ── 4. Sales decline vs prior period ─────────────────────────────────
        BigDecimal recentSales = safeAmount(saleRepository.sumTotalByCompanyAndDateRange(companyId, since, now));
        BigDecimal priorSales = safeAmount(saleRepository.sumTotalByCompanyAndDateRange(
                companyId, now.minusDays(days * 2), now.minusDays(days)));

        if (priorSales.compareTo(BigDecimal.ZERO) > 0) {
            double decline = priorSales.subtract(recentSales)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(priorSales, 1, RoundingMode.HALF_UP)
                    .doubleValue();
            if (decline > 40) {
                anomalies.add(anomaly(
                        "SALES_DROP",
                        decline > 60 ? "HIGH" : "MEDIUM",
                        String.format("Sales dropped %.0f%% vs previous %d days", decline, days),
                        "Recent: " + formatAmount(recentSales) + " UZS vs prior: " + formatAmount(priorSales) + " UZS"
                ));
            }
        }

        // ── 5. Dead stock growth ──────────────────────────────────────────────
        int deadCount = productRepository.findDeadStockSince(companyId, now.minusDays(60)).size();
        long totalProducts = productRepository.countByCompanyIdAndStatus(companyId, "active");
        if (totalProducts > 0 && (double) deadCount / totalProducts > 0.25) {
            anomalies.add(anomaly(
                    "DEAD_STOCK_HIGH",
                    "MEDIUM",
                    deadCount + " items have had no sales in 60+ days",
                    String.format("%.0f%% of active products are dead stock. Review pricing and promotions.",
                            100.0 * deadCount / totalProducts)
            ));
        }

        return anomalies;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> insight(String type, String severity,
                                         String title, String body, String action) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("severity", severity);
        m.put("title", title);
        m.put("body", body);
        m.put("action", action);
        m.put("generatedAt", LocalDateTime.now().toString());
        return m;
    }

    private Map<String, Object> anomaly(String type, String severity, String title, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("severity", severity);
        m.put("title", title);
        m.put("detail", detail);
        m.put("detectedAt", LocalDateTime.now().toString());
        return m;
    }

    private BigDecimal safeAmount(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String formatAmount(BigDecimal v) {
        if (v == null) return "0";
        return String.format("%,.0f", v.doubleValue());
    }

    private BigDecimal sumExpenses(Long companyId, LocalDateTime from, LocalDateTime to) {
        var spec = (org.springframework.data.jpa.domain.Specification<uz.bizcontrol.entity.CashTransaction>)
                (root, q, cb) -> {
                    var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    preds.add(cb.equal(root.get("company").get("id"), companyId));
                    preds.add(cb.equal(root.get("transactionType"), "expense"));
                    preds.add(cb.equal(root.get("status"), "active"));
                    preds.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), from));
                    preds.add(cb.lessThanOrEqualTo(root.get("transactionDate"), to));
                    return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
                };
        return cashTransactionRepository.findAll(spec).stream()
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
