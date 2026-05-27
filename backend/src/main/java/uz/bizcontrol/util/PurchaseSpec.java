package uz.bizcontrol.util;

import org.springframework.data.jpa.domain.Specification;
import uz.bizcontrol.entity.Purchase;
import java.time.LocalDateTime;

public class PurchaseSpec {

    public static Specification<Purchase> build(String search, Long supplierId, String paymentStatus,
                                                  String paymentMethod, String fromDate, String toDate) {
        Specification<Purchase> spec = Specification.where(
                (root, q, cb) -> cb.notEqual(root.get("status"), "cancelled"));

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("purchaseNumber")), like));
        }
        if (supplierId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("supplier").get("id"), supplierId));
        }
        if (paymentStatus != null) {
            spec = spec.and(switch (paymentStatus) {
                case "paid" -> (root, q, cb) -> cb.equal(root.get("unpaidAmount"), java.math.BigDecimal.ZERO);
                case "unpaid" -> (root, q, cb) -> cb.greaterThan(root.get("unpaidAmount"), java.math.BigDecimal.ZERO);
                default -> Specification.where(null);
            });
        }
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("paymentMethod"), paymentMethod));
        }
        if (fromDate != null && !fromDate.isBlank()) {
            LocalDateTime from = LocalDateTime.parse(fromDate + "T00:00:00");
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("purchaseDate"), from));
        }
        if (toDate != null && !toDate.isBlank()) {
            LocalDateTime to = LocalDateTime.parse(toDate + "T23:59:59");
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("purchaseDate"), to));
        }
        return spec;
    }
}
