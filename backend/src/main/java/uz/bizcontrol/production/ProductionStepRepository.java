package uz.bizcontrol.production;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductionStepRepository extends JpaRepository<ProductionStep, Long> {
    List<ProductionStep> findByProductionOrderIdOrderBySortOrderAsc(Long productionOrderId);
}
