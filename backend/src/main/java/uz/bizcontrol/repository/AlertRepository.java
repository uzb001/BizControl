package uz.bizcontrol.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.Alert;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, String status);
    Page<Alert> findByCompanyId(Long companyId, Pageable pageable);
    long countByCompanyIdAndStatus(Long companyId, String status);
}
