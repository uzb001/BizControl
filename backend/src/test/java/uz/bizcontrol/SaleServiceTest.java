package uz.bizcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.bizcontrol.dto.request.SaleRequest;
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
class SaleServiceTest {

    @Mock SaleRepository saleRepository;
    @Mock SaleItemRepository saleItemRepository;
    @Mock ProductRepository productRepository;
    @Mock CustomerRepository customerRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock DebtRepository debtRepository;
    @Mock CashTransactionRepository cashTransactionRepository;
    @Mock CompanyService companyService;
    @Mock AuditService auditService;
    @Mock AlertService alertService;
    @Mock ApprovalService approvalService;
    @Mock DailyCloseRepository dailyCloseRepository;
    @Mock WarehouseStockService warehouseStockService;
    @Mock uz.bizcontrol.accounting.AccountingService accountingService;

    @InjectMocks SaleService saleService;

    private Company company() {
        Company c = new Company();
        c.setId(1L);
        c.setCashBalance(new BigDecimal("1000000"));
        c.setBankBalance(new BigDecimal("500000"));
        return c;
    }

    private Product product(Long id, BigDecimal purchasePrice, BigDecimal sellingPrice, BigDecimal stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("Product " + id);
        p.setPurchasePrice(purchasePrice);
        p.setSellingPrice(sellingPrice);
        p.setCurrentStock(stock);
        p.setUnit("piece");
        p.setMinStockLevel(BigDecimal.ZERO);
        Company c = new Company(); c.setId(1L);
        p.setCompany(c);
        return p;
    }

    private Sale savedSale(Long id) {
        Sale s = new Sale();
        s.setId(id);
        s.setStatus("active");
        s.setDocStatus("draft");
        s.setSaleNumber("SL-20260101-0001");
        Company c = new Company(); c.setId(1L);
        s.setCompany(c);
        return s;
    }

    // ── Test: Negative quantity is rejected ───────────────────────────────────

    @Test
    void create_negativeQuantity_throwsBusinessException() {
        SaleRequest req = new SaleRequest();
        SaleRequest.SaleItemRequest item = new SaleRequest.SaleItemRequest();
        item.setProductId(1L);
        item.setQuantity(new BigDecimal("-1"));
        item.setSellingPrice(new BigDecimal("10000"));
        req.setItems(List.of(item));

        when(companyService.getById(1L)).thenReturn(company());
        Sale mockSale = savedSale(1L);
        when(saleRepository.save(any())).thenReturn(mockSale);
        when(productRepository.findByCompanyIdAndId(1L, 1L))
                .thenReturn(Optional.of(product(1L, new BigDecimal("8000"), new BigDecimal("10000"), new BigDecimal("100"))));

        assertThrows(BusinessException.class,
                () -> saleService.create(1L, 1L, req));
    }

    // ── Test: Insufficient stock is rejected ─────────────────────────────────

    @Test
    void create_insufficientStock_throwsBusinessException() {
        SaleRequest req = new SaleRequest();
        SaleRequest.SaleItemRequest item = new SaleRequest.SaleItemRequest();
        item.setProductId(1L);
        item.setQuantity(new BigDecimal("100"));
        item.setSellingPrice(new BigDecimal("10000"));
        req.setItems(List.of(item));

        when(companyService.getById(1L)).thenReturn(company());
        Sale mockSale = savedSale(1L);
        when(saleRepository.save(any())).thenReturn(mockSale);
        when(productRepository.findByCompanyIdAndId(1L, 1L))
                .thenReturn(Optional.of(product(1L, new BigDecimal("8000"), new BigDecimal("10000"), new BigDecimal("5"))));

        assertThrows(BusinessException.class,
                () -> saleService.create(1L, 1L, req));
    }

    // ── Test: Below-cost sale triggers approval ───────────────────────────────

