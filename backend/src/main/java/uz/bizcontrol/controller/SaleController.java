package uz.bizcontrol.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.dto.request.SaleRequest;
import uz.bizcontrol.dto.response.SaleDetailResponse;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.entity.Sale;
import uz.bizcontrol.repository.CashTransactionRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;
import uz.bizcontrol.service.SaleService;
import uz.bizcontrol.util.SaleSpec;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;
    private final CashTransactionRepository cashTransactionRepository;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Page<Sale>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "saleDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        permissionService.require(principal, "sales.view");
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        var spec = SaleSpec.build(search, customerId, paymentStatus, paymentMethod, fromDate, toDate);
        Page<Sale> result = saleService.list(principal.getCompanyId(), spec, PageRequest.of(page, size, sort));
        if (!permissionService.hasPermission(principal, "sales.view_profit"))
            result.getContent().forEach(s -> s.setProfitMasked(true));
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Sale> create(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @Valid @RequestBody SaleRequest req) {
        permissionService.require(principal, "sales.create");
        return ResponseEntity.ok(saleService.create(principal.getCompanyId(), principal.getUserId(), req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleDetailResponse> getOne(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "sales.view");
        Sale sale = saleService.getOne(principal.getCompanyId(), id);
        var payments = cashTransactionRepository.findBySaleId(principal.getCompanyId(), id);
        SaleDetailResponse response = SaleDetailResponse.from(sale, payments);
        if (!permissionService.hasPermission(principal, "sales.view_profit")) {
            response.setTotalProfit(null);
            if (response.getItems() != null) response.getItems().forEach(item -> {
                item.setProfitAmount(null);
                item.setPurchaseCost(null);
            });
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<Sale> post(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "sales.create");
        return ResponseEntity.ok(saleService.post(principal.getCompanyId(), principal.getUserId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Sale> edit(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SaleRequest req) {
        permissionService.require(principal, "sales.edit");
        return ResponseEntity.ok(saleService.edit(principal.getCompanyId(), principal.getUserId(), id, req));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Sale> cancel(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "sales.cancel");
        return ResponseEntity.ok(saleService.cancel(principal.getCompanyId(), principal.getUserId(), id));
    }

    @PostMapping("/{id}/payment")
    public ResponseEntity<Sale> addPayment(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        permissionService.require(principal, "sales.add_payment");
        if (body.get("amount") == null) throw new BusinessException("amount is required");
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String method = body.getOrDefault("paymentMethod", "cash").toString();
        String note   = body.getOrDefault("note", "").toString();
        return ResponseEntity.ok(saleService.addPayment(
                principal.getCompanyId(), principal.getUserId(), id, amount, method, note));
    }
}
