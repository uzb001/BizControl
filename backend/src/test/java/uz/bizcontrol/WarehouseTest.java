package uz.bizcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.*;
import uz.bizcontrol.service.*;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the multi-warehouse stock engine. Verifies the core invariant
 * (product total = sum of warehouse stock) and the four operations
 * (IN / OUT / SET / TRANSFER) plus the guard rails.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseTest {

    @Mock WarehouseRepository warehouseRepository;
    @Mock WarehouseStockRepository warehouseStockRepository;
    @Mock StockTransferRepository stockTransferRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock ProductRepository productRepository;
    @Mock CompanyService companyService;
    @Mock AuditService auditService;
    @Mock AlertService alertService;
    @Mock ApprovalService approvalService;

    @InjectMocks WarehouseStockService stockService;

    // WarehouseService for archive guard tests
    @InjectMocks WarehouseService warehouseService;

    private Warehouse wh(Long id, String code, String status) {
        Warehouse w = Warehouse.builder().id(id).name(code).code(code).type("main").status(status).build();
        Company c = new Company(); c.setId(1L); w.setCompany(c);
        return w;
    }

    private Product product(Long id, BigDecimal stock) {
        Product p = new Product();
        p.setId(id); p.setName("Product " + id); p.setUnit("kg");
        p.setPurchasePrice(new BigDecimal("10000"));
        p.setMinStockLevel(BigDecimal.ZERO);
        p.setCurrentStock(stock);
        Company c = new Company(); c.setId(1L); p.setCompany(c);
        return p;
    }

    private WarehouseStock row(Long whId, Long productId, BigDecimal qty) {
        return WarehouseStock.builder()
                .id(1L).companyId(1L).warehouseId(whId).productId(productId)
                .quantity(qty).reservedQuantity(BigDecimal.ZERO).build();
    }

    // ── IN ────────────────────────────────────────────────────────────────────
    @Test
    void stockIn_increasesWarehouseAndProductTotal() {
        Warehouse w = wh(5L, "MAIN", "active");
        Product p = product(1L, new BigDecimal("100"));
        WarehouseStock r = row(5L, 1L, new BigDecimal("100"));

        when(warehouseRepository.findByCompanyIdAndId(1L, 5L)).thenReturn(Optional.of(w));
        when(productRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(p));
        when(warehouseStockRepository.findByWarehouseIdAndProductId(5L, 1L)).thenReturn(Optional.of(r));
        when(warehouseStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(approvalService.isLargeStockAdjust(any())).thenReturn(false);

        stockService.stockIn(1L, 1L, 1L, 5L, new BigDecimal("40"), "restock", false);

        assertEquals(0, r.getQuantity().compareTo(new BigDecimal("140")));      // warehouse row
        assertEquals(0, p.getCurrentStock().compareTo(new BigDecimal("140")));  // product total — invariant
        verify(auditService).log(eq(1L), eq(1L), eq("WAREHOUSE_STOCK_IN"), eq("WarehouseStock"), eq(1L), anyString(), anyString());
    }

    // ── OUT ───────────────────────────────────────────────────────────────────
    @Test
    void stockOut_decreasesBoth() {
        Warehouse w = wh(5L, "MAIN", "active");
        Product p = product(1L, new BigDecimal("100"));
        WarehouseStock r = row(5L, 1L, new BigDecimal("100"));

        when(warehouseRepository.findByCompanyIdAndId(1L, 5L)).thenReturn(Optional.of(w));
        when(productRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(p));
        when(warehouseStockRepository.findByWarehouseIdAndProductId(5L, 1L)).thenReturn(Optional.of(r));
        when(warehouseStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(approvalService.isLargeStockAdjust(any())).thenReturn(false);

        stockService.stockOut(1L, 1L, 1L, 5L, new BigDecimal("30"), "sold offline", false, false);

        assertEquals(0, r.getQuantity().compareTo(new BigDecimal("70")));
        assertEquals(0, p.getCurrentStock().compareTo(new BigDecimal("70")));
    }

    @Test
    void stockOut_exceedingAvailable_throws() {
        Warehouse w = wh(5L, "MAIN", "active");
        WarehouseStock r = row(5L, 1L, new BigDecimal("10"));
        when(warehouseStockRepository.findByWarehouseIdAndProductId(5L, 1L)).thenReturn(Optional.of(r));

        assertThrows(BusinessException.class,
                () -> stockService.stockOut(1L, 1L, 1L, 5L, new BigDecimal("50"), "oversell", false, false));
    }

    // ── SET ───────────────────────────────────────────────────────────────────
    @Test
    void stockSet_setsExactQuantity() {
        Warehouse w = wh(5L, "MAIN", "active");
        Product p = product(1L, new BigDecimal("100"));
        WarehouseStock r = row(5L, 1L, new BigDecimal("100"));

        when(warehouseRepository.findByCompanyIdAndId(1L, 5L)).thenReturn(Optional.of(w));
        when(productRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(p));
        when(warehouseStockRepository.findByWarehouseIdAndProductId(5L, 1L)).thenReturn(Optional.of(r));
        when(warehouseStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(approvalService.isLargeStockAdjust(any())).thenReturn(false);

        // set to 250 from 100 → delta +150 (but allowLarge=true to bypass approval gate)
        stockService.stockSet(1L, 1L, 1L, 5L, new BigDecimal("250"), "inventory count", true);

        assertEquals(0, r.getQuantity().compareTo(new BigDecimal("250")));
        assertEquals(0, p.getCurrentStock().compareTo(new BigDecimal("250")));
    }

    // ── TRANSFER ────────────────────────────────────────────────────────────────
    @Test
    void transfer_movesBetweenWarehouses_totalUnchanged() {
        Warehouse from = wh(5L, "MAIN", "active");
        Warehouse to = wh(6L, "STORE", "active");
        Product p = product(1L, new BigDecimal("100"));
        WarehouseStock fromRow = row(5L, 1L, new BigDecimal("100"));
        WarehouseStock toRow = row(6L, 1L, new BigDecimal("0"));

        when(warehouseRepository.findByCompanyIdAndId(1L, 5L)).thenReturn(Optional.of(from));
        when(warehouseRepository.findByCompanyIdAndId(1L, 6L)).thenReturn(Optional.of(to));
        when(productRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(p));
        when(warehouseStockRepository.findByWarehouseIdAndProductId(5L, 1L)).thenReturn(Optional.of(fromRow));
        when(warehouseStockRepository.findByWarehouseIdAndProductId(6L, 1L)).thenReturn(Optional.of(toRow));
        when(warehouseStockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stockTransferRepository.save(any())).thenAnswer(i -> { StockTransfer t = i.getArgument(0); t.setId(77L); return t; });

        stockService.transfer(1L, 1L, 1L, 5L, 6L, new BigDecimal("30"), "to store");

        assertEquals(0, fromRow.getQuantity().compareTo(new BigDecimal("70")));
        assertEquals(0, toRow.getQuantity().compareTo(new BigDecimal("30")));
        // Product total must NOT change on a transfer (still 100); product never saved
        assertEquals(0, p.getCurrentStock().compareTo(new BigDecimal("100")));
        verify(productRepository, never()).save(any());
        verify(auditService).log(eq(1L), eq(1L), eq("STOCK_TRANSFER"), eq("StockTransfer"), eq(77L), anyString(), anyString());
    }

    @Test
    void transfer_exceedingAvailable_throws() {
        Warehouse from = wh(5L, "MAIN", "active");
        Warehouse to = wh(6L, "STORE", "active");
        Product p = product(1L, new BigDecimal("10"));
        WarehouseStock fromRow = row(5L, 1L, new BigDecimal("10"));

        when(warehouseRepository.findByCompanyIdAndId(1L, 5L)).thenReturn(Optional.of(from));
        when(warehouseRepository.findByCompanyIdAndId(1L, 6L)).thenReturn(Optional.of(to));
        when(productRepository.findByCompanyIdAndId(1L, 1L)).thenReturn(Optional.of(p));
        when(warehouseStockRepository.findByWarehouseIdAndProductId(5L, 1L)).thenReturn(Optional.of(fromRow));

        assertThrows(BusinessException.class,
                () -> stockService.transfer(1L, 1L, 1L, 5L, 6L, new BigDecimal("50"), "too much"));
    }

    @Test
    void transfer_sameWarehouse_throws() {
        assertThrows(BusinessException.class,
                () -> stockService.transfer(1L, 1L, 1L, 5L, 5L, new BigDecimal("5"), "noop"));
    }

    // ── ARCHIVE GUARD ───────────────────────────────────────────────────────────
    @Test
    void archive_warehouseWithStock_isBlocked() {
        Warehouse w = wh(5L, "MAIN", "active");
        when(warehouseRepository.findByCompanyIdAndId(1L, 5L)).thenReturn(Optional.of(w));
        when(warehouseStockRepository.countPositiveByWarehouse(5L)).thenReturn(3L);

        assertThrows(BusinessException.class, () -> warehouseService.archive(1L, 1L, 5L));
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void archive_emptyWarehouse_succeeds() {
        Warehouse w = wh(5L, "OLD", "active");
        when(warehouseRepository.findByCompanyIdAndId(1L, 5L)).thenReturn(Optional.of(w));
        when(warehouseStockRepository.countPositiveByWarehouse(5L)).thenReturn(0L);
        when(warehouseRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        warehouseService.archive(1L, 1L, 5L);
        assertEquals("archived", w.getStatus());
    }
}
