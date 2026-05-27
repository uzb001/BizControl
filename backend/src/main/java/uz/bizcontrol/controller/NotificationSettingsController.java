package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.Company;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.CompanyRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.AuditService;
import uz.bizcontrol.service.PermissionService;
import uz.bizcontrol.service.TelegramService;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Notification-settings endpoints (V20+). Owner/admin can configure:
 *   - Telegram chat id (per-company; bot token is global env)
 *   - daily monitor on/off + time-of-day + timezone
 *   - notification language (UI + Telegram messages)
 *   - send a test Telegram message
 * Every mutation writes an audit log row.
 */
@RestController
@RequestMapping("/settings/notifications")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private static final Set<String> VALID_LANGS = Set.of("en", "ru", "uz");

    private final CompanyRepository companyRepository;
    private final TelegramService telegramService;
    private final AuditService auditService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "settings.view");
        Company c = companyRepository.findById(p.getCompanyId())
                .orElseThrow(() -> BusinessException.notFound("Company"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("telegramChatId",       c.getTelegramChatId());
        out.put("dailyMonitorEnabled",  c.getDailyMonitorEnabled() == null || c.getDailyMonitorEnabled());
        out.put("dailyMonitorTime",     c.getDailyMonitorTime()      != null ? c.getDailyMonitorTime()      : "19:00");
        out.put("timezone",             c.getTimezone()              != null ? c.getTimezone()              : "Asia/Tashkent");
        out.put("notificationLanguage", c.getNotificationLanguage()  != null ? c.getNotificationLanguage()  : "en");
        // Surface whether the global bot is even configured so the UI can warn appropriately
        out.put("telegramBotConfigured", telegramService.isConfigured());
        return ResponseEntity.ok(out);
    }

    @PutMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> update(@AuthenticationPrincipal BizControlPrincipal p,
                                                      @RequestBody Map<String, Object> body) {
        permissionService.require(p, "settings.edit_company");
        Company c = companyRepository.findById(p.getCompanyId())
                .orElseThrow(() -> BusinessException.notFound("Company"));

        if (body.containsKey("telegramChatId")) {
            Object v = body.get("telegramChatId");
            c.setTelegramChatId(v == null ? null : v.toString().trim());
        }
        if (body.containsKey("dailyMonitorEnabled")) {
            c.setDailyMonitorEnabled(Boolean.TRUE.equals(body.get("dailyMonitorEnabled")));
        }
        if (body.containsKey("dailyMonitorTime")) {
            String t = String.valueOf(body.get("dailyMonitorTime"));
            try { LocalTime.parse(t); } catch (Exception e) {
                throw new BusinessException("dailyMonitorTime must be in HH:mm format (e.g. 19:00)");
            }
            c.setDailyMonitorTime(t);
        }
        if (body.containsKey("timezone")) {
            String tz = String.valueOf(body.get("timezone"));
            try { ZoneId.of(tz); } catch (Exception e) {
                throw new BusinessException("Unknown timezone: " + tz);
            }
            c.setTimezone(tz);
        }
        if (body.containsKey("notificationLanguage")) {
            String lang = String.valueOf(body.get("notificationLanguage"));
            if (!VALID_LANGS.contains(lang))
                throw new BusinessException("notificationLanguage must be one of " + VALID_LANGS);
            c.setNotificationLanguage(lang);
        }
        companyRepository.save(c);
        auditService.log(p.getCompanyId(), p.getUserId(), "NOTIFICATION_SETTINGS_UPDATED",
                "Company", c.getId(), null, "notification settings updated");
        return get(p);
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTest(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "settings.edit_company");
        Company c = companyRepository.findById(p.getCompanyId())
                .orElseThrow(() -> BusinessException.notFound("Company"));

        if (!telegramService.isConfigured()) {
            return ResponseEntity.ok(Map.of(
                    "sent", false,
                    "reason", "Telegram bot token is not configured."));
        }
        if (c.getTelegramChatId() == null || c.getTelegramChatId().isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "sent", false,
                    "reason", "Telegram chat id is not set for this company."));
        }
        boolean ok = telegramService.send(c.getTelegramChatId(),
                "<b>BizControl test notification</b>\nIf you see this, daily-health alerts will reach this chat.");
        auditService.log(p.getCompanyId(), p.getUserId(), "TEST_NOTIFICATION_SENT",
                "Company", c.getId(), null, ok ? "test message sent" : "test message failed");
        return ResponseEntity.ok(Map.of("sent", ok,
                "reason", ok ? "Test message delivered to Telegram"
                             : "Telegram rejected the message — check chat id and bot permissions"));
    }
}
