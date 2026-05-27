package uz.bizcontrol.production;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductionCostRepository extends JpaRepository<ProductionCost, Long> {
    List<ProductionCost> findByProductionOrderId(Long productionOrderId);
}
