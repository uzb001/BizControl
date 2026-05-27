package uz.bizcontrol.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByCompanyIdOrderByCode(Long companyId);
    Optional<Account> findByCompanyIdAndCode(Long companyId, String code);
    boolean existsByCompanyId(Long companyId);
}
