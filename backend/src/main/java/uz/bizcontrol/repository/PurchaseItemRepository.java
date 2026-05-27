package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.bizcontrol.entity.PurchaseItem;

import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {
    @Query("SELECT i FROM PurchaseItem i WHERE i.purchase.id = :purchaseId")
    List<PurchaseItem> findByPurchaseId(@Param("purchaseId") Long purchaseId);
}
