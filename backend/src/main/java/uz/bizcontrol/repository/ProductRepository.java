package uz.bizcontrol.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.bizcontrol.entity.Product;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Page<Product> findByCompanyId(Long companyId, Pageable pageable);
    List<Product> findByCompanyIdAndStatus(Long companyId, String status);
    Optional<Product> findByCompanyIdAndId(Long companyId, Long id);
    boolean existsByCompanyIdAndSku(Long companyId, String sku);
    boolean existsByCompanyIdAndName(Long companyId, String name);

    @Query("SELECT p FROM Product p WHERE p.company.id = :companyId AND p.currentStock <= p.minStockLevel AND p.currentStock > 0 AND p.status = 'active'")
    List<Product> findLowStockProducts(Long companyId);

    @Query("SELECT p FROM Product p WHERE p.company.id = :companyId AND p.currentStock = 0 AND p.status = 'active'")
    List<Product> findOutOfStockProducts(Long companyId);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.company.id = :companyId AND p.currentStock <= p.minStockLevel AND p.status = 'active'")
    long countLowStockByCompany(Long companyId);

    // Dead stock: products with no sale movements since a given date
    @Query("""
        SELECT p FROM Product p
        WHERE p.company.id = :companyId
          AND p.status = 'active'
          AND p.currentStock > 0
          AND p.id NOT IN (
              SELECT DISTINCT si.product.id FROM SaleItem si
              JOIN si.sale s
              WHERE s.company.id = :companyId
                AND s.status = 'active'
                AND s.saleDate >= :since
          )
        ORDER BY p.currentStock DESC
        """)
    List<Product> findDeadStockSince(@Param("companyId") Long companyId, @Param("since") LocalDateTime since);

    long countByCompanyIdAndStatus(Long companyId, String status);

    @Query("SELECT SUM(p.currentStock * p.purchasePrice) FROM Product p WHERE p.company.id = :companyId AND p.status = 'active'")
    java.math.BigDecimal sumStockValue(Long companyId);
}
