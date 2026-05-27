package uz.bizcontrol.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Best-effort Telegram bot adapter for alerts. Reads the bot token from the
 * {@code APP_TELEGRAM_BOT_TOKEN} environment variable (or the
 * {@code app.telegram.bot-token} property). Per-company chat ids live on
 * {@link uz.bizcontrol.entity.Company#getTelegramChatId()}.
 *
 * <p>Behaviour: silent no-op when either the token or the chat id is missing
 * — never throws or blocks the caller. Errors are logged at WARN.
 */
@Slf4j
@Service
public class TelegramService {

    private static final String API_BASE = "https://api.telegram.org/bot";
    private final RestTemplate rest = new RestTemplate();

    @Value("${app.telegram.bot-token:${APP_TELEGRAM_BOT_TOKEN:}}")
    private String botToken;

    /** True iff a global bot token is configured and we can plausibly send anything. */
    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank();
    }

    /**
     * Send {@code message} to {@code chatId}. Returns true on a real send,
     * false when missing config or on failure. Does not throw.
     */
    public boolean send(String chatId, String message) {
        if (!isConfigured()) {
            log.debug("[Telegram] skip — no bot token configured");
            return false;
        }
        if (chatId == null || chatId.isBlank()) {
            log.debug("[Telegram] skip — no chat id for recipient");
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                    Map.of("chat_id", chatId, "text", message, "parse_mode", "HTML",
                            "disable_web_page_preview", true),
                    headers);
            rest.postForObject(API_BASE + botToken + "/sendMessage", req, String.class);
            log.info("[Telegram] sent message to chat {}", chatId);
            return true;
        } catch (Exception e) {
            log.warn("[Telegram] send failed for chat {} — {}", chatId, e.getMessage());
            return false;
        }
    }
}
