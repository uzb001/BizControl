package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import uz.bizcontrol.entity.Debt;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long>, JpaSpecificationExecutor<Debt> {
    Optional<Debt> findByCompanyIdAndRelatedSaleIdAndDebtType(Long companyId, Long saleId, String debtType);
    Optional<Debt> findByCompanyIdAndRelatedPurchaseIdAndDebtType(Long companyId, Long purchaseId, String debtType);
    List<Debt> findByCompanyIdAndDebtTypeAndStatus(Long companyId, String debtType, String status);

    @Query("SELECT COALESCE(SUM(d.remainingAmount),0) FROM Debt d WHERE d.company.id = :companyId AND d.debtType = :type AND d.status IN ('open','partial','overdue')")
    BigDecimal sumRemainingByCompanyAndType(Long companyId, String type);
}
