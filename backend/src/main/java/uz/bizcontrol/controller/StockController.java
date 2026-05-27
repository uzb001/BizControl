package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.Product;
import uz.bizcontrol.entity.StockMovement;
import uz.bizcontrol.repository.ProductRepository;
import uz.bizcontrol.repository.StockMovementRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;
import uz.bizcontrol.util.ProductSpec;

@RestController
@RequestMapping("/stock")
@RequiredArgsConstructor
public class StockController {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Page<Product>> listStock(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        permissionService.require(principal, "stock.view");
        var spec = ProductSpec.build(search, "active", categoryId, null, stockStatus);
        var withCompany = org.springframework.data.jpa.domain.Specification.where(
                (org.springframework.data.jpa.domain.Specification<Product>)
                        (root, q, cb) -> cb.equal(root.get("company").get("id"), principal.getCompanyId())
        ).and(spec);
        Page<Product> result = productRepository.findAll(withCompany, PageRequest.of(page, size, Sort.by("name")));

        // Mask purchase price if lacking permission
        if (!permissionService.hasPermission(principal, "products.view_purchase_price")) {
            result.getContent().forEach(p -> p.setPurchasePrice(null));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/movements")
    public ResponseEntity<Page<StockMovement>> listMovements(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        permissionService.require(principal, "stock.view_movements");
        return ResponseEntity.ok(stockMovementRepository.findByCompanyId(
                principal.getCompanyId(), PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }
}
