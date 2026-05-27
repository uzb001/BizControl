package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import uz.bizcontrol.entity.Purchase;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>, JpaSpecificationExecutor<Purchase> {
    Optional<Purchase> findByCompanyIdAndId(Long companyId, Long id);

    @Query("SELECT COALESCE(SUM(p.totalAmount),0) FROM Purchase p WHERE p.company.id = :companyId AND p.status = 'active' AND p.purchaseDate BETWEEN :from AND :to")
    BigDecimal sumTotalByCompanyAndDateRange(Long companyId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(p) FROM Purchase p WHERE p.company.id = :companyId AND p.status = 'active' AND p.unpaidAmount > 0")
    long countUnpaidPurchasesByCompany(Long companyId);

    boolean existsByCompanyIdAndPurchaseNumber(Long companyId, String purchaseNumber);

    long countByCompanyId(Long companyId);
}
