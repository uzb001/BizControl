package uz.bizcontrol.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.dto.request.PurchaseRequest;
import uz.bizcontrol.dto.response.PurchaseDetailResponse;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.entity.Purchase;
import uz.bizcontrol.repository.CashTransactionRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;
import uz.bizcontrol.service.PurchaseService;
import uz.bizcontrol.util.PurchaseSpec;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final CashTransactionRepository cashTransactionRepository;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Page<Purchase>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "purchaseDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        permissionService.require(principal, "purchases.view");
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        var spec = PurchaseSpec.build(search, supplierId, paymentStatus, paymentMethod, fromDate, toDate);
        return ResponseEntity.ok(purchaseService.list(principal.getCompanyId(), spec, PageRequest.of(page, size, sort)));
    }

    @PostMapping
    public ResponseEntity<Purchase> create(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @Valid @RequestBody PurchaseRequest req) {
        permissionService.require(principal, "purchases.create");
        return ResponseEntity.ok(purchaseService.create(principal.getCompanyId(), principal.getUserId(), req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseDetailResponse> getOne(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "purchases.view");
        Purchase purchase = purchaseService.getOne(principal.getCompanyId(), id);
        var payments = cashTransactionRepository.findByPurchaseId(principal.getCompanyId(), id);
        return ResponseEntity.ok(PurchaseDetailResponse.from(purchase, payments));
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<Purchase> post(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "purchases.create");
        return ResponseEntity.ok(purchaseService.post(principal.getCompanyId(), principal.getUserId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Purchase> edit(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody PurchaseRequest req) {
        permissionService.require(principal, "purchases.edit");
        return ResponseEntity.ok(purchaseService.edit(principal.getCompanyId(), principal.getUserId(), id, req));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Purchase> cancel(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "purchases.cancel");
        return ResponseEntity.ok(purchaseService.cancel(principal.getCompanyId(), principal.getUserId(), id));
    }

    @PostMapping("/{id}/payment")
    public ResponseEntity<Purchase> addPayment(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        permissionService.require(principal, "purchases.add_payment");
        if (body.get("amount") == null) throw new BusinessException("amount is required");
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String method = body.getOrDefault("paymentMethod", "cash").toString();
        String note   = body.getOrDefault("note", "").toString();
        return ResponseEntity.ok(purchaseService.addPayment(
                principal.getCompanyId(), principal.getUserId(), id, amount, method, note));
    }
}
