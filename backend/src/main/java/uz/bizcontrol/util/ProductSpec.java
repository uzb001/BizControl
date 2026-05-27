package uz.bizcontrol.util;

import org.springframework.data.jpa.domain.Specification;
import uz.bizcontrol.entity.Product;

public class ProductSpec {

    public static Specification<Product> build(String search, String status, Long categoryId, Long supplierId, String stockStatus) {
        Specification<Product> spec = Specification.where(null);

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("sku"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("barcode"), "")), like)
            ));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (supplierId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("supplier").get("id"), supplierId));
        }
        if (stockStatus != null) {
            spec = spec.and(switch (stockStatus) {
                case "out_of_stock" -> (root, q, cb) -> cb.equal(root.get("currentStock"), java.math.BigDecimal.ZERO);
                case "low_stock" -> (root, q, cb) -> cb.and(
                        cb.greaterThan(root.get("currentStock"), java.math.BigDecimal.ZERO),
                        cb.lessThanOrEqualTo(root.get("currentStock"), root.get("minStockLevel"))
                );
                case "in_stock" -> (root, q, cb) -> cb.greaterThan(root.get("currentStock"), root.get("minStockLevel"));
                default -> Specification.where(null);
            });
        }
        return spec;
    }
}
