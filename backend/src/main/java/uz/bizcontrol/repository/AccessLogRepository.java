package uz.bizcontrol.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.AccessLog;
import java.util.List;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {
    Page<AccessLog> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    List<AccessLog> findByCompanyIdAndUserIdOrderByCreatedAtDesc(Long companyId, Long userId);
    List<AccessLog> findByCompanyIdAndResultOrderByCreatedAtDesc(Long companyId, String result);
}
