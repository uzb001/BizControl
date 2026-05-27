-- =====================================================
-- V19: Daily operation monitor + optional Telegram integration
-- Adds a per-company Telegram chat id (the bot token is global env var) so
-- daily-health warnings can be pushed to a chat alongside the in-app alert.
-- If either piece is missing the scheduler still creates the in-app alert —
-- Telegram is best-effort, never blocking.
-- Additive only.
-- =====================================================

-- daily_monitor_enabled is nullable (null is treated as enabled at the service
-- layer) so builder-based entity inserts that omit the field don't fail.
ALTER TABLE companies
    ADD COLUMN telegram_chat_id VARCHAR(64),
    ADD COLUMN daily_monitor_enabled BOOLEAN DEFAULT TRUE;
