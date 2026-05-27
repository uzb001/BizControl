package uz.bizcontrol.production;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WasteRecordRepository extends JpaRepository<WasteRecord, Long> {
    List<WasteRecord> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<WasteRecord> findByProductionOrderId(Long productionOrderId);
}
