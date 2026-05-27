package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.Alert;
import uz.bizcontrol.entity.Company;
import uz.bizcontrol.repository.AlertRepository;
import uz.bizcontrol.repository.CashTransactionRepository;
import uz.bizcontrol.repository.CompanyRepository;
import uz.bizcontrol.repository.SaleRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily operational-health monitor. Every active company is checked once per
 * day: does it have any sales / cash / bank activity? If not, an in-app
 * {@link Alert} is created and (best-effort) a Telegram message is sent.
 *
 * <p>Health level:
 * <ul>
 *   <li><b>green</b> — sales > 0 AND cash > 0 AND bank > 0</li>
 *   <li><b>yellow</b> — 1 or 2 categories at zero</li>
 *   <li><b>red</b> — all three at zero (suspicious silence)</li>
 * </ul>
 *
 * <p>The scheduled job is best-effort and never blocks the request thread.
 * The same {@link #evaluate} method backs the dashboard widget so what the
 * operator sees in the UI is exactly what the scheduler decides on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationalHealthService {

    private final CompanyRepository companyRepository;
    private final SaleRepository saleRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final AlertRepository alertRepository;
    private final TelegramService telegram;

    /** Snapshot of a company's activity for one day. Returned to the dashboard widget. */
    public record OperationalHealth(LocalDate date, long saleCount, long cashCount,
                                    long bankCount, String level, List<String> missing,
                                    String timezone) {}

    /** Resolve the company's IANA timezone, falling back gracefully to UTC if unset/invalid. */
    private static ZoneId zoneOf(Company c) {
        String tz = c != null ? c.getTimezone() : null;
        if (tz == null || tz.isBlank()) return ZoneId.of("Asia/Tashkent");
        try { return ZoneId.of(tz); } catch (Exception e) { return ZoneId.of("Asia/Tashkent"); }
    }

    /** "Today" for {@code companyId} in its OWN timezone — what the dashboard widget shows. */
    public LocalDate companyToday(Long companyId) {
        Company c = companyRepository.findById(companyId).orElse(null);
        return ZonedDateTime.now(zoneOf(c)).toLocalDate();
    }

    /**
     * Evaluate (read-only) the operational health of {@code companyId} for {@code date}.
     * The date is treated as the company's LOCAL day; the SQL range is built in UTC so
     * timestamp comparisons stay correct across timezones.
     */
    public OperationalHealth evaluate(Long companyId, LocalDate date) {
        Company c = companyRepository.findById(companyId).orElse(null);
        ZoneId zone = zoneOf(c);
        LocalDateTime from = date.atStartOfDay(zone).toLocalDateTime();
        LocalDateTime to   = date.atTime(23, 59, 59, 999_000_000).atZone(zone).toLocalDateTime();

        long sales = saleRepository.countByCompanyIdAndDateRange(companyId, from, to);
        long cash  = cashTransactionRepository.countByCompanyIdAndSourceAndDateRange(companyId, "cash", from, to);
        long bank  = cashTransactionRepository.countByCompanyIdAndSourceAndDateRange(companyId, "bank", from, to);

        java.util.List<String> missing = new java.util.ArrayList<>();
        if (sales == 0) missing.add("sales");
        if (cash  == 0) missing.add("cash");
        if (bank  == 0) missing.add("bank");

        String level = switch (missing.size()) {
            case 0 -> "green";
            case 1, 2 -> "yellow";
            default -> "red";
        };
        return new OperationalHealth(date, sales, cash, bank, level, missing, zone.getId());
    }

    /**
     * Hourly sweep. Each company's daily check fires when its LOCAL clock crosses
     * its configured {@code dailyMonitorTime} (default 19:00). Idempotent within
     * a day via the {@code last_daily_check_at} column.
     */
    @Scheduled(cron = "${app.daily-monitor.cron:0 0 * * * *}")
    public void runDailyCheck() {
        int processed = 0, warnings = 0;
        for (Company c : companyRepository.findAll()) {
            if (Boolean.FALSE.equals(c.getDailyMonitorEnabled())) continue;
            if (!"active".equals(c.getStatus())) continue;
            if (!isLocalTriggerTime(c)) continue;
            processed++;
            try {
                ZonedDateTime now = ZonedDateTime.now(zoneOf(c));
                if (checkAndAlert(c, now.toLocalDate())) warnings++;
                c.setLastDailyCheckAt(now.toLocalDateTime());
                companyRepository.save(c);
            } catch (Exception e) {
                log.warn("[OperationalHealth] check failed for company {} — {}", c.getId(), e.getMessage());
            }
        }
        if (processed > 0) {
            log.info("[OperationalHealth] hourly sweep — {} companies due, {} warnings created",
                    processed, warnings);
        }
    }

    /**
     * True iff right now (in the company's TZ) the local hour matches the company's
     * configured trigger hour AND we have not already fired today. Defensive against
     * misformatted {@code dailyMonitorTime} strings.
     */
    private boolean isLocalTriggerTime(Company c) {
        ZoneId zone = zoneOf(c);
        ZonedDateTime nowLocal = ZonedDateTime.now(zone);
        LocalTime configured;
        try {
            configured = LocalTime.parse(c.getDailyMonitorTime() != null ? c.getDailyMonitorTime() : "19:00");
        } catch (Exception e) {
            configured = LocalTime.of(19, 0);
        }
        if (nowLocal.getHour() != configured.getHour()) return false;
        // Already fired today (compare in local TZ)?
        LocalDateTime last = c.getLastDailyCheckAt();
        if (last == null) return true;
        ZonedDateTime lastLocal = last.atZone(ZoneId.of("UTC")).withZoneSameInstant(zone);
        return !lastLocal.toLocalDate().equals(nowLocal.toLocalDate());
    }

    /**
     * Compute health for {@code company} on {@code date} and persist an Alert
     * (+ optional Telegram message) when the level is not green. Returns true
     * iff a warning was created.
     */
    @Transactional
    public boolean checkAndAlert(Company company, LocalDate date) {
        OperationalHealth h = evaluate(company.getId(), date);
        if ("green".equals(h.level())) return false;

        String msg = "On " + date + ": sales=" + h.saleCount()
                + ", cash=" + h.cashCount() + ", bank=" + h.bankCount()
                + " — missing: " + String.join(", ", h.missing());

        Alert a = Alert.builder()
                .company(company)
                .alertType("daily_health")
                .title("Daily activity warning (" + h.level().toUpperCase() + ")")
                .message(msg)
                .relatedEntityType("Company")
                .relatedEntityId(company.getId())
                .status("new")
                .build();
        alertRepository.save(a);

        if (telegram.isConfigured() && company.getTelegramChatId() != null) {
            telegram.send(company.getTelegramChatId(),
                    "<b>BizControl daily health: " + h.level().toUpperCase() + "</b>\n" + msg);
        }
        return true;
    }

    /** Lightweight summary suited for the dashboard widget JSON shape. */
    public Map<String, Object> widget(Long companyId) {
        OperationalHealth h = evaluate(companyId, companyToday(companyId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", h.date().toString());
        m.put("level", h.level());
        m.put("saleCount", h.saleCount());
        m.put("cashCount", h.cashCount());
        m.put("bankCount", h.bankCount());
        m.put("missing", h.missing());
        m.put("timezone", h.timezone());
        return m;
    }
}
