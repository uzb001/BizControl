package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.bizcontrol.entity.Company;
import uz.bizcontrol.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final CompanyService companyService;
    private final AlertService alertService;

    public Map<String, Object> getDashboard(Long companyId, String period) {
        LocalDateTime[] range = getDateRange(period);
        LocalDateTime from = range[0], to = range[1];

        LocalDateTime prevFrom = from.minus(Duration.between(from, to));
        LocalDateTime prevTo = from;

        Company company = companyService.getById(companyId);

        BigDecimal currentSales = saleRepository.sumTotalByCompanyAndDateRange(companyId, from, to);
        BigDecimal prevSales = saleRepository.sumTotalByCompanyAndDateRange(companyId, prevFrom, prevTo);
        BigDecimal currentProfit = saleRepository.sumProfitByCompanyAndDateRange(companyId, from, to);
        BigDecimal prevProfit = saleRepository.sumProfitByCompanyAndDateRange(companyId, prevFrom, prevTo);

        BigDecimal totalCustomerDebt = customerRepository.sumTotalDebtByCompany(companyId);
        BigDecimal totalSupplierDebt = supplierRepository.sumTotalDebtByCompany(companyId);

        long lowStockCount = productRepository.countLowStockByCompany(companyId);
        long unpaidSales = saleRepository.countUnpaidSalesByCompany(companyId);
        long unpaidPurchases = purchaseRepository.countUnpaidPurchasesByCompany(companyId);
        long newAlerts = alertService.countNew(companyId);

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("sales", currentSales);
        dashboard.put("salesGrowth", calcGrowth(prevSales, currentSales));
        dashboard.put("profit", currentProfit);
        dashboard.put("profitGrowth", calcGrowth(prevProfit, currentProfit));
        dashboard.put("cashBalance", company.getCashBalance());
        dashboard.put("bankBalance", company.getBankBalance());
        dashboard.put("totalCustomerDebt", totalCustomerDebt);
        dashboard.put("totalSupplierDebt", totalSupplierDebt);
        dashboard.put("lowStockProducts", lowStockCount);
        dashboard.put("unpaidSales", unpaidSales);
        dashboard.put("unpaidPurchases", unpaidPurchases);
        dashboard.put("newAlerts", newAlerts);
        dashboard.put("healthScore", calcHealthScore(company, currentSales, prevSales, currentProfit, prevProfit, lowStockCount, totalCustomerDebt, totalSupplierDebt));
        dashboard.put("period", period);
        return dashboard;
    }

    private int calcHealthScore(Company company, BigDecimal sales, BigDecimal prevSales,
                                 BigDecimal profit, BigDecimal prevProfit,
                                 long lowStock, BigDecimal customerDebt, BigDecimal supplierDebt) {
        int score = 70;
        // Sales growth
        double growth = calcGrowth(prevSales, sales);
        if (growth > 10) score += 10;
        else if (growth < -10) score -= 15;
        // Profit
        if (profit.compareTo(BigDecimal.ZERO) > 0) score += 5;
        // Cash
        if (company.getCashBalance().compareTo(BigDecimal.ZERO) > 0) score += 5;
        else score -= 10;
        // Low stock
        if (lowStock > 5) score -= 5;
        if (lowStock > 10) score -= 5;
        // Debts
        if (customerDebt.compareTo(new BigDecimal("1000000")) > 0) score -= 5;
        if (supplierDebt.compareTo(new BigDecimal("1000000")) > 0) score -= 5;
        return Math.max(0, Math.min(100, score));
    }

    private double calcGrowth(BigDecimal prev, BigDecimal curr) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) return 0;
        return curr.subtract(prev)
                .multiply(new BigDecimal("100"))
                .divide(prev, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private LocalDateTime[] getDateRange(String period) {
        LocalDate today = LocalDate.now();
        return switch (period != null ? period : "this_month") {
            case "today" -> new LocalDateTime[]{today.atStartOfDay(), today.atTime(LocalTime.MAX)};
            case "yesterday" -> new LocalDateTime[]{today.minusDays(1).atStartOfDay(), today.minusDays(1).atTime(LocalTime.MAX)};
            case "this_week" -> new LocalDateTime[]{today.with(DayOfWeek.MONDAY).atStartOfDay(), today.atTime(LocalTime.MAX)};
            case "last_month" -> new LocalDateTime[]{today.minusMonths(1).withDayOfMonth(1).atStartOfDay(), today.withDayOfMonth(1).atStartOfDay()};
            default -> new LocalDateTime[]{today.withDayOfMonth(1).atStartOfDay(), today.atTime(LocalTime.MAX)};
        };
    }
}
