package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.ImportBatch;
import java.util.List;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    List<ImportBatch> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    Optional<ImportBatch> findByCompanyIdAndId(Long companyId, Long id);
}
