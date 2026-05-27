package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.repository.CashTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes the deferred action stored in an {@link ApprovalRequest#getPendingActionPayload()}
 * once that request is approved.
 *
 * <p>Uses {@code @Lazy} injection for SaleService / PurchaseService / ProductService to
 * break potential circular-dependency chains, since those services depend on ApprovalService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeferredActionService {

    // Lazy to break circular: SaleService → ApprovalService → DeferredActionService → SaleService
    private final @Lazy SaleService saleService;
    private final @Lazy PurchaseService purchaseService;
    private final @Lazy ProductService productService;
    private final @Lazy WarehouseStockService warehouseStockService;
    private final CashTransactionRepository cashTransactionRepository;
    private final CompanyService companyService;
    private final AuditService auditService;

    /**
     * Dispatch the deferred action from an approved request.
     * No-op if {@code pendingActionPayload} is null.
     */
    @Transactional
    public void dispatch(ApprovalRequest req) {
        String payload = req.getPendingActionPayload();
        if (payload == null || payload.isBlank()) {
            log.debug("[DeferredAction] No payload for approval #{} — nothing to dispatch", req.getId());
            return;
        }

        Map<String, String> data = parseJson(payload);
        String action = data.get("action");
        if (action == null) {
            log.warn("[DeferredAction] Missing 'action' in payload for approval #{}", req.getId());
            return;
        }

        log.info("[DeferredAction] Dispatching action={} for approval #{}", action, req.getId());

        switch (action) {
            case "LARGE_EXPENSE"          -> executeLargeExpense(req, data);
            case "LARGE_STOCK_ADJUST"     -> executeLargeStockAdjust(req, data);
            case "WAREHOUSE_STOCK_ADJUST" -> executeWarehouseStockAdjust(req, data);
            case "SALE_CANCEL"            -> executeSaleCancel(req, data);
            case "PURCHASE_CANCEL"        -> executePurchaseCancel(req, data);
            default -> log.warn("[DeferredAction] Unknown action '{}' for approval #{}", action, req.getId());
        }
    }

    // ── Action handlers ──────────────────────────────────────────────────────

    private void executeLargeExpense(ApprovalRequest req, Map<String, String> d) {
        Long companyId  = Long.parseLong(d.get("companyId"));
        Long requesterId = Long.parseLong(d.get("requesterId"));
        BigDecimal amount = new BigDecimal(d.get("amount"));
        String paymentSource = d.getOrDefault("paymentSource", "cash");
        String transactionType = d.getOrDefault("transactionType", "expense");
        String currency  = d.getOrDefault("currency", "UZS");
        String category  = d.getOrDefault("category", "manual");
        String note      = d.getOrDefault("note", "Approved large expense");

        Company company = companyService.getById(companyId);

        CashTransaction tx = CashTransaction.builder()
                .company(company)
                .transactionType(transactionType)
                .paymentSource(paymentSource)
                .amount(amount)
                .currency(currency)
                .category(category)
                .transactionDate(LocalDateTime.now())
                .note(note + " [approved via approval #" + req.getId() + "]")
                .status("active")
                .createdBy(requesterId)
                .build();
        cashTransactionRepository.save(tx);

        // Update balance
        boolean isIncome = "income".equals(transactionType);
        if ("bank".equals(paymentSource) || "bank_transfer".equals(paymentSource)) {
            company.setBankBalance(isIncome
                    ? company.getBankBalance().add(amount)
                    : company.getBankBalance().subtract(amount));
        } else {
            company.setCashBalance(isIncome
                    ? company.getCashBalance().add(amount)
                    : company.getCashBalance().subtract(amount));
        }
        companyService.save(company);

        auditService.log(companyId, req.getApproverId(), "DEFERRED_EXEC", "CashTransaction", tx.getId(),
                null, "LARGE_EXPENSE approved: " + amount + " " + currency);
        log.info("[DeferredAction] LARGE_EXPENSE executed: txId={} amount={}", tx.getId(), amount);
    }

    private void executeLargeStockAdjust(ApprovalRequest req, Map<String, String> d) {
        Long companyId   = Long.parseLong(d.get("companyId"));
        Long requesterId = Long.parseLong(d.get("requesterId"));
        Long productId   = Long.parseLong(d.get("productId"));
        BigDecimal quantity = new BigDecimal(d.get("quantity"));
        String note = d.getOrDefault("note", "Approved stock adjustment");

        // Bypass the approval gate in adjustStock by calling the internal method
        StockMovement movement = productService.adjustStockDirectly(companyId, requesterId, productId, quantity,
                note + " [approved via approval #" + req.getId() + "]");

        auditService.log(companyId, req.getApproverId(), "DEFERRED_EXEC", "Product", productId,
                null, "LARGE_STOCK_ADJUST approved: qty=" + quantity);
        log.info("[DeferredAction] LARGE_STOCK_ADJUST executed: movementId={} qty={}", movement.getId(), quantity);
    }

    private void executeWarehouseStockAdjust(ApprovalRequest req, Map<String, String> d) {
        Long companyId   = Long.parseLong(d.get("companyId"));
        Long requesterId = Long.parseLong(d.get("requesterId"));
        Long productId   = Long.parseLong(d.get("productId"));
        Long warehouseId = Long.parseLong(d.get("warehouseId"));
        BigDecimal delta = new BigDecimal(d.get("delta"));
        String movementType = d.getOrDefault("movementType", "adjustment");
        String note = d.getOrDefault("note", "Approved warehouse stock adjustment");

        StockMovement movement = warehouseStockService.applyApprovedAdjust(
                companyId, requesterId, productId, warehouseId, delta, movementType,
                note + " [approved via approval #" + req.getId() + "]");

        auditService.log(companyId, req.getApproverId(), "DEFERRED_EXEC", "WarehouseStock", productId,
                null, "WAREHOUSE_STOCK_ADJUST approved: delta=" + delta);
        log.info("[DeferredAction] WAREHOUSE_STOCK_ADJUST executed: movementId={} delta={}", movement.getId(), delta);
    }

    private void executeSaleCancel(ApprovalRequest req, Map<String, String> d) {
        Long companyId   = Long.parseLong(d.get("companyId"));
        Long requesterId = Long.parseLong(d.get("requesterId"));
        Long saleId      = Long.parseLong(d.get("saleId"));

        saleService.cancelAndReverse(companyId, requesterId, saleId);

        auditService.log(companyId, req.getApproverId(), "DEFERRED_EXEC", "Sale", saleId,
                null, "SALE_CANCEL approved via approval #" + req.getId());
        log.info("[DeferredAction] SALE_CANCEL executed: saleId={}", saleId);
    }

    private void executePurchaseCancel(ApprovalRequest req, Map<String, String> d) {
        Long companyId   = Long.parseLong(d.get("companyId"));
        Long requesterId = Long.parseLong(d.get("requesterId"));
        Long purchaseId  = Long.parseLong(d.get("purchaseId"));

        purchaseService.cancelAndReverse(companyId, requesterId, purchaseId);

        auditService.log(companyId, req.getApproverId(), "DEFERRED_EXEC", "Purchase", purchaseId,
                null, "PURCHASE_CANCEL approved via approval #" + req.getId());
        log.info("[DeferredAction] PURCHASE_CANCEL executed: purchaseId={}", purchaseId);
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    /**
     * Minimal JSON → Map parser for the simple flat JSON blobs we store.
     * Does NOT handle nested objects or arrays — sufficient for our payloads.
     */
    public static Map<String, String> parseJson(String json) {
        Map<String, String> result = new ConcurrentHashMap<>();
        if (json == null) return result;
        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}"))   s = s.substring(0, s.length() - 1);
        for (String pair : s.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key   = kv[0].trim().replaceAll("\"", "");
                String value = kv[1].trim().replaceAll("\"", "");
                result.put(key, value);
            }
        }
        return result;
    }
}
