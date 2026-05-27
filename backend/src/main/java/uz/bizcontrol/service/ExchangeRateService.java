package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.ExchangeRate;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.ExchangeRateRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Manual exchange-rate registry (architecture is API-ready: a future provider can
 * call {@link #setRate}). Conversion is ONLY performed when explicitly requested
 * via {@link #toBase} — transactions and balances always keep their own currency.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository repo;
    private final CompanyService companyService;
    private final AuditService auditService;

    public String baseCurrency(Long companyId) {
        String b = companyService.getById(companyId).getMainCurrency();
        return b != null && !b.isBlank() ? b : "UZS";
    }

    public List<ExchangeRate> list(Long companyId) {
        return repo.findByCompanyId(companyId);
    }

    /** Rate from {@code currency} to the company base currency (1.0 for base / unknown). */
    public BigDecimal rateToBase(Long companyId, String currency) {
        if (currency == null || currency.equalsIgnoreCase(baseCurrency(companyId))) return BigDecimal.ONE;
        return repo.findByCompanyIdAndCurrency(companyId, currency.toUpperCase())
                .map(ExchangeRate::getRateToBase).orElse(BigDecimal.ONE);
    }

    @Transactional
    public ExchangeRate setRate(Long companyId, Long userId, String currency, BigDecimal rate) {
        if (currency == null || currency.isBlank()) throw new BusinessException("Currency is required");
        if (rate == null || rate.signum() <= 0) throw new BusinessException("Rate must be greater than zero");
        String cur = currency.toUpperCase();
        ExchangeRate r = repo.findByCompanyIdAndCurrency(companyId, cur)
                .orElseGet(() -> ExchangeRate.builder().companyId(companyId).currency(cur).build());
        r.setRateToBase(rate);
        r.setUpdatedBy(userId);
        r = repo.save(r);
        auditService.log(companyId, userId, "SET_EXCHANGE_RATE", "ExchangeRate", r.getId(),
                null, cur + " = " + rate);
        return r;
    }

    /** Explicit conversion of an amount in {@code currency} to the base currency. */
    public BigDecimal toBase(Long companyId, String currency, BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.multiply(rateToBase(companyId, currency)).setScale(2, RoundingMode.HALF_UP);
    }
}
