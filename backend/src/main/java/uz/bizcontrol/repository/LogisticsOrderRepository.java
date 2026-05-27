package uz.bizcontrol.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import uz.bizcontrol.entity.LogisticsOrder;

import java.util.List;
import java.util.Optional;

public interface LogisticsOrderRepository
        extends JpaRepository<LogisticsOrder, Long>, JpaSpecificationExecutor<LogisticsOrder> {

    Optional<LogisticsOrder> findByCompanyIdAndId(Long companyId, Long id);
    Page<LogisticsOrder> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    List<LogisticsOrder> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, String status);

    @Query("SELECT COUNT(o) FROM LogisticsOrder o WHERE o.companyId = :companyId")
    long countByCompanyId(Long companyId);
}
