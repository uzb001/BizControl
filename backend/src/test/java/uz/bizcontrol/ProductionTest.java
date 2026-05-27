package uz.bizcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uz.bizcontrol.accounting.Account;
import uz.bizcontrol.accounting.ChartOfAccountsService;
import uz.bizcontrol.accounting.JournalLine;
import uz.bizcontrol.accounting.JournalService;
import uz.bizcontrol.entity.Product;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.production.*;
import uz.bizcontrol.repository.ProductRepository;
import uz.bizcontrol.service.AlertService;
import uz.bizcontrol.service.AuditService;
import uz.bizcontrol.service.WarehouseStockService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductionTest {

    // ── BOM math ─────────────────────────────────────────────────────────────
    @Mock BomTemplateRepository bomTemplateRepository;
    @Mock BomComponentRepository bomComponentRepository;
    @Mock ProductRepository productRepository;
    @Mock AuditService auditService;
    @InjectMocks BomService bomService;

    private Product prod(Long id, BigDecimal cost) {
        Product p = new Product(); p.setId(id); p.setName("P" + id); p.setUnit("kg");
        p.setPurchasePrice(cost); p.setMinStockLevel(BigDecimal.ZERO); p.setCurrentStock(BigDecimal.ZERO);
        var c = new uz.bizcontrol.entity.Company(); c.setId(1L); p.setCompany(c);
        return p;
    }

    @Test
    void computeRequired_appliesWastePercent() {
        BomTemplate t = BomTemplate.builder().id(1L).companyId(1L).productId(99L)
                .outputQuantity(BigDecimal.ONE).build();
        BomComponent c = BomComponent.builder().id(1L).bomTemplateId(1L).componentProductId(5L)
                .quantity(new BigDecimal("2")).unit("kg").wastePercent(new BigDecimal("5")).build();
        when(bomComponentRepository.findByBomTemplateId(1L)).thenReturn(List.of(c));
        when(productRepository.findByCompanyIdAndId(1L, 5L)).thenReturn(Optional.of(prod(5L, new BigDecimal("10000"))));

        // produce 10 → 2 * 10 * 1.05 = 21
        List<ProductionOrderComponent> req = bomService.computeRequired(1L, t, new BigDecimal("10"), 7L);
        assertEquals(1, req.size());
        assertEquals(0, req.get(0).getRequiredQuantity().compareTo(new BigDecimal("21")));
        assertEquals(0, req.get(0).getWasteQuantity().compareTo(new BigDecimal("1")));
        assertEquals(0, req.get(0).getTotalCost().compareTo(new BigDecimal("210000")));
    }

    // ── Production completion ────────────────────────────────────────────────
    @Mock ProductionOrderRepository orderRepository;
    @Mock ProductionOrderComponentRepository componentRepository;
    @Mock ProductionStepRepository stepRepository;
    @Mock ProductionCostRepository costRepository;
    @Mock WasteRecordRepository wasteRepository;
    @Mock BomService bomServiceMock;
    @Mock WarehouseStockService warehouseStockService;
    @Mock ChartOfAccountsService chart;
    @Mock JournalService journalService;
    @Mock AlertService alertService;
    @InjectMocks ProductionOrderService orderService;

    private ProductionOrder order(String status) {
        return ProductionOrder.builder().id(50L).companyId(1L).orderNumber("PR-1").productId(99L)
                .plannedQuantity(new BigDecimal("10")).completedQuantity(BigDecimal.ZERO)
                .status(status).sourceWarehouseId(7L).finishedGoodsWarehouseId(8L)
                .totalCost(BigDecimal.ZERO).costPerUnit(BigDecimal.ZERO).build();
    }

    private ProductionOrderComponent comp() {
        return ProductionOrderComponent.builder().id(1L).productionOrderId(50L).productId(5L).warehouseId(7L)
                .requiredQuantity(new BigDecimal("21")).consumedQuantity(BigDecimal.ZERO)
                .unit("kg").unitCost(new BigDecimal("10000")).totalCost(new BigDecimal("210000"))
                .wastePercent(new BigDecimal("5")).wasteQuantity(new BigDecimal("1")).build();
    }

    private void stubCommon() {
        when(componentRepository.findByProductionOrderId(50L)).thenReturn(List.of(comp()));
        when(productRepository.findByCompanyIdAndId(1L, 5L)).thenReturn(Optional.of(prod(5L, new BigDecimal("10000"))));
        when(productRepository.findByCompanyIdAndId(1L, 99L)).thenReturn(Optional.of(prod(99L, new BigDecimal("0"))));
        when(costRepository.findByProductionOrderId(50L)).thenReturn(List.of());
        when(stepRepository.findByProductionOrderIdOrderBySortOrderAsc(50L)).thenReturn(List.of());
        when(warehouseStockService.resolveWarehouseId(eq(1L), any())).thenReturn(8L);
        when(chart.require(eq(1L), anyString())).thenReturn(acct(1L));
        when(chart.ensureAccount(eq(1L), anyString(), anyString(), anyString(), anyString())).thenReturn(acct(2L));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(componentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Account acct(Long id) { Account a = new Account(); a.setId(id); return a; }

    @Test
    void complete_consumesRawAddsFinishedAndPostsBalancedJournal() {
        when(orderRepository.findByCompanyIdAndId(1L, 50L)).thenReturn(Optional.of(order("in_progress")));
        stubCommon();
        when(warehouseStockService.availableInWarehouse(1L, 7L, 5L)).thenReturn(new BigDecimal("100"));

        ProductionOrder result = orderService.complete(1L, 1L, 50L);
        assertEquals("completed", result.getStatus());

        // consume (negative) + output (positive) both go through the warehouse engine
        verify(warehouseStockService).applyApprovedAdjust(eq(1L), eq(1L), eq(5L), eq(7L),
                argThat(d -> d.compareTo(new BigDecimal("-21")) == 0), eq("production_consume"), anyString());
        verify(warehouseStockService).applyApprovedAdjust(eq(1L), eq(1L), eq(99L), eq(8L),
                argThat(d -> d.compareTo(new BigDecimal("10")) == 0), eq("production_output"), anyString());

        // a balanced journal entry is posted
        ArgumentCaptor<List<JournalLine>> cap = ArgumentCaptor.forClass(List.class);
        verify(journalService).post(eq(1L), eq(1L), any(), eq("PRODUCTION"), eq(50L), anyString(), cap.capture());
        BigDecimal debit = cap.getValue().stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = cap.getValue().stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, debit.compareTo(credit), "journal must balance");
        assertEquals(0, debit.compareTo(new BigDecimal("210000")), "debit total = raw+added");
    }

    @Test
    void complete_insufficientMaterial_blocks() {
        when(orderRepository.findByCompanyIdAndId(1L, 50L)).thenReturn(Optional.of(order("in_progress")));
        when(componentRepository.findByProductionOrderId(50L)).thenReturn(List.of(comp()));
        when(productRepository.findByCompanyIdAndId(1L, 5L)).thenReturn(Optional.of(prod(5L, new BigDecimal("10000"))));
        when(warehouseStockService.availableInWarehouse(1L, 7L, 5L)).thenReturn(new BigDecimal("5")); // need 21

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.complete(1L, 1L, 50L));
        assertTrue(ex.getMessage().toLowerCase().contains("insufficient"));
        verify(journalService, never()).post(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void complete_wrongStatus_blocks() {
        when(orderRepository.findByCompanyIdAndId(1L, 50L)).thenReturn(Optional.of(order("draft")));
        assertThrows(BusinessException.class, () -> orderService.complete(1L, 1L, 50L));
    }
}
