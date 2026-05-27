-- =====================================================
-- V20: FX-difference accounting + per-company timezone + notification settings
-- One migration covering three closely-related limitations:
--   1. FX gain/loss on payable settlements (when payment rate ≠ expense rate)
--   2. Per-company timezone (daily monitor evaluates each company in its TZ)
--   3. Notification settings (daily monitor time, language, telegram opt-out)
-- Additive only. All columns nullable / default-bearing so existing rows
-- and Hibernate @Builder inserts that omit them keep working.
-- =====================================================

-- Phase 1: FX delta on logistics expense settlements ------------------
ALTER TABLE logistics_expense_payments
    ADD COLUMN fx_delta NUMERIC(18,2) DEFAULT 0;
-- Signed amount in the order currency:
--   > 0 → FX LOSS (we paid more than booked, DR FX_DIFFERENCE)
--   < 0 → FX GAIN (we paid less than booked, CR FX_DIFFERENCE)
--   = 0 → no FX impact (same rate as booked, or cross-currency exact match)

-- Phase 2 + 3: Notifications + timezone -------------------------------
ALTER TABLE companies
    ADD COLUMN timezone             VARCHAR(60) DEFAULT 'Asia/Tashkent',
    ADD COLUMN daily_monitor_time   VARCHAR(5)  DEFAULT '19:00',
    ADD COLUMN notification_language VARCHAR(8) DEFAULT 'en',
    ADD COLUMN last_daily_check_at  TIMESTAMP;

-- Backfill any pre-V20 rows (defensive — DEFAULTs cover normal cases)
UPDATE companies SET timezone = 'Asia/Tashkent' WHERE timezone IS NULL;
UPDATE companies SET daily_monitor_time = '19:00' WHERE daily_monitor_time IS NULL;
UPDATE companies SET notification_language = 'en' WHERE notification_language IS NULL;
