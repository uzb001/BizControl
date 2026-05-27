package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.LandedCostAllocation;

import java.util.List;

public interface LandedCostAllocationRepository extends JpaRepository<LandedCostAllocation, Long> {
    List<LandedCostAllocation> findByLogisticsOrderIdOrderByIdAsc(Long logisticsOrderId);
    List<LandedCostAllocation> findByCompanyIdAndProductIdOrderByCreatedAtDesc(Long companyId, Long productId);
    void deleteByLogisticsOrderId(Long logisticsOrderId);
}
