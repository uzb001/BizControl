package uz.bizcontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Company {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String address;

    @Column(name = "business_type")
    private String businessType;

    @Column(name = "main_currency")
    private String mainCurrency = "UZS";

    @Column(name = "secondary_currency")
    private String secondaryCurrency;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "default_payment_methods")
    private String defaultPaymentMethods;

    @Column(name = "default_stock_unit")
    private String defaultStockUnit = "piece";

    @Column(name = "starting_cash_balance")
    private BigDecimal startingCashBalance = BigDecimal.ZERO;

    @Column(name = "starting_bank_balance")
    private BigDecimal startingBankBalance = BigDecimal.ZERO;

    @Column(name = "cash_balance")
    private BigDecimal cashBalance = BigDecimal.ZERO;

    @Column(name = "bank_balance")
    private BigDecimal bankBalance = BigDecimal.ZERO;

    private String status = "active";

    /** Telegram chat id where daily-health alerts go (optional; V19+). */
    @Column(name = "telegram_chat_id")
    private String telegramChatId;

    /** When false the daily-monitor scheduler skips this company (V19+). Null treated as enabled. */
    @Builder.Default
    @Column(name = "daily_monitor_enabled")
    private Boolean dailyMonitorEnabled = Boolean.TRUE;

    /** IANA timezone the daily monitor + dashboard use for "today" (V20+). */
    @Builder.Default
    @Column(name = "timezone")
    private String timezone = "Asia/Tashkent";

    /** Hour-of-day the daily monitor should fire in {@link #timezone} (HH:mm; V20+). */
    @Builder.Default
    @Column(name = "daily_monitor_time")
    private String dailyMonitorTime = "19:00";

    /** Language for in-app + Telegram alerts: en | ru | uz (V20+). */
    @Builder.Default
    @Column(name = "notification_language")
    private String notificationLanguage = "en";

    /** Last time the daily monitor processed this company (V20+; null = never). */
    @Column(name = "last_daily_check_at")
    private LocalDateTime lastDailyCheckAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
