package uz.bizcontrol.production;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long>,
        JpaSpecificationExecutor<ProductionOrder> {
    Page<ProductionOrder> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    List<ProductionOrder> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    Optional<ProductionOrder> findByCompanyIdAndId(Long companyId, Long id);
    long countByCompanyId(Long companyId);
    long countByCompanyIdAndStatus(Long companyId, String status);
    boolean existsByCompanyIdAndOrderNumber(Long companyId, String orderNumber);

    @Query("SELECT COUNT(o) FROM ProductionOrder o WHERE o.companyId = :cid AND " +
           "(o.sourceWarehouseId = :wid OR o.productionWarehouseId = :wid OR o.finishedGoodsWarehouseId = :wid)")
    long countUsingWarehouse(@Param("cid") Long cid, @Param("wid") Long wid);
}
