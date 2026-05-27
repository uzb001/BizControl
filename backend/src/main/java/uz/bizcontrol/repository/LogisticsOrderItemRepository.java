package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.LogisticsOrderItem;

import java.util.List;

public interface LogisticsOrderItemRepository extends JpaRepository<LogisticsOrderItem, Long> {
    List<LogisticsOrderItem> findByLogisticsOrderIdOrderByIdAsc(Long logisticsOrderId);
    void deleteByLogisticsOrderId(Long logisticsOrderId);
    long countByLogisticsOrderId(Long logisticsOrderId);
}