    @Test
    void create_belowCostSale_triggersApprovalNotPosted() {
        SaleRequest req = new SaleRequest();
        SaleRequest.SaleItemRequest item = new SaleRequest.SaleItemRequest();
        item.setProductId(1L);
        item.setQuantity(new BigDecimal("1"));
        item.setSellingPrice(new BigDecimal("5000")); // below purchasePrice of 8000
        req.setItems(List.of(item));
        req.setPaidAmount(new BigDecimal("5000"));

        when(companyService.getById(1L)).thenReturn(company());
        Sale mockSale = savedSale(1L);
        when(saleRepository.save(any())).thenReturn(mockSale);
        when(productRepository.findByCompanyIdAndId(1L, 1L))
                .thenReturn(Optional.of(product(1L, new BigDecimal("8000"), new BigDecimal("10000"), new BigDecimal("10"))));
        when(saleItemRepository.saveAll(any())).thenReturn(List.of());

        ApprovalRequest ar = new ApprovalRequest();
        ar.setId(10L);
        when(approvalService.isSaleBelowCost(any(), any())).thenReturn(true);
        when(approvalService.request(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(ar);
        when(approvalService.buildMetadata(any())).thenReturn("{}");

        Sale result = saleService.create(1L, 1L, req);
        assertEquals("pending_approval", result.getDocStatus());
        verify(approvalService).request(eq(1L), eq(1L), eq("SALE_BELOW_COST"), eq("Sale"), any(), anyString(), anyString());
    }

    // ── Test: Cancel posted sale (public API) creates approval and throws ────────

    @Test
    void cancel_postedSale_requiresApprovalAndBlocks() {
        Product p = product(1L, new BigDecimal("8000"), new BigDecimal("10000"), new BigDecimal("9"));
        SaleItem si = new SaleItem();
        si.setProduct(p);
        si.setQuantity(new BigDecimal("1"));

        Sale sale = savedSale(1L);
        sale.setDocStatus("posted");
        sale.setStatus("active");
        sale.setTotalAmount(new BigDecimal("10000"));
        sale.setSaleDate(java.time.LocalDateTime.now());
        sale.setItems(List.of(si));

        when(saleRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(sale));
        when(approvalService.hasPendingFor(1L, "Sale", 1L)).thenReturn(false);

        ApprovalRequest ar = new ApprovalRequest(); ar.setId(99L);
        when(approvalService.requestDeferred(anyLong(), anyLong(), anyString(), anyString(),
                anyLong(), anyString(), anyString(), anyString())).thenReturn(ar);
        when(approvalService.buildMetadata(any())).thenReturn("{}");

        uz.bizcontrol.exception.PendingApprovalException ex =
                assertThrows(uz.bizcontrol.exception.PendingApprovalException.class,
                        () -> saleService.cancel(1L, 1L, 1L));
        assertEquals(99L, ex.getApprovalId());
        // Verify stock was NOT changed
        verify(productRepository, never()).save(any());
    }

    // ── Test: cancelAndReverse actually reverses stock and cash ──────────────

    @Test
    void cancelAndReverse_postedSale_reversesSideEffects() {
        Product p = product(1L, new BigDecimal("8000"), new BigDecimal("10000"), new BigDecimal("9"));
        SaleItem si = new SaleItem();
        si.setProduct(p);
        si.setQuantity(new BigDecimal("1"));

        Sale sale = savedSale(1L);
        sale.setDocStatus("posted");
        sale.setStatus("active");
        sale.setTotalAmount(new BigDecimal("10000"));
        sale.setPaidAmount(new BigDecimal("10000"));
        sale.setUnpaidAmount(BigDecimal.ZERO);
        sale.setItems(List.of(si));

        when(saleRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(sale));
        when(saleRepository.save(any())).thenReturn(sale);
        when(cashTransactionRepository.findBySaleIdAndStatusAndType(any(), any(), any(), any())).thenReturn(List.of());
        when(companyService.getById(1L)).thenReturn(company());

        Sale result = saleService.cancelAndReverse(1L, 1L, 1L);
        assertEquals("cancelled", result.getDocStatus());
        // Stock must be restored: 9 + 1 = 10
        verify(productRepository).save(argThat(prod -> prod.getCurrentStock().compareTo(new BigDecimal("10")) == 0));
        verify(auditService).log(eq(1L), eq(1L), eq("CANCEL"), eq("Sale"), eq(1L), anyString(), anyString());
    }

    // ── Test: cancel draft sale needs no approval ─────────────────────────────

    @Test
    void cancel_draftSale_immediatelyCancelled() {
        Sale sale = savedSale(1L);
        sale.setDocStatus("draft");
        sale.setStatus("active");
        sale.setItems(List.of());

        when(saleRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(sale));
        when(saleRepository.save(any())).thenReturn(sale);

        Sale result = saleService.cancel(1L, 1L, 1L);
        assertEquals("cancelled", result.getDocStatus());
        verify(approvalService, never()).requestDeferred(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
