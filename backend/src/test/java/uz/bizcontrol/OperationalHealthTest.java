package uz.bizcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uz.bizcontrol.entity.Alert;
import uz.bizcontrol.entity.Company;
import uz.bizcontrol.repository.AlertRepository;
import uz.bizcontrol.repository.CashTransactionRepository;
import uz.bizcontrol.repository.CompanyRepository;
import uz.bizcontrol.repository.SaleRepository;
import uz.bizcontrol.service.OperationalHealthService;
import uz.bizcontrol.service.TelegramService;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Daily operational-health monitor tests:
 *   - all activity present → green, no alert
 *   - some category missing → yellow, alert created
 *   - all missing → red, alert created
 *   - Telegram not configured → check still succeeds (best-effort)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperationalHealthTest {

    @Mock CompanyRepository companyRepository;
    @Mock SaleRepository saleRepository;
    @Mock CashTransactionRepository cashTransactionRepository;
    @Mock AlertRepository alertRepository;
    @Mock TelegramService telegram;
    @InjectMocks OperationalHealthService service;

    private static final Long COMPANY = 1L;
    private final LocalDate today = LocalDate.now();

    private Company company() {
        Company c = new Company();
        c.setId(COMPANY); c.setName("Acme"); c.setStatus("active");
        c.setDailyMonitorEnabled(true);
        return c;
    }

    private void stubCounts(long sales, long cash, long bank) {
        when(saleRepository.countByCompanyIdAndDateRange(eq(COMPANY), any(), any())).thenReturn(sales);
        when(cashTransactionRepository.countByCompanyIdAndSourceAndDateRange(eq(COMPANY), eq("cash"), any(), any())).thenReturn(cash);
        when(cashTransactionRepository.countByCompanyIdAndSourceAndDateRange(eq(COMPANY), eq("bank"), any(), any())).thenReturn(bank);
    }

    // ── 1. All categories present → green, no alert ─────────────────────────

    @Test
    void allCategoriesPresent_returnsGreen_noAlert() {
        stubCounts(3, 5, 2);
        var h = service.evaluate(COMPANY, today);
        assertEquals("green", h.level());
        assertTrue(h.missing().isEmpty());

        boolean warned = service.checkAndAlert(company(), today);
        assertFalse(warned);
        verify(alertRepository, never()).save(any());
    }

    // ── 2. Some missing → yellow, alert created ─────────────────────────────

    @Test
    void partialActivity_returnsYellow_andCreatesAlert() {
        stubCounts(2, 0, 1);  // cash missing
        var h = service.evaluate(COMPANY, today);
        assertEquals("yellow", h.level());
        assertTrue(h.missing().contains("cash"));

        boolean warned = service.checkAndAlert(company(), today);
        assertTrue(warned);

        ArgumentCaptor<Alert> cap = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(cap.capture());
        assertEquals("daily_health", cap.getValue().getAlertType());
        assertTrue(cap.getValue().getTitle().contains("YELLOW"));
    }

    // ── 3. All missing → red, alert created ─────────────────────────────────

    @Test
    void noActivity_returnsRed_andCreatesAlert() {
        stubCounts(0, 0, 0);
        var h = service.evaluate(COMPANY, today);
        assertEquals("red", h.level());
        assertEquals(3, h.missing().size());

        boolean warned = service.checkAndAlert(company(), today);
        assertTrue(warned);
        verify(alertRepository).save(argThat(a -> a.getTitle().contains("RED")));
    }

    // ── 4. Telegram unconfigured → check still succeeds ─────────────────────

    @Test
    void telegramUnconfigured_doesNotCrash() {
        stubCounts(0, 0, 0);
        when(telegram.isConfigured()).thenReturn(false);

        // Should NOT throw — Telegram is best-effort
        assertDoesNotThrow(() -> service.checkAndAlert(company(), today));
        verify(telegram, never()).send(any(), any());
    }

    // ── 5. Telegram configured + chat id → message sent ─────────────────────

    @Test
    void telegramConfigured_sendsMessageWithoutBlocking() {
        stubCounts(0, 0, 0);
        when(telegram.isConfigured()).thenReturn(true);
        Company c = company(); c.setTelegramChatId("12345");

        service.checkAndAlert(c, today);
        verify(telegram).send(eq("12345"), contains("daily health"));
    }

    // ── 6. Scheduled run skips disabled companies ───────────────────────────

    @Test
    void scheduledRun_skipsDisabledCompanies() {
        Company disabled = company(); disabled.setDailyMonitorEnabled(false);
        when(companyRepository.findAll()).thenReturn(java.util.List.of(disabled));
        service.runDailyCheck();
        verify(alertRepository, never()).save(any());
    }

    // ── 7. Widget shape ─────────────────────────────────────────────────────

    @Test
    void widget_returnsExpectedKeys() {
        stubCounts(1, 2, 0);
        var m = service.widget(COMPANY);
        assertEquals("yellow", m.get("level"));
        assertEquals(1L, m.get("saleCount"));
        assertEquals(2L, m.get("cashCount"));
        assertEquals(0L, m.get("bankCount"));
        assertTrue(((java.util.List<?>) m.get("missing")).contains("bank"));
        assertNotNull(m.get("timezone"));
    }

    // ── 8. Per-company timezone (V20) ────────────────────────────────────────

    @Test
    void evaluate_honoursCompanyTimezone() {
        Company c = company();
        c.setTimezone("America/New_York");
        when(companyRepository.findById(COMPANY)).thenReturn(java.util.Optional.of(c));
        stubCounts(1, 1, 1);

        var h = service.evaluate(COMPANY, today);
        assertEquals("America/New_York", h.timezone(), "snapshot tags the resolved zone");
        assertEquals("green", h.level());
    }

    @Test
    void evaluate_fallsBackWhenCompanyHasNoTimezone() {
        Company c = company();
        c.setTimezone(null);
        when(companyRepository.findById(COMPANY)).thenReturn(java.util.Optional.of(c));
        stubCounts(0, 0, 0);

        // Doesn't crash on missing TZ — falls back to Asia/Tashkent
        var h = service.evaluate(COMPANY, today);
        assertEquals("Asia/Tashkent", h.timezone());
        assertEquals("red", h.level());
    }

    @Test
    void evaluate_invalidTimezone_fallsBack() {
        Company c = company();
        c.setTimezone("Not/A_Real_Zone");
        when(companyRepository.findById(COMPANY)).thenReturn(java.util.Optional.of(c));
        stubCounts(1, 1, 1);
        // Does not throw
        var h = service.evaluate(COMPANY, today);
        assertEquals("Asia/Tashkent", h.timezone());
    }
}
