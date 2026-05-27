package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import uz.bizcontrol.entity.Customer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findByCompanyIdAndId(Long companyId, Long id);
    List<Customer> findByCompanyIdAndStatus(Long companyId, String status);
    boolean existsByCompanyIdAndName(Long companyId, String name);

    @Query("SELECT COALESCE(SUM(c.currentDebt),0) FROM Customer c WHERE c.company.id = :companyId AND c.status != 'inactive'")
    BigDecimal sumTotalDebtByCompany(Long companyId);
}
