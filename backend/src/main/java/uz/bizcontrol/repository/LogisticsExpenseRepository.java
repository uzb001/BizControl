package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.LogisticsExpense;

import java.util.List;

public interface LogisticsExpenseRepository extends JpaRepository<LogisticsExpense, Long> {
    List<LogisticsExpense> findByLogisticsOrderIdOrderByIdAsc(Long logisticsOrderId);
    void deleteByLogisticsOrderId(Long logisticsOrderId);
}
