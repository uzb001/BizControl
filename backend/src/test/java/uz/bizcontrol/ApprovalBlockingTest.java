package uz.bizcontrol;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.exception.PendingApprovalException;
import uz.bizcontrol.repository.*;
import uz.bizcontrol.service.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that blocking approval gates prevent side effects from being applied
 * before an approval is granted.
 *
 * Note: ApprovalService threshold methods are pure calculations — tested directly via
 * a real instance (no mocks needed). ProductService tests use a @Mock for ApprovalService.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalBlockingTest {

    // ── ApprovalService threshold checks (pure logic, no DB needed) ────────────

    /** Real instance: threshold methods have no repository dependencies. */
    private final ApprovalService thresholdChecker = new ApprovalService(null, null, null);

    @Test
    void largeExpense_threshold_isCorrect() {
        assertTrue(thresholdChecker.isLargeExpense(new BigDecimal("5000000")));
        assertTrue(thresholdChecker.isLargeExpense(new BigDecimal("10000000")));
        assertFalse(thresholdChecker.isLargeExpense(new BigDecimal("4999999")));
        assertFalse(thresholdChecker.isLargeExpense(BigDecimal.ZERO));
    }

    @Test
    void largeStockAdjust_threshold_isCorrect() {
        assertTrue(thresholdChecker.isLargeStockAdjust(new BigDecimal("100")));
        assertTrue(thresholdChecker.isLargeStockAdjust(new BigDecimal("-100")));
        assertFalse(thresholdChecker.isLargeStockAdjust(new BigDecimal("99")));
        assertFalse(thresholdChecker.isLargeStockAdjust(new BigDecimal("-99")));
    }

    @Test
    void highDiscount_threshold_isCorrect() {
        BigDecimal subtotal = new BigDecimal("100000");
        assertTrue(thresholdChecker.isHighDiscount(new BigDecimal("30000"), subtotal));   // exactly 30% — triggers
        assertTrue(thresholdChecker.isHighDiscount(new BigDecimal("35000"), subtotal));   // 35% — triggers
        assertFalse(thresholdChecker.isHighDiscount(new BigDecimal("29000"), subtotal));  // 29% — clearly below
        assertFalse(thresholdChecker.isHighDiscount(BigDecimal.ZERO, subtotal));
    }

    // ── ProductService: large stock adjust is blocked ─────────────────────────

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock CompanyService companyService;
    @Mock AuditService auditService;
    @Mock AlertService alertService;
    @Mock ApprovalService approvalService;   // mock for ProductService injection
    @Mock WarehouseStockService warehouseStockService;
    @InjectMocks ProductService productService;

    @Test
    void adjustStock_largeQuantity_throwsPendingApproval_stockUnchanged() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Laptop");
        product.setCurrentStock(new BigDecimal("50"));
        product.setMinStockLevel(BigDecimal.ZERO);
        Company c = new Company(); c.setId(1L);
        product.setCompany(c);

        when(productRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(product));
        when(approvalService.isLargeStockAdjust(new BigDecimal("200"))).thenReturn(true);
        when(approvalService.hasPendingFor(1L, "Product", 1L)).thenReturn(false);
        when(approvalService.buildMetadata(any())).thenReturn("{}");

        ApprovalRequest ar = new ApprovalRequest(); ar.setId(55L);
        when(approvalService.requestDeferred(anyLong(), anyLong(), anyString(), anyString(),
                anyLong(), anyString(), anyString(), anyString())).thenReturn(ar);

        PendingApprovalException ex = assertThrows(PendingApprovalException.class,
                () -> productService.adjustStock(1L, 1L, 1L, new BigDecimal("200"), "inventory count"));

        assertEquals(55L, ex.getApprovalId());
        // Critical: stock must NOT have changed
        assertEquals(new BigDecimal("50"), product.getCurrentStock());
        verify(productRepository, never()).save(any());
    }

    @Test
    void adjustStock_smallQuantity_appliesImmediately() {
        Product product = new Product();
        product.setId(2L);
        product.setName("Pen");
        product.setCurrentStock(new BigDecimal("100"));
        product.setMinStockLevel(BigDecimal.ZERO);
        Company c = new Company(); c.setId(1L);
        product.setCompany(c);

        when(productRepository.findByCompanyIdAndId(1L, 2L)).thenReturn(Optional.of(product));
        when(approvalService.isLargeStockAdjust(new BigDecimal("5"))).thenReturn(false);

        // Re-read for adjustStockDirectly (called after gate passes)
        when(productRepository.findByCompanyIdAndId(1L, 2L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);

        StockMovement movement = new StockMovement(); movement.setId(1L);
        when(stockMovementRepository.save(any())).thenReturn(movement);

        assertDoesNotThrow(() -> productService.adjustStock(1L, 1L, 2L, new BigDecimal("5"), "small top-up"));
        verify(productRepository).save(any());
    }

    @Test
    void adjustStock_removeStock_appliesImmediately() {
        Product p = new Product(); p.setId(5L); p.setName("Crate");
        p.setCurrentStock(new BigDecimal("50")); p.setMinStockLevel(BigDecimal.ZERO);
        Company c = new Company(); c.setId(1L); p.setCompany(c);
        when(productRepository.findByCompanyIdAndId(1L, 5L)).thenReturn(Optional.of(p));
        when(approvalService.isLargeStockAdjust(new BigDecimal("-20"))).thenReturn(false);
        when(productRepository.save(any())).thenReturn(p);
        when(stockMovementRepository.save(any())).thenReturn(new StockMovement());

        assertDoesNotThrow(() -> productService.adjustStock(1L, 1L, 5L, new BigDecimal("-20"), "sold offline"));
        assertEquals(0, p.getCurrentStock().compareTo(new BigDecimal("30")));   // 50 - 20 = 30
    }

    @Test
    void adjustStock_outExceedingStock_throwsNegative() {
        Product p = new Product(); p.setId(3L); p.setName("Box");
        p.setCurrentStock(new BigDecimal("10")); p.setMinStockLevel(BigDecimal.ZERO);
        Company c = new Company(); c.setId(1L); p.setCompany(c);
        when(productRepository.findByCompanyIdAndId(1L, 3L)).thenReturn(Optional.of(p));

        uz.bizcontrol.exception.BusinessException ex = assertThrows(uz.bizcontrol.exception.BusinessException.class,
                () -> productService.adjustStock(1L, 1L, 3L, new BigDecimal("-20"), "oversell"));
        assertTrue(ex.getMessage().toLowerCase().contains("negative"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void adjustStock_ownerBypassesLargeApproval_appliesImmediately() {
        Product p = new Product(); p.setId(4L); p.setName("Sack");
        p.setCurrentStock(BigDecimal.ZERO); p.setMinStockLevel(BigDecimal.ZERO);
        Company c = new Company(); c.setId(1L); p.setCompany(c);
        when(productRepository.findByCompanyIdAndId(1L, 4L)).thenReturn(Optional.of(p));
        when(productRepository.save(any())).thenReturn(p);
        when(stockMovementRepository.save(any())).thenReturn(new StockMovement());

        // allowLarge=true → no approval gate even for 1000 units
        assertDoesNotThrow(() -> productService.adjustStock(1L, 1L, 4L, new BigDecimal("1000"), "initial stock", true));
        assertEquals(0, p.getCurrentStock().compareTo(new BigDecimal("1000")));
        verify(approvalService, never()).requestDeferred(anyLong(), anyLong(), anyString(), anyString(),
                anyLong(), anyString(), anyString(), anyString());
    }

    // ── DeferredActionService JSON parser ─────────────────────────────────────

    @Test
    void deferredActionService_parseJson_extractsValues() {
        String json = "{\"action\":\"LARGE_EXPENSE\",\"companyId\":\"1\",\"amount\":\"6000000.00\",\"currency\":\"UZS\"}";
        Map<String, String> map = DeferredActionService.parseJson(json);
        assertEquals("LARGE_EXPENSE", map.get("action"));
        assertEquals("1", map.get("companyId"));
        assertEquals("6000000.00", map.get("amount"));
        assertEquals("UZS", map.get("currency"));
    }

    // ── MoneyTransactionRequest DTO validation ────────────────────────────────

    private static final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void moneyTransactionRequest_negativeAmount_failsValidation() {
        var req = new uz.bizcontrol.dto.request.MoneyTransactionRequest();
        req.setTransactionType("expense");
        req.setAmount(new BigDecimal("-500"));
        var violations = validator.validate(req);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("amount")),
                "Expected violation on 'amount' for negative value");
    }

    @Test
    void moneyTransactionRequest_zeroAmount_failsValidation() {
        var req = new uz.bizcontrol.dto.request.MoneyTransactionRequest();
        req.setTransactionType("income");
        req.setAmount(BigDecimal.ZERO);
        var violations = validator.validate(req);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("amount")),
                "Expected violation on 'amount' for zero value");
    }

    @Test
    void moneyTransactionRequest_validExpense_passesValidation() {
        var req = new uz.bizcontrol.dto.request.MoneyTransactionRequest();
        req.setTransactionType("expense");
        req.setPaymentSource("cash");
        req.setAmount(new BigDecimal("50000"));
        var violations = validator.validate(req);
        assertTrue(violations.isEmpty(),
                "Expected no violations for a valid expense request, got: " + violations);
    }

    @Test
    void moneyTransactionRequest_invalidTransactionType_failsValidation() {
        var req = new uz.bizcontrol.dto.request.MoneyTransactionRequest();
        req.setTransactionType("transfer"); // invalid
        req.setAmount(new BigDecimal("10000"));
        var violations = validator.validate(req);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("transactionType")));
    }
}
