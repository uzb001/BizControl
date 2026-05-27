package uz.bizcontrol.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.dto.request.ProductRequest;
import uz.bizcontrol.entity.Product;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.entity.StockMovement;
import uz.bizcontrol.repository.ProductRepository;
import uz.bizcontrol.repository.StockMovementRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;
import uz.bizcontrol.service.ProductService;
import uz.bizcontrol.util.ProductSpec;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Page<Product>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        permissionService.require(principal, "products.view");
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);
        var spec = ProductSpec.build(search, status, categoryId, supplierId, stockStatus);
        Page<Product> result = productService.list(principal.getCompanyId(), spec, pageable);

        // Mask purchase price if lacking permission
        if (!permissionService.hasPermission(principal, "products.view_purchase_price")) {
            result.getContent().forEach(p -> p.setPurchasePrice(null));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Product> create(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @Valid @RequestBody ProductRequest req) {
        permissionService.require(principal, "products.create");
        return ResponseEntity.ok(productService.create(principal.getCompanyId(), principal.getUserId(), req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getOne(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "products.view");
        Product product = productService.getOne(principal.getCompanyId(), id);
        if (!permissionService.hasPermission(principal, "products.view_purchase_price")) {
            product.setPurchasePrice(null);
        }
        return ResponseEntity.ok(product);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest req) {
        permissionService.require(principal, "products.edit");
        return ResponseEntity.ok(productService.update(principal.getCompanyId(), principal.getUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "products.delete");
        productService.deactivate(principal.getCompanyId(), principal.getUserId(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> history(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "stock.view_movements");
        var movements = stockMovementRepository.findByCompanyIdAndProductIdOrderByCreatedAtDesc(
                principal.getCompanyId(), id);
        return ResponseEntity.ok(movements);
    }

    @PostMapping("/bulk-deactivate")
    public ResponseEntity<Map<String, Object>> bulkDeactivate(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestBody Map<String, Object> body) {
        permissionService.require(principal, "products.delete");
        @SuppressWarnings("unchecked")
        java.util.List<Integer> rawIds = (java.util.List<Integer>) body.get("ids");
        int count = 0;
        for (Integer rawId : rawIds) {
            try {
                productService.deactivate(principal.getCompanyId(), principal.getUserId(), rawId.longValue());
                count++;
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of("deactivated", count));
    }

    @PostMapping("/stock/adjust")
    public ResponseEntity<StockMovement> adjustStock(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestBody Map<String, Object> body) {
        permissionService.require(principal, "stock.adjust");
        if (body.get("productId") == null) throw new BusinessException("productId is required");
        if (body.get("quantity") == null) throw new BusinessException("quantity is required");
        Long productId = Long.valueOf(body.get("productId").toString());
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
        String note = body.getOrDefault("note", "").toString();
        // OWNER applies any size immediately; others have large adjustments gated by approval.
        return ResponseEntity.ok(productService.adjustStock(
                principal.getCompanyId(), principal.getUserId(), productId, quantity, note, principal.isOwner()));
    }
}
