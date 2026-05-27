package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.LogisticsExpensePayment;

import java.util.List;

public interface LogisticsExpensePaymentRepository extends JpaRepository<LogisticsExpensePayment, Long> {
    List<LogisticsExpensePayment> findByLogisticsOrderIdOrderByPaidAtAsc(Long logisticsOrderId);
    List<LogisticsExpensePayment> findByLogisticsExpenseIdOrderByPaidAtAsc(Long logisticsExpenseId);
}
