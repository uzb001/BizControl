package uz.bizcontrol.util;

import org.springframework.data.jpa.domain.Specification;
import uz.bizcontrol.entity.Sale;
import java.time.LocalDateTime;

public class SaleSpec {

    public static Specification<Sale> build(String search, Long customerId, String paymentStatus,
                                             String paymentMethod, String fromDate, String toDate) {
        Specification<Sale> spec = Specification.where(null);
        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("saleNumber")), like));
        }
        if (customerId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("customer").get("id"), customerId));
        }
        if (paymentStatus != null) {
            spec = spec.and(switch (paymentStatus) {
                case "paid" -> (root, q, cb) -> cb.equal(root.get("unpaidAmount"), java.math.BigDecimal.ZERO);
                case "unpaid" -> (root, q, cb) -> cb.greaterThan(root.get("unpaidAmount"), java.math.BigDecimal.ZERO);
                case "partial" -> (root, q, cb) -> cb.and(
                        cb.greaterThan(root.get("paidAmount"), java.math.BigDecimal.ZERO),
                        cb.greaterThan(root.get("unpaidAmount"), java.math.BigDecimal.ZERO)
                );
                default -> Specification.where(null);
            });
        }
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("paymentMethod"), paymentMethod));
        }
        if (fromDate != null && !fromDate.isBlank()) {
            LocalDateTime from = LocalDateTime.parse(fromDate + "T00:00:00");
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("saleDate"), from));
        }
        if (toDate != null && !toDate.isBlank()) {
            LocalDateTime to = LocalDateTime.parse(toDate + "T23:59:59");
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("saleDate"), to));
        }
        // Exclude cancelled by default
        spec = spec.and((root, q, cb) -> cb.notEqual(root.get("status"), "cancelled"));
        return spec;
    }
}
