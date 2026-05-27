package uz.bizcontrol.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.DailyClose;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyCloseRepository extends JpaRepository<DailyClose, Long> {
    Optional<DailyClose> findByCompanyIdAndCloseDate(Long companyId, LocalDate date);
    Optional<DailyClose> findByCompanyIdAndId(Long companyId, Long id);
    Page<DailyClose> findByCompanyIdOrderByCloseDateDesc(Long companyId, Pageable pageable);
    boolean existsByCompanyIdAndCloseDateAndStatus(Long companyId, LocalDate date, String status);
}
