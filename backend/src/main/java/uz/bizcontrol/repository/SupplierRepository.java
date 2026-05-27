package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import uz.bizcontrol.entity.Supplier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {
    Optional<Supplier> findByCompanyIdAndId(Long companyId, Long id);
    List<Supplier> findByCompanyIdAndStatus(Long companyId, String status);
    boolean existsByCompanyIdAndName(Long companyId, String name);
    long countByCompanyIdAndCountryId(Long companyId, Long countryId);

    @Query("SELECT COALESCE(SUM(s.currentDebt),0) FROM Supplier s WHERE s.company.id = :companyId AND s.status != 'inactive'")
    BigDecimal sumTotalDebtByCompany(Long companyId);
}
