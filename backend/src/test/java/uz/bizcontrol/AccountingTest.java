package uz.bizcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.bizcontrol.accounting.*;
import uz.bizcontrol.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integrity tests for the double-entry engine. These guarantee the accounting
 * invariants that the whole financial system relies on.
 */
@ExtendWith(MockitoExtension.class)
class AccountingTest {

    @Mock JournalEntryRepository entryRepository;
    @Mock AccountRepository accountRepository;
    @InjectMocks JournalService journalService;

    private Account acc(long id, String code, String type, String normal) {
        return Account.builder().id(id).companyId(1L).code(code).name(code).type(type).normalBalance(normal).build();
    }

    private void stubCompanyAccounts() {
        when(accountRepository.findByCompanyIdOrderByCode(1L)).thenReturn(List.of(
                acc(1, "1000", "ASSET", "DEBIT"),     // Cash
                acc(2, "4000", "REVENUE", "CREDIT"),   // Sales Revenue
                acc(3, "1100", "ASSET", "DEBIT"),      // A/R
                acc(4, "5000", "EXPENSE", "DEBIT"),    // COGS
                acc(5, "1200", "ASSET", "DEBIT")       // Inventory
        ));
    }

    // ── A balanced entry posts successfully ──────────────────────────────────
    @Test
    void post_balancedEntry_succeeds() {
        stubCompanyAccounts();
        when(entryRepository.save(any())).thenAnswer(inv -> { JournalEntry e = inv.getArgument(0); e.setId(7L); return e; });

        JournalEntry e = journalService.post(1L, 1L, LocalDateTime.now(), "SALE", 100L, "test",
                List.of(JournalService.debit(1L, new BigDecimal("10000"), "cash"),
                        JournalService.credit(2L, new BigDecimal("10000"), "revenue")));

        assertEquals(7L, e.getId());
        assertEquals(2, e.getLines().size());
        verify(entryRepository).save(any());
    }

    // ── An unbalanced entry is rejected and NOT persisted ────────────────────
    @Test
    void post_unbalancedEntry_throwsAndPersistsNothing() {
        // balance check fails before account validation — no account stub needed
        assertThrows(BusinessException.class, () -> journalService.post(1L, 1L, LocalDateTime.now(), "MANUAL", null, "bad",
                List.of(JournalService.debit(1L, new BigDecimal("10000"), "cash"),
                        JournalService.credit(2L, new BigDecimal("9000"), "revenue"))));
        verify(entryRepository, never()).save(any());
    }

    // ── A line that is both debit and credit is rejected ─────────────────────
    @Test
    void post_lineWithDebitAndCredit_throws() {
        JournalLine bad = JournalLine.builder().accountId(1L)
                .debit(new BigDecimal("100")).credit(new BigDecimal("100")).build();
        assertThrows(BusinessException.class, () -> journalService.post(1L, 1L, LocalDateTime.now(), "MANUAL", null, "bad",
                List.of(bad, JournalService.credit(2L, new BigDecimal("100"), "x"))));
        verify(entryRepository, never()).save(any());
    }

    // ── An account from another company is rejected ──────────────────────────
    @Test
    void post_foreignAccount_throws() {
        stubCompanyAccounts();
        assertThrows(BusinessException.class, () -> journalService.post(1L, 1L, LocalDateTime.now(), "MANUAL", null, "x",
                List.of(JournalService.debit(999L, new BigDecimal("100"), "foreign"),
                        JournalService.credit(2L, new BigDecimal("100"), "rev"))));
        verify(entryRepository, never()).save(any());
    }

    // ── Single-line entries are rejected ─────────────────────────────────────
    @Test
    void post_singleLine_throws() {
        assertThrows(BusinessException.class, () -> journalService.post(1L, 1L, LocalDateTime.now(), "MANUAL", null, "x",
                List.of(JournalService.debit(1L, new BigDecimal("100"), "only"))));
        verify(entryRepository, never()).save(any());
    }

    // ── Reversal swaps debit/credit and marks the original reversed ──────────
    @Test
    void reverse_createsMirrorAndMarksOriginal() {
        stubCompanyAccounts();
        JournalEntry orig = JournalEntry.builder().id(50L).companyId(1L).status("posted")
                .entryDate(LocalDateTime.now())
                .lines(new java.util.ArrayList<>(List.of(
                        JournalService.debit(1L, new BigDecimal("10000"), "cash"),
                        JournalService.credit(2L, new BigDecimal("10000"), "revenue"))))
                .build();
        when(entryRepository.findById(50L)).thenReturn(Optional.of(orig));
        when(entryRepository.save(any())).thenAnswer(inv -> {
            JournalEntry e = inv.getArgument(0);
            if (e.getId() == null) e.setId(51L);
            return e;
        });

        JournalEntry reversal = journalService.reverse(1L, 1L, 50L, "mistake");

        assertEquals("REVERSAL", reversal.getSourceType());
        assertEquals(50L, reversal.getReversesEntryId());
        // original cash debit -> reversal cash credit
        assertEquals(new BigDecimal("10000.00"), reversal.getLines().get(0).getCredit());
        assertEquals("reversed", orig.getStatus());
        assertEquals(51L, orig.getReversedByEntryId());
    }

    // ── Already-reversed entries cannot be reversed again ────────────────────
    @Test
    void reverse_alreadyReversed_throws() {
        JournalEntry orig = JournalEntry.builder().id(60L).companyId(1L).status("reversed").reversedByEntryId(61L)
                .lines(new java.util.ArrayList<>()).build();
        when(entryRepository.findById(60L)).thenReturn(Optional.of(orig));
        assertThrows(BusinessException.class, () -> journalService.reverse(1L, 1L, 60L, null));
    }

