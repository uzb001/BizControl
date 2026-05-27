package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import uz.bizcontrol.entity.Sale;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {
    Optional<Sale> findByCompanyIdAndId(Long companyId, Long id);

    @Query("SELECT COALESCE(SUM(s.totalAmount),0) FROM Sale s WHERE s.company.id = :companyId AND s.status = 'active' AND s.saleDate BETWEEN :from AND :to")
    BigDecimal sumTotalByCompanyAndDateRange(Long companyId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(si.profitAmount),0) FROM SaleItem si JOIN si.sale s WHERE s.company.id = :companyId AND s.status = 'active' AND s.saleDate BETWEEN :from AND :to")
    BigDecimal sumProfitByCompanyAndDateRange(Long companyId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(s) FROM Sale s WHERE s.company.id = :companyId AND s.status = 'active' AND s.unpaidAmount > 0")
    long countUnpaidSalesByCompany(Long companyId);

    @Query("SELECT COALESCE(SUM(s.unpaidAmount),0) FROM Sale s WHERE s.company.id = :companyId AND s.status = 'active'")
    BigDecimal sumUnpaidByCompany(Long companyId);

    @Query("SELECT COUNT(s) FROM Sale s WHERE s.company.id = :companyId AND s.status = 'active' AND s.saleDate BETWEEN :from AND :to")
    long countByCompanyIdAndDateRange(Long companyId, LocalDateTime from, LocalDateTime to);

    boolean existsByCompanyIdAndSaleNumber(Long companyId, String saleNumber);

    long countByCompanyId(Long companyId);
}
