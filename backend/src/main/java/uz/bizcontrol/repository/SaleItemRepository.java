package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.bizcontrol.entity.SaleItem;

import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    @Query("SELECT i FROM SaleItem i WHERE i.sale.id = :saleId")
    List<SaleItem> findBySaleId(@Param("saleId") Long saleId);
}
