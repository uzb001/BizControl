package uz.bizcontrol.production;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface ProductionOrderComponentRepository extends JpaRepository<ProductionOrderComponent, Long> {
    List<ProductionOrderComponent> findByProductionOrderId(Long productionOrderId);
    @Transactional
    void deleteByProductionOrderId(Long productionOrderId);
}
