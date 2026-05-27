package uz.bizcontrol.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.bizcontrol.entity.StockMovement;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {
    Page<StockMovement> findByCompanyId(Long companyId, Pageable pageable);
    Page<StockMovement> findByCompanyIdAndWarehouseId(Long companyId, Long warehouseId, Pageable pageable);
    List<StockMovement> findByCompanyIdAndProductIdOrderByCreatedAtDesc(Long companyId, Long productId);
    List<StockMovement> findByReferenceIdAndReferenceType(Long referenceId, String referenceType);
}
