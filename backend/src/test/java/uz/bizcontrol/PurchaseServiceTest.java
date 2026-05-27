package uz.bizcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.bizcontrol.dto.request.PurchaseRequest;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.*;
import uz.bizcontrol.service.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock PurchaseRepository purchaseRepository;
    @Mock PurchaseItemRepository purchaseItemRepository;
    @Mock ProductRepository productRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock DebtRepository debtRepository;
    @Mock CashTransactionRepository cashTransactionRepository;
    @Mock CompanyService companyService;
    @Mock AuditService auditService;
    @Mock ApprovalService approvalService;
    @Mock DailyCloseRepository dailyCloseRepository;
    @Mock WarehouseStockService warehouseStockService;
    @Mock uz.bizcontrol.accounting.AccountingService accountingService;

    @InjectMocks PurchaseService purchaseService;

    private Company company() {
        Company c = new Company(); c.setId(1L);
        c.setCashBalance(BigDecimal.valueOf(1_000_000));
        c.setBankBalance(BigDecimal.valueOf(500_000));
        return c;
    }

    private Purchase savedPurchase(Long id) {
        Purchase p = new Purchase(); p.setId(id);
        p.setStatus("active"); p.setDocStatus("draft");
        p.setPurchaseNumber("PO-20260101-0001");
        Company c = new Company(); c.setId(1L);
        p.setCompany(c);
        return p;
    }

    @Test
    void create_negativeQuantity_throwsBusinessException() {
        PurchaseRequest req = new PurchaseRequest();
        PurchaseRequest.PurchaseItemRequest item = new PurchaseRequest.PurchaseItemRequest();
        item.setProductId(1L);
        item.setQuantity(new BigDecimal("-5"));
        item.setPurchasePrice(new BigDecimal("10000"));
        req.setItems(List.of(item));

        when(companyService.getById(1L)).thenReturn(company());
        when(purchaseRepository.save(any())).thenReturn(savedPurchase(1L));
        Product p = new Product(); p.setId(1L); p.setName("Test");
        p.setCurrentStock(BigDecimal.TEN); p.setPurchasePrice(BigDecimal.valueOf(10000));
        Company c = new Company(); c.setId(1L); p.setCompany(c);
        when(productRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(p));

        assertThrows(BusinessException.class, () -> purchaseService.create(1L, 1L, req));
    }

    @Test
    void cancel_lockedPurchase_throwsBusinessException() {
        Purchase purchase = savedPurchase(1L);
        purchase.setLocked(true);
        when(purchaseRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(purchase));
        assertThrows(BusinessException.class, () -> purchaseService.cancel(1L, 1L, 1L));
    }

    @Test
    void cancel_postedPurchase_requiresApprovalAndDoesNotReverse() {
        Purchase purchase = savedPurchase(1L);
        purchase.setDocStatus("posted");
        purchase.setStatus("active");
        purchase.setTotalAmount(BigDecimal.valueOf(500_000));
        purchase.setPurchaseDate(java.time.LocalDateTime.now());
        purchase.setItems(List.of());

        when(purchaseRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(purchase));
        when(approvalService.hasPendingFor(1L, "Purchase", 1L)).thenReturn(false);
        ApprovalRequest ar = new ApprovalRequest(); ar.setId(77L);
        when(approvalService.requestDeferred(anyLong(), anyLong(), anyString(), anyString(),
                anyLong(), anyString(), anyString(), anyString())).thenReturn(ar);
        when(approvalService.buildMetadata(any())).thenReturn("{}");

        uz.bizcontrol.exception.PendingApprovalException ex =
                assertThrows(uz.bizcontrol.exception.PendingApprovalException.class,
                        () -> purchaseService.cancel(1L, 1L, 1L));
        assertEquals(77L, ex.getApprovalId());
        verify(productRepository, never()).save(any());
    }

    @Test
    void cancelAndReverse_postedPurchase_reversesSideEffects() {
        Product p = new Product(); p.setId(1L); p.setName("Widget");
        p.setCurrentStock(BigDecimal.valueOf(10)); p.setPurchasePrice(BigDecimal.valueOf(5000));
        Company c = new Company(); c.setId(1L); p.setCompany(c);

        PurchaseItem pi = new PurchaseItem();
        pi.setProduct(p);
        pi.setQuantity(BigDecimal.valueOf(3));

        Purchase purchase = savedPurchase(1L);
        purchase.setDocStatus("posted");
        purchase.setStatus("active");
        purchase.setTotalAmount(BigDecimal.valueOf(15000));
        purchase.setItems(List.of(pi));

        when(purchaseRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(any())).thenReturn(purchase);
        when(cashTransactionRepository.findByPurchaseIdAndStatusAndType(any(), any(), any(), any())).thenReturn(List.of());
        when(companyService.getById(1L)).thenReturn(company());

        Purchase result = purchaseService.cancelAndReverse(1L, 1L, 1L);
        assertEquals("cancelled", result.getDocStatus());
        // Stock must be removed: 10 - 3 = 7
        verify(productRepository).save(argThat(prod -> prod.getCurrentStock().compareTo(BigDecimal.valueOf(7)) == 0));
    }

    @Test
    void addPayment_cancelledPurchase_throwsBusinessException() {
        Purchase purchase = savedPurchase(1L);
        purchase.setStatus("cancelled");
        when(purchaseRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(purchase));
        assertThrows(BusinessException.class,
                () -> purchaseService.addPayment(1L, 1L, 1L, BigDecimal.valueOf(1000), "cash", "test"));
    }
}
