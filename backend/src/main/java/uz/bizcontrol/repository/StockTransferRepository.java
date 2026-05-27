package uz.bizcontrol.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.StockTransfer;

import java.util.List;
import java.util.Optional;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    Page<StockTransfer> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    List<StockTransfer> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    Optional<StockTransfer> findByCompanyIdAndId(Long companyId, Long id);
    long countByFromWarehouseIdOrToWarehouseId(Long fromWarehouseId, Long toWarehouseId);
}