    // ── Cancelling/editing a sale reverses its ledger entry (no double count) ──
    @Test
    void reverseBySource_reversesActiveSaleEntry() {
        stubCompanyAccounts();
        JournalEntry sale = JournalEntry.builder().id(70L).companyId(1L).status("posted")
                .sourceType("SALE").sourceId(500L).entryDate(LocalDateTime.now())
                .lines(new java.util.ArrayList<>(List.of(
                        JournalService.debit(1L, new BigDecimal("10000"), "cash"),
                        JournalService.credit(2L, new BigDecimal("10000"), "rev"))))
                .build();
        when(entryRepository.findByCompanyIdAndSourceTypeAndSourceId(1L, "SALE", 500L)).thenReturn(List.of(sale));
        when(entryRepository.findById(70L)).thenReturn(Optional.of(sale));
        when(entryRepository.save(any())).thenAnswer(inv -> {
            JournalEntry e = inv.getArgument(0); if (e.getId() == null) e.setId(71L); return e;
        });

        journalService.reverseBySource(1L, 1L, "SALE", 500L, "cancelled");
        assertEquals("reversed", sale.getStatus());
        assertEquals(71L, sale.getReversedByEntryId());
    }

    // ── Sale recording produces a balanced entry (Dr cash+AR+COGS = Cr rev+inv) ─
    @Test
    void recordSale_producesBalancedEntry() {
        ChartOfAccountsService chart = mock(ChartOfAccountsService.class);
        JournalService realJournal = new JournalService(entryRepository, accountRepository);
        AccountingService accounting = new AccountingService(chart, realJournal);
        stubCompanyAccounts();
        when(chart.require(eq(1L), anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(1);
            return switch (code) {
                case "1000" -> acc(1, "1000", "ASSET", "DEBIT");
                case "4000" -> acc(2, "4000", "REVENUE", "CREDIT");
                case "1100" -> acc(3, "1100", "ASSET", "DEBIT");
                case "5000" -> acc(4, "5000", "EXPENSE", "DEBIT");
                case "1200" -> acc(5, "1200", "ASSET", "DEBIT");
                default -> acc(9, code, "ASSET", "DEBIT");
            };
        });
        when(entryRepository.save(any())).thenAnswer(inv -> { JournalEntry e = inv.getArgument(0); e.setId(1L); return e; });

        uz.bizcontrol.entity.Sale sale = new uz.bizcontrol.entity.Sale();
        sale.setId(1L); sale.setSaleNumber("SL-1"); sale.setPaymentMethod("cash"); sale.setSaleDate(LocalDateTime.now());

        uz.bizcontrol.entity.SaleItem item = new uz.bizcontrol.entity.SaleItem();
        item.setQuantity(new BigDecimal("2"));
        item.setPurchaseCost(new BigDecimal("3000"));

        JournalEntry e = accounting.recordSale(1L, 1L, sale, List.of(item),
                new BigDecimal("10000"), new BigDecimal("6000"));

        assertNotNull(e);
        BigDecimal debit = e.getLines().stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = e.getLines().stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, debit.compareTo(credit), "sale journal must balance");
        // total debit = paid 6000 + AR 4000 + COGS 6000 = 16000; credit = revenue 10000 + inventory 6000 = 16000
        assertEquals(0, debit.compareTo(new BigDecimal("16000.00")));
    }

    // ── Purchase recording produces a balanced entry (Dr Inventory = Cr Cash+AP) ─
    @Test
    void recordPurchase_producesBalancedEntry() {
        ChartOfAccountsService chart = mock(ChartOfAccountsService.class);
        JournalService realJournal = new JournalService(entryRepository, accountRepository);
        AccountingService accounting = new AccountingService(chart, realJournal);
        when(accountRepository.findByCompanyIdOrderByCode(1L)).thenReturn(List.of(
                acc(5, "1200", "ASSET", "DEBIT"),       // Inventory
                acc(1, "1000", "ASSET", "DEBIT"),        // Cash
                acc(6, "2000", "LIABILITY", "CREDIT")    // A/P
        ));
        when(chart.require(eq(1L), anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(1);
            return switch (code) {
                case "1200" -> acc(5, "1200", "ASSET", "DEBIT");
                case "1000" -> acc(1, "1000", "ASSET", "DEBIT");
                case "2000" -> acc(6, "2000", "LIABILITY", "CREDIT");
                default -> acc(9, code, "ASSET", "DEBIT");
            };
        });
        when(entryRepository.save(any())).thenAnswer(inv -> { JournalEntry e = inv.getArgument(0); e.setId(1L); return e; });

        uz.bizcontrol.entity.Purchase purchase = new uz.bizcontrol.entity.Purchase();
        purchase.setId(1L); purchase.setPurchaseNumber("PO-1"); purchase.setPaymentMethod("cash");
        purchase.setPurchaseDate(LocalDateTime.now());

        JournalEntry e = accounting.recordPurchase(1L, 1L, purchase, new BigDecimal("20000"), new BigDecimal("12000"));

        assertNotNull(e);
        BigDecimal debit = e.getLines().stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = e.getLines().stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, debit.compareTo(credit), "purchase journal must balance");
        // Dr Inventory 20000 ; Cr Cash 12000 + AP 8000 = 20000
        assertEquals(0, debit.compareTo(new BigDecimal("20000.00")));
    }
}
