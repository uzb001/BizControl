package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.bizcontrol.entity.WarehouseStock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {

    Optional<WarehouseStock> findByWarehouseIdAndProductId(Long warehouseId, Long productId);

    List<WarehouseStock> findByCompanyIdAndWarehouseId(Long companyId, Long warehouseId);

    List<WarehouseStock> findByCompanyIdAndProductId(Long companyId, Long productId);

    List<WarehouseStock> findByCompanyId(Long companyId);

    @Query("SELECT COALESCE(SUM(ws.quantity),0) FROM WarehouseStock ws WHERE ws.warehouseId = :warehouseId")
    BigDecimal sumQuantityByWarehouse(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COALESCE(SUM(ws.quantity),0) FROM WarehouseStock ws WHERE ws.productId = :productId")
    BigDecimal sumQuantityByProduct(@Param("productId") Long productId);

    long countByWarehouseId(Long warehouseId);

    @Query("SELECT COUNT(ws) FROM WarehouseStock ws WHERE ws.warehouseId = :warehouseId AND ws.quantity > 0")
    long countPositiveByWarehouse(@Param("warehouseId") Long warehouseId);
}
