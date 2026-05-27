package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.ExchangeRate;

import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    List<ExchangeRate> findByCompanyId(Long companyId);
    Optional<ExchangeRate> findByCompanyIdAndCurrency(Long companyId, String currency);
}
