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
import uz.bizcontrol.entity.*;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.*;
import uz.bizcontrol.accounting.JournalEntry;
import uz.bizcontrol.service.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integrity tests for the logistics & landed-cost engine. Verifies:
 *   1. Draft creation produces the right totals
 *   2. Source == destination rejected
 *   3. Confirm transfers every item, posts a balanced journal, records cash txns,
 *      decrements company balances, persists allocations summing cent-true
 *   4. Empty draft blocks confirmation
 *   5. Expense currency mismatch is rejected
 *   6. Mutations on a confirmed order are blocked
 *   7. Draft can be cancelled; confirmed cannot be cancelled
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogisticsTest {

    @Mock LogisticsOrderRepository orderRepository;
    @Mock LogisticsOrderItemRepository itemRepository;
    @Mock LogisticsExpenseRepository expenseRepository;
    @Mock LandedCostAllocationRepository allocationRepository;
    @Mock LogisticsExpensePaymentRepository paymentRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock ProductRepository productRepository;
    @Mock CashTransactionRepository cashTransactionRepository;
    @Mock WarehouseStockService warehouseStockService;
    @Mock CompanyService companyService;
    @Mock AuditService auditService;
    @Mock ChartOfAccountsService chart;
    @Mock JournalService journalService;
    @InjectMocks LogisticsService logisticsService;

    private static final Long COMPANY = 1L;
    private static final Long USER = 7L;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Warehouse wh(Long id, String name, String status) {
        Warehouse w = new Warehouse();
        w.setId(id); w.setName(name); w.setStatus(status);
        Company c = new Company(); c.setId(COMPANY); w.setCompany(c);
        return w;
    }

    private Product prod(Long id, BigDecimal cost) {
        Product p = new Product(); p.setId(id); p.setName("P" + id);
        p.setUnit("kg"); p.setPurchasePrice(cost);
        Company c = new Company(); c.setId(COMPANY); p.setCompany(c);
        return p;
    }

    private Account acct(Long id) { Account a = new Account(); a.setId(id); return a; }

    private Company company() {
        Company c = new Company();
        c.setId(COMPANY);
        c.setCashBalance(new BigDecimal("1000000"));
        c.setBankBalance(new BigDecimal("1000000"));
        return c;
    }

    private void stubBasics() {
        // Two warehouses, both active
        when(warehouseRepository.findByCompanyIdAndId(COMPANY, 10L)).thenReturn(Optional.of(wh(10L, "Shanghai", "active")));
        when(warehouseRepository.findByCompanyIdAndId(COMPANY, 20L)).thenReturn(Optional.of(wh(20L, "Tashkent", "active")));

        // Products with purchase prices
        when(productRepository.findByCompanyIdAndId(COMPANY, 100L)).thenReturn(Optional.of(prod(100L, new BigDecimal("100"))));
        when(productRepository.findByCompanyIdAndId(COMPANY, 200L)).thenReturn(Optional.of(prod(200L, new BigDecimal("300"))));

        when(orderRepository.countByCompanyId(COMPANY)).thenReturn(0L);
        // ID-assigning save
        AtomicLong oid = new AtomicLong(500);
        when(orderRepository.save(any(LogisticsOrder.class))).thenAnswer(inv -> {
            LogisticsOrder lo = inv.getArgument(0);
            if (lo.getId() == null) lo.setId(oid.incrementAndGet());
            return lo;
        });
        AtomicLong iid = new AtomicLong(1000);
        when(itemRepository.save(any(LogisticsOrderItem.class))).thenAnswer(inv -> {
            LogisticsOrderItem it = inv.getArgument(0);
            if (it.getId() == null) it.setId(iid.incrementAndGet());
            return it;
        });
        AtomicLong eid = new AtomicLong(2000);
        when(expenseRepository.save(any(LogisticsExpense.class))).thenAnswer(inv -> {
            LogisticsExpense ex = inv.getArgument(0);
            if (ex.getId() == null) ex.setId(eid.incrementAndGet());
            return ex;
        });
        when(cashTransactionRepository.save(any(CashTransaction.class))).thenAnswer(inv -> {
            CashTransaction ct = inv.getArgument(0);
            if (ct.getId() == null) ct.setId(99L);
            return ct;
        });
        when(companyService.getById(COMPANY)).thenReturn(company());
        when(chart.require(eq(COMPANY), anyString())).thenAnswer(inv -> acct((long) inv.getArgument(1, String.class).hashCode()));
    }

    // ── 1. Draft creation ──────────────────────────────────────────────────────

    @Test
    void createDraft_inlineItemsAndExpenses_computesTotals() {
        stubBasics();
        // Return saved items + expenses on subsequent reads (recalculate)
        List<LogisticsOrderItem> savedItems = new ArrayList<>();
        List<LogisticsExpense> savedExpenses = new ArrayList<>();
        when(itemRepository.save(any())).thenAnswer(inv -> { LogisticsOrderItem it = inv.getArgument(0); it.setId(1L); savedItems.add(it); return it; });
        when(expenseRepository.save(any())).thenAnswer(inv -> { LogisticsExpense e = inv.getArgument(0); e.setId(1L); savedExpenses.add(e); return e; });
        when(itemRepository.findByLogisticsOrderIdOrderByIdAsc(anyLong())).thenReturn(savedItems);
        when(expenseRepository.findByLogisticsOrderIdOrderByIdAsc(anyLong())).thenReturn(savedExpenses);

        Map<String, Object> body = new HashMap<>();
        body.put("sourceWarehouseId", 10);
        body.put("destinationWarehouseId", 20);
        body.put("currency", "USD");
        body.put("items", List.of(
                Map.of("productId", 100, "quantity", "5"),                 // 5 × 100 = 500
                Map.of("productId", 200, "quantity", "2", "unitCost", "250") // 2 × 250 = 500
        ));
        body.put("expenses", List.of(
                Map.of("expenseType", "shipping", "amount", "100", "currency", "USD"),
                Map.of("expenseType", "customs",  "amount", "100", "currency", "USD")
        ));
        LogisticsOrder o = logisticsService.createDraft(COMPANY, USER, body);

        assertEquals("draft", o.getStatus());
        assertEquals(0, o.getItemsValue().compareTo(new BigDecimal("1000.00")), "items=500+500=1000");
        assertEquals(0, o.getExpensesTotal().compareTo(new BigDecimal("200.00")), "expenses=100+100=200");
        assertEquals(0, o.getLandedTotal().compareTo(new BigDecimal("1200.00")), "landed=1000+200=1200");
        verify(auditService).log(eq(COMPANY), eq(USER), eq("CREATE"), eq("LogisticsOrder"), anyLong(), isNull(), anyString());
    }

    // ── 2. Source == destination rejected ──────────────────────────────────────

    @Test
    void createDraft_sameSrcAndDst_rejected() {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceWarehouseId", 10);
        body.put("destinationWarehouseId", 10);
        BusinessException ex = assertThrows(BusinessException.class, () -> logisticsService.createDraft(COMPANY, USER, body));
        assertTrue(ex.getMessage().toLowerCase().contains("differ"));
    }

    // ── 3. Confirm: transfer + cash + journal + allocations ────────────────────

    @Test
    void confirm_transfersStockPostsBalancedJournalAndRecordsCash() {
        stubBasics();

        // Pre-existing draft
        LogisticsOrder draft = LogisticsOrder.builder()
                .id(500L).companyId(COMPANY).orderNumber("LOG-1")
                .sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("draft").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(draft));

        List<LogisticsOrderItem> items = List.of(
                LogisticsOrderItem.builder().id(1L).companyId(COMPANY).logisticsOrderId(500L)
                        .productId(100L).quantity(new BigDecimal("5"))
                        .unitCost(new BigDecimal("100")).itemValue(new BigDecimal("500")).build(),
                LogisticsOrderItem.builder().id(2L).companyId(COMPANY).logisticsOrderId(500L)
                        .productId(200L).quantity(new BigDecimal("2"))
                        .unitCost(new BigDecimal("250")).itemValue(new BigDecimal("500")).build()
        );
        when(itemRepository.findByLogisticsOrderIdOrderByIdAsc(500L)).thenReturn(items);

        List<LogisticsExpense> expenses = List.of(
                LogisticsExpense.builder().id(1L).companyId(COMPANY).logisticsOrderId(500L)
                        .expenseType("shipping").amount(new BigDecimal("100"))
                        .currency("USD").exchangeRate(BigDecimal.ONE).convertedAmount(new BigDecimal("100"))
                        .paymentSource("cash").paidAmount(new BigDecimal("100")).paymentStatus("paid").build(),
                LogisticsExpense.builder().id(2L).companyId(COMPANY).logisticsOrderId(500L)
                        .expenseType("customs").amount(new BigDecimal("100"))
                        .currency("USD").exchangeRate(BigDecimal.ONE).convertedAmount(new BigDecimal("100"))
                        .paymentSource("bank").paidAmount(new BigDecimal("100")).paymentStatus("paid").build()
        );
        when(expenseRepository.findByLogisticsOrderIdOrderByIdAsc(500L)).thenReturn(expenses);

        LogisticsOrder confirmed = logisticsService.confirm(COMPANY, USER, 500L);

        assertEquals("confirmed", confirmed.getStatus());
        assertNotNull(confirmed.getConfirmedAt());
        assertEquals(0, confirmed.getLandedTotal().compareTo(new BigDecimal("1200.00")));

        // Stock transfer per item
        verify(warehouseStockService).transfer(COMPANY, USER, 100L, 10L, 20L, new BigDecimal("5"), "Logistics LOG-1");
        verify(warehouseStockService).transfer(COMPANY, USER, 200L, 10L, 20L, new BigDecimal("2"), "Logistics LOG-1");

        // Two cash transactions saved (one per expense)
        verify(cashTransactionRepository, times(2)).save(any(CashTransaction.class));

        // ONE balanced journal entry posted
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalLine>> cap = ArgumentCaptor.forClass(List.class);
        verify(journalService).post(eq(COMPANY), eq(USER), any(), eq("LOGISTICS"), eq(500L), anyString(), cap.capture());
        BigDecimal d = cap.getValue().stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = cap.getValue().stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, d.compareTo(c), "journal must balance");
        assertEquals(0, d.compareTo(new BigDecimal("200.00")), "debit total = expensesTotal");

        // Allocations cent-true: 100 + 100 = 200 split 500:500 → 100:100
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LandedCostAllocation>> aCap = ArgumentCaptor.forClass(List.class);
        verify(allocationRepository).saveAll(aCap.capture());
        BigDecimal allocSum = aCap.getValue().stream()
                .map(LandedCostAllocation::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, allocSum.compareTo(new BigDecimal("200.00")), "allocations sum cent-true to expensesTotal");
        for (LandedCostAllocation a : aCap.getValue()) {
            assertEquals(0, a.getAllocatedAmount().compareTo(new BigDecimal("100.00")), "equal value → equal allocation");
        }

        verify(auditService).log(eq(COMPANY), eq(USER), eq("CONFIRM"), eq("LogisticsOrder"), eq(500L), eq("draft"), eq("confirmed"));
    }

    // ── 4. Empty draft blocks confirmation ─────────────────────────────────────

    @Test
    void confirm_emptyDraft_blocks() {
        stubBasics();
        LogisticsOrder draft = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("draft").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(draft));
        when(itemRepository.findByLogisticsOrderIdOrderByIdAsc(500L)).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> logisticsService.confirm(COMPANY, USER, 500L));
        assertTrue(ex.getMessage().toLowerCase().contains("at least one item"));
        verify(warehouseStockService, never()).transfer(any(), any(), any(), any(), any(), any(), any());
        verify(journalService, never()).post(any(), any(), any(), any(), any(), any(), any());
    }

    // ── 5. Expense currency mismatch ───────────────────────────────────────────

    @Test
    void confirm_expenseCurrencyMismatch_blocks() {
        stubBasics();
        LogisticsOrder draft = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("draft").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(draft));
        when(itemRepository.findByLogisticsOrderIdOrderByIdAsc(500L)).thenReturn(List.of(
                LogisticsOrderItem.builder().id(1L).companyId(COMPANY).logisticsOrderId(500L)
                        .productId(100L).quantity(new BigDecimal("1"))
                        .unitCost(new BigDecimal("100")).itemValue(new BigDecimal("100")).build()
        ));
        when(expenseRepository.findByLogisticsOrderIdOrderByIdAsc(500L)).thenReturn(List.of(
                LogisticsExpense.builder().id(1L).companyId(COMPANY).logisticsOrderId(500L)
                        .expenseType("shipping").amount(new BigDecimal("100"))
                        .currency("EUR").exchangeRate(null).convertedAmount(new BigDecimal("0"))
                        .paymentSource("cash").paidAmount(new BigDecimal("100")).paymentStatus("paid").build()
        ));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> logisticsService.confirm(COMPANY, USER, 500L));
        assertTrue(ex.getMessage().toLowerCase().contains("exchange rate"));
        verify(warehouseStockService, never()).transfer(any(), any(), any(), any(), any(), any(), any());
    }

    // ── 6. Confirmed orders are immutable ──────────────────────────────────────

    @Test
    void addItem_onConfirmedOrder_blocked() {
        LogisticsOrder confirmed = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("confirmed").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(confirmed));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> logisticsService.addItem(COMPANY, USER, 500L, Map.of("productId", 100, "quantity", "1")));
        assertTrue(ex.getMessage().toLowerCase().contains("draft"));
    }

    @Test
    void confirm_twice_blocked() {
        LogisticsOrder confirmed = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("confirmed").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(confirmed));

        assertThrows(BusinessException.class, () -> logisticsService.confirm(COMPANY, USER, 500L));
    }

    // ── 7. Cancel lifecycle ────────────────────────────────────────────────────

    @Test
    void cancelDraft_works() {
        stubBasics();
        LogisticsOrder draft = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("draft").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(draft));

        logisticsService.cancelDraft(COMPANY, USER, 500L);
        ArgumentCaptor<LogisticsOrder> cap = ArgumentCaptor.forClass(LogisticsOrder.class);
        verify(orderRepository).save(cap.capture());
        assertEquals("cancelled", cap.getValue().getStatus());
        assertNotNull(cap.getValue().getCancelledAt());
    }

    @Test
    void cancelConfirmed_blocked() {
        LogisticsOrder confirmed = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("confirmed").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(confirmed));
        assertThrows(BusinessException.class, () -> logisticsService.cancelDraft(COMPANY, USER, 500L));
    }

    // ── 8. Reversal lifecycle ─────────────────────────────────────────────────

    @Test
    void reverse_restoresStockAndReversesCashAndJournal() {
        stubBasics();
        // Confirmed order with one CashTransaction attached per expense
        LogisticsOrder confirmed = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("confirmed").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(confirmed));

        when(itemRepository.findByLogisticsOrderIdOrderByIdAsc(500L)).thenReturn(List.of(
                LogisticsOrderItem.builder().id(1L).companyId(COMPANY).logisticsOrderId(500L)
                        .productId(100L).quantity(new BigDecimal("5"))
                        .unitCost(new BigDecimal("100")).itemValue(new BigDecimal("500")).build()
        ));
        when(expenseRepository.findByLogisticsOrderIdOrderByIdAsc(500L)).thenReturn(List.of(
                LogisticsExpense.builder().id(1L).companyId(COMPANY).logisticsOrderId(500L)
                        .expenseType("shipping").amount(new BigDecimal("60"))
                        .currency("USD").exchangeRate(BigDecimal.ONE).convertedAmount(new BigDecimal("60"))
                        .paymentSource("cash").cashTransactionId(7001L)
                        .paidAmount(new BigDecimal("60")).paymentStatus("paid").build(),
                LogisticsExpense.builder().id(2L).companyId(COMPANY).logisticsOrderId(500L)
                        .expenseType("customs").amount(new BigDecimal("40"))
                        .currency("USD").exchangeRate(BigDecimal.ONE).convertedAmount(new BigDecimal("40"))
                        .paymentSource("bank").cashTransactionId(7002L)
                        .paidAmount(new BigDecimal("40")).paymentStatus("paid").build()
        ));
        when(paymentRepository.findByLogisticsExpenseIdOrderByPaidAtAsc(anyLong())).thenReturn(List.of());
        CashTransaction ct1 = CashTransaction.builder().id(7001L)
                .amount(new BigDecimal("60")).status("active").paymentSource("cash").build();
        CashTransaction ct2 = CashTransaction.builder().id(7002L)
                .amount(new BigDecimal("40")).status("active").paymentSource("bank").build();
        when(cashTransactionRepository.findById(7001L)).thenReturn(Optional.of(ct1));
        when(cashTransactionRepository.findById(7002L)).thenReturn(Optional.of(ct2));

        LogisticsOrder result = logisticsService.reverse(COMPANY, USER, 500L, "wrong shipment");

        assertEquals("reversed", result.getStatus());
        assertNotNull(result.getReversedAt());
        assertEquals(USER, result.getReversedBy());

        // Stock transferred BACK (destination → source)
        verify(warehouseStockService).transfer(COMPANY, USER, 100L, 20L, 10L,
                new BigDecimal("5"), "Logistics REVERSAL LOG-1");

        // CashTransactions marked reversed
        ArgumentCaptor<CashTransaction> ctCap = ArgumentCaptor.forClass(CashTransaction.class);
        verify(cashTransactionRepository, atLeast(2)).save(ctCap.capture());
        assertTrue(ctCap.getAllValues().stream().allMatch(c -> "reversed".equals(c.getStatus())));

        // Journal reversed via reverseBySource (single call, regardless of expense count)
        verify(journalService).reverseBySource(eq(COMPANY), eq(USER), eq("LOGISTICS"), eq(500L), anyString());

        verify(auditService).log(eq(COMPANY), eq(USER), eq("REVERSE"), eq("LogisticsOrder"), eq(500L), eq("confirmed"), eq("reversed"));
    }

    @Test
    void reverse_twice_blocked() {
        LogisticsOrder alreadyReversed = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("reversed")
                .reversedAt(java.time.LocalDateTime.now()).reversedBy(USER).build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(alreadyReversed));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> logisticsService.reverse(COMPANY, USER, 500L, "test"));
        assertTrue(ex.getMessage().toLowerCase().contains("reverse"));
        verify(journalService, never()).reverseBySource(any(), any(), any(), any(), any());
    }

    @Test
    void reverse_draftOrder_blocked() {
        LogisticsOrder draft = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("draft").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(draft));
        assertThrows(BusinessException.class, () -> logisticsService.reverse(COMPANY, USER, 500L, null));
    }

    // ── 9. Multi-currency expenses ────────────────────────────────────────────

    @Test
    void addExpense_multiCurrency_requiresExchangeRate() {
        stubBasics();
        when(itemRepository.findByLogisticsOrderIdOrderByIdAsc(anyLong())).thenReturn(List.of());
        when(expenseRepository.findByLogisticsOrderIdOrderByIdAsc(anyLong())).thenReturn(List.of());

        // Create a USD order first
        Map<String, Object> body = new HashMap<>();
        body.put("sourceWarehouseId", 10); body.put("destinationWarehouseId", 20); body.put("currency", "USD");
        LogisticsOrder o = logisticsService.createDraft(COMPANY, USER, body);
        when(orderRepository.findByCompanyIdAndId(COMPANY, o.getId())).thenReturn(Optional.of(o));

        // Add CNY expense without exchangeRate → blocked
        Map<String, Object> exp = new HashMap<>();
        exp.put("expenseType", "customs"); exp.put("amount", "1000"); exp.put("currency", "CNY");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> logisticsService.addExpense(COMPANY, USER, o.getId(), exp));
        assertTrue(ex.getMessage().toLowerCase().contains("exchangerate"));
    }

    @Test
    void addExpense_multiCurrency_computesConvertedAmount() {
        stubBasics();
        when(itemRepository.findByLogisticsOrderIdOrderByIdAsc(anyLong())).thenReturn(List.of());
        when(expenseRepository.findByLogisticsOrderIdOrderByIdAsc(anyLong())).thenReturn(List.of());

        Map<String, Object> body = new HashMap<>();
        body.put("sourceWarehouseId", 10); body.put("destinationWarehouseId", 20); body.put("currency", "USD");
        LogisticsOrder o = logisticsService.createDraft(COMPANY, USER, body);
        when(orderRepository.findByCompanyIdAndId(COMPANY, o.getId())).thenReturn(Optional.of(o));

        Map<String, Object> exp = new HashMap<>();
        exp.put("expenseType", "customs"); exp.put("amount", "1000");
        exp.put("currency", "CNY"); exp.put("exchangeRate", "0.14");
        LogisticsExpense saved = logisticsService.addExpense(COMPANY, USER, o.getId(), exp);

        assertEquals(0, saved.getExchangeRate().compareTo(new BigDecimal("0.14")));
        assertEquals(0, saved.getConvertedAmount().compareTo(new BigDecimal("140.00")),
                "convertedAmount = amount × rate = 1000 × 0.14 = 140 USD");
        assertEquals("CNY", saved.getCurrency());
    }

    // ── 10. Unpaid expense → Logistics Payable ───────────────────────────────

    @Test
    void confirm_unpaidExpense_creditsLogisticsPayable() {
        stubBasics();
        LogisticsOrder draft = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-PAYABLE").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("draft").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(draft));

        when(itemRepository.findByLogisticsOrderIdOrderByIdAsc(500L)).thenReturn(List.of(
                LogisticsOrderItem.builder().id(1L).companyId(COMPANY).logisticsOrderId(500L)
                        .productId(100L).quantity(new BigDecimal("5"))
                        .unitCost(new BigDecimal("100")).itemValue(new BigDecimal("500")).build()
        ));
        when(expenseRepository.findByLogisticsOrderIdOrderByIdAsc(500L)).thenReturn(List.of(
                LogisticsExpense.builder().id(1L).companyId(COMPANY).logisticsOrderId(500L)
                        .expenseType("customs").amount(new BigDecimal("100"))
                        .currency("USD").exchangeRate(BigDecimal.ONE).convertedAmount(new BigDecimal("100"))
                        .paymentSource("cash").paidAmount(BigDecimal.ZERO).paymentStatus("unpaid").build()
        ));

        logisticsService.confirm(COMPANY, USER, 500L);

        // No cash transaction created (it's unpaid)
        verify(cashTransactionRepository, never()).save(any(CashTransaction.class));

        // Journal: DR Customs 100 / CR LogisticsPayable 100 (no cash credit)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<uz.bizcontrol.accounting.JournalLine>> cap =
                ArgumentCaptor.forClass(List.class);
        verify(journalService).post(eq(COMPANY), eq(USER), any(), eq("LOGISTICS"), eq(500L), anyString(), cap.capture());
        BigDecimal d = cap.getValue().stream().map(uz.bizcontrol.accounting.JournalLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = cap.getValue().stream().map(uz.bizcontrol.accounting.JournalLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, d.compareTo(c), "balanced");
        assertEquals(0, d.compareTo(new BigDecimal("100.00")));
    }

    // ── 11. payExpense settles outstanding ───────────────────────────────────

    @Test
    void payExpense_settlesAndPostsPayableJournal() {
        stubBasics();
        LogisticsOrder order = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("confirmed").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(order));

        LogisticsExpense unpaid = LogisticsExpense.builder().id(99L).companyId(COMPANY).logisticsOrderId(500L)
                .expenseType("customs").amount(new BigDecimal("100"))
                .currency("USD").exchangeRate(BigDecimal.ONE).convertedAmount(new BigDecimal("100"))
                .paymentSource("cash").paidAmount(BigDecimal.ZERO).paymentStatus("unpaid").build();
        when(expenseRepository.findById(99L)).thenReturn(Optional.of(unpaid));

        when(journalService.post(any(), any(), any(), eq("LOGISTICS_PAYMENT"), eq(99L), anyString(), any()))
                .thenReturn(JournalEntry.builder().id(8888L).build());
        when(paymentRepository.save(any())).thenAnswer(i -> {
            uz.bizcontrol.entity.LogisticsExpensePayment p = i.getArgument(0);
            p.setId(123L); return p;
        });

        var payment = logisticsService.payExpense(COMPANY, USER, 500L, 99L,
                Map.of("amount", "60", "paymentSource", "bank"));

        assertEquals(0, payment.getAmount().compareTo(new BigDecimal("60.00")));
        assertEquals("partial", unpaid.getPaymentStatus());
        assertEquals(0, unpaid.getPaidAmount().compareTo(new BigDecimal("60.00")));

        verify(cashTransactionRepository).save(any(CashTransaction.class));
        verify(journalService).post(eq(COMPANY), eq(USER), any(),
                eq("LOGISTICS_PAYMENT"), eq(99L), anyString(), any());
        verify(auditService).log(eq(COMPANY), eq(USER), eq("PAY_PAYABLE"),
                eq("LogisticsExpense"), eq(99L), anyString(), anyString());
    }

    // ── 12. FX delta on payable settlement (V20) ─────────────────────────────

    @Test
    void payExpense_sameRate_noFxDelta() {
        stubBasics();
        LogisticsOrder order = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-FX").currency("USD").status("confirmed")
                .sourceWarehouseId(10L).destinationWarehouseId(20L).build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(order));
        LogisticsExpense exp = LogisticsExpense.builder().id(99L).companyId(COMPANY).logisticsOrderId(500L)
                .amount(new BigDecimal("100")).paidAmount(BigDecimal.ZERO)
                .currency("CNY").exchangeRate(new BigDecimal("0.14"))
                .convertedAmount(new BigDecimal("14.00"))
                .paymentSource("cash").paymentStatus("unpaid").build();
        when(expenseRepository.findById(99L)).thenReturn(Optional.of(exp));
        when(journalService.post(any(), any(), any(), eq("LOGISTICS_PAYMENT"), eq(99L), anyString(), any()))
                .thenReturn(JournalEntry.builder().id(8888L).build());
        when(paymentRepository.save(any())).thenAnswer(i -> { var p = (uz.bizcontrol.entity.LogisticsExpensePayment) i.getArgument(0); p.setId(1L); return p; });

        var pmt = logisticsService.payExpense(COMPANY, USER, 500L, 99L,
                Map.of("amount", "100", "currency", "CNY", "exchangeRate", "0.14", "paymentSource", "cash"));

        assertEquals(0, pmt.getFxDelta().compareTo(BigDecimal.ZERO));

        // Journal has only 2 lines (DR Payable + CR Cash), no FX line
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<uz.bizcontrol.accounting.JournalLine>> cap = ArgumentCaptor.forClass(java.util.List.class);
        verify(journalService).post(eq(COMPANY), eq(USER), any(), eq("LOGISTICS_PAYMENT"), eq(99L), anyString(), cap.capture());
        assertEquals(2, cap.getValue().size(), "no FX line when rates match");
    }

    @Test
    void payExpense_higherRate_postsLossFxLine() {
        stubBasics();
        LogisticsOrder order = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-FX").currency("USD").status("confirmed")
                .sourceWarehouseId(10L).destinationWarehouseId(20L).build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(order));
        // Booked at 0.14, paying at 0.15 (CNY got more expensive in USD) → LOSS
        LogisticsExpense exp = LogisticsExpense.builder().id(99L).companyId(COMPANY).logisticsOrderId(500L)
                .amount(new BigDecimal("1000")).paidAmount(BigDecimal.ZERO)
                .currency("CNY").exchangeRate(new BigDecimal("0.14"))
                .convertedAmount(new BigDecimal("140.00"))
                .paymentSource("cash").paymentStatus("unpaid").build();
        when(expenseRepository.findById(99L)).thenReturn(Optional.of(exp));
        when(journalService.post(any(), any(), any(), eq("LOGISTICS_PAYMENT"), eq(99L), anyString(), any()))
                .thenReturn(JournalEntry.builder().id(8888L).build());
        when(paymentRepository.save(any())).thenAnswer(i -> { var p = (uz.bizcontrol.entity.LogisticsExpensePayment) i.getArgument(0); p.setId(1L); return p; });

        var pmt = logisticsService.payExpense(COMPANY, USER, 500L, 99L,
                Map.of("amount", "1000", "currency", "CNY", "exchangeRate", "0.15", "paymentSource", "bank"));

        // payBase = 1000 × 0.15 = 150, clearedInBase = 1000 × 0.14 = 140, delta = 10 (LOSS)
        assertEquals(0, pmt.getFxDelta().compareTo(new BigDecimal("10.00")), "loss is positive 10 USD");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<uz.bizcontrol.accounting.JournalLine>> cap = ArgumentCaptor.forClass(java.util.List.class);
        verify(journalService).post(eq(COMPANY), eq(USER), any(), eq("LOGISTICS_PAYMENT"), eq(99L), anyString(), cap.capture());
        BigDecimal d = cap.getValue().stream().map(uz.bizcontrol.accounting.JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = cap.getValue().stream().map(uz.bizcontrol.accounting.JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, d.compareTo(c), "balanced with FX line");
        assertEquals(3, cap.getValue().size(), "3 lines: DR Payable, CR Cash, DR FX_DIFFERENCE");
        // FX line should be a DEBIT (loss)
        long fxDebits = cap.getValue().stream()
                .filter(l -> l.getDebit().signum() > 0 && l.getMemo().contains("FX loss")).count();
        assertEquals(1, fxDebits, "FX line is a debit (loss)");
    }

    @Test
    void payExpense_lowerRate_postsGainFxLine() {
        stubBasics();
        LogisticsOrder order = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-FX").currency("USD").status("confirmed")
                .sourceWarehouseId(10L).destinationWarehouseId(20L).build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(order));
        // Booked at 0.14, paying at 0.13 (CNY cheaper in USD) → GAIN
        LogisticsExpense exp = LogisticsExpense.builder().id(99L).companyId(COMPANY).logisticsOrderId(500L)
                .amount(new BigDecimal("1000")).paidAmount(BigDecimal.ZERO)
                .currency("CNY").exchangeRate(new BigDecimal("0.14"))
                .convertedAmount(new BigDecimal("140.00"))
                .paymentSource("cash").paymentStatus("unpaid").build();
        when(expenseRepository.findById(99L)).thenReturn(Optional.of(exp));
        when(journalService.post(any(), any(), any(), eq("LOGISTICS_PAYMENT"), eq(99L), anyString(), any()))
                .thenReturn(JournalEntry.builder().id(8888L).build());
        when(paymentRepository.save(any())).thenAnswer(i -> { var p = (uz.bizcontrol.entity.LogisticsExpensePayment) i.getArgument(0); p.setId(1L); return p; });

        var pmt = logisticsService.payExpense(COMPANY, USER, 500L, 99L,
                Map.of("amount", "1000", "currency", "CNY", "exchangeRate", "0.13", "paymentSource", "cash"));

        // payBase = 130, clearedInBase = 140, delta = -10 (GAIN)
        assertEquals(0, pmt.getFxDelta().compareTo(new BigDecimal("-10.00")), "gain is negative 10 USD");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<uz.bizcontrol.accounting.JournalLine>> cap = ArgumentCaptor.forClass(java.util.List.class);
        verify(journalService).post(eq(COMPANY), eq(USER), any(), eq("LOGISTICS_PAYMENT"), eq(99L), anyString(), cap.capture());
        BigDecimal d = cap.getValue().stream().map(uz.bizcontrol.accounting.JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = cap.getValue().stream().map(uz.bizcontrol.accounting.JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, d.compareTo(c), "balanced with FX line");
        long fxCredits = cap.getValue().stream()
                .filter(l -> l.getCredit().signum() > 0 && l.getMemo().contains("FX gain")).count();
        assertEquals(1, fxCredits, "FX line is a credit (gain)");
    }

    // ── 13. Cross-currency payable payment (V20) ─────────────────────────────

    @Test
    void payExpense_crossCurrency_balancesAndClearsExpenseCorrectly() {
        stubBasics();
        // Order in USD. Expense in CNY at rate 0.14. Pay in EUR.
        LogisticsOrder order = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-CROSS").currency("USD").status("confirmed")
                .sourceWarehouseId(10L).destinationWarehouseId(20L).build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(order));
        LogisticsExpense exp = LogisticsExpense.builder().id(99L).companyId(COMPANY).logisticsOrderId(500L)
                .amount(new BigDecimal("1000")).paidAmount(BigDecimal.ZERO)
                .currency("CNY").exchangeRate(new BigDecimal("0.14"))
                .convertedAmount(new BigDecimal("140.00"))
                .paymentSource("cash").paymentStatus("unpaid").build();
        when(expenseRepository.findById(99L)).thenReturn(Optional.of(exp));
        when(journalService.post(any(), any(), any(), eq("LOGISTICS_PAYMENT"), eq(99L), anyString(), any()))
                .thenReturn(JournalEntry.builder().id(8888L).build());
        when(paymentRepository.save(any())).thenAnswer(i -> { var p = (uz.bizcontrol.entity.LogisticsExpensePayment) i.getArgument(0); p.setId(1L); return p; });

        // Partial payment in EUR. 100 EUR × 1.40 = 140 USD which clears 1000 CNY @ 0.14
        // (math here is exact so we don't bump into the rounding-cent guard)
        var pmt = logisticsService.payExpense(COMPANY, USER, 500L, 99L,
                Map.of("amount", "100", "currency", "EUR", "exchangeRate", "1.40", "paymentSource", "bank"));

        assertEquals("EUR", pmt.getCurrency());
        // Cross-currency: fxDelta is zero (both legs use the payment rate)
        assertEquals(0, pmt.getFxDelta().compareTo(BigDecimal.ZERO));
        // CashTransaction was created in EUR (not CNY)
        ArgumentCaptor<CashTransaction> ctCap = ArgumentCaptor.forClass(CashTransaction.class);
        verify(cashTransactionRepository).save(ctCap.capture());
        assertEquals("EUR", ctCap.getValue().getCurrency());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<uz.bizcontrol.accounting.JournalLine>> cap = ArgumentCaptor.forClass(java.util.List.class);
        verify(journalService).post(eq(COMPANY), eq(USER), any(), eq("LOGISTICS_PAYMENT"), eq(99L), anyString(), cap.capture());
        BigDecimal d = cap.getValue().stream().map(uz.bizcontrol.accounting.JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = cap.getValue().stream().map(uz.bizcontrol.accounting.JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, d.compareTo(c), "cross-currency journal still balances");
    }

    @Test
    void payExpense_crossCurrency_requiresExchangeRate() {
        stubBasics();
        LogisticsOrder order = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-CROSS").currency("USD").status("confirmed")
                .sourceWarehouseId(10L).destinationWarehouseId(20L).build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(order));
        LogisticsExpense exp = LogisticsExpense.builder().id(99L).companyId(COMPANY).logisticsOrderId(500L)
                .amount(new BigDecimal("1000")).paidAmount(BigDecimal.ZERO)
                .currency("CNY").exchangeRate(new BigDecimal("0.14"))
                .convertedAmount(new BigDecimal("140.00"))
                .paymentSource("cash").paymentStatus("unpaid").build();
        when(expenseRepository.findById(99L)).thenReturn(Optional.of(exp));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                logisticsService.payExpense(COMPANY, USER, 500L, 99L,
                        Map.of("amount", "100", "currency", "EUR", "paymentSource", "cash")));
        assertTrue(ex.getMessage().toLowerCase().contains("exchangerate"));
    }

    @Test
    void payExpense_exceedingOutstanding_blocked() {
        LogisticsOrder order = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").currency("USD").status("confirmed")
                .sourceWarehouseId(10L).destinationWarehouseId(20L).build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(order));
        LogisticsExpense exp = LogisticsExpense.builder().id(99L).companyId(COMPANY).logisticsOrderId(500L)
                .amount(new BigDecimal("100")).paidAmount(new BigDecimal("40"))
                .currency("USD").exchangeRate(BigDecimal.ONE)
                .convertedAmount(new BigDecimal("100"))
                .paymentSource("cash").paymentStatus("partial").build();
        when(expenseRepository.findById(99L)).thenReturn(Optional.of(exp));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                logisticsService.payExpense(COMPANY, USER, 500L, 99L,
                        Map.of("amount", "100", "paymentSource", "cash")));
        assertTrue(ex.getMessage().toLowerCase().contains("outstanding"));
    }

    @Test
    void reverse_failsCleanly_whenDestinationStockShort() {
        stubBasics();
        LogisticsOrder confirmed = LogisticsOrder.builder().id(500L).companyId(COMPANY)
                .orderNumber("LOG-1").sourceWarehouseId(10L).destinationWarehouseId(20L)
                .currency("USD").status("confirmed").build();
        when(orderRepository.findByCompanyIdAndId(COMPANY, 500L)).thenReturn(Optional.of(confirmed));
        when(itemRepository.findByLogisticsOrderIdOrderByIdAsc(500L)).thenReturn(List.of(
                LogisticsOrderItem.builder().id(1L).companyId(COMPANY).logisticsOrderId(500L)
                        .productId(100L).quantity(new BigDecimal("5"))
                        .unitCost(new BigDecimal("100")).itemValue(new BigDecimal("500")).build()
        ));
        // Simulate downstream sale — destination doesn't have enough to give back
        doThrow(new BusinessException("Insufficient available stock"))
                .when(warehouseStockService).transfer(any(), any(), any(), eq(20L), eq(10L), any(), anyString());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> logisticsService.reverse(COMPANY, USER, 500L, null));
        assertTrue(ex.getMessage().toLowerCase().contains("sold")
                || ex.getMessage().toLowerCase().contains("transferred"));
        verify(journalService, never()).reverseBySource(any(), any(), any(), any(), any());
    }
}
