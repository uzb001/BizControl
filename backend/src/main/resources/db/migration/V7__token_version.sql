-- ── V7: token_version for immediate JWT invalidation ─────────────────────────
-- Allows the server to invalidate all tokens issued to a user (e.g. on role
-- change or deactivation) by incrementing this counter.  Every JWT carries the
-- value at issue time; the filter rejects any token whose version is stale.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS token_version INT NOT NULL DEFAULT 1;

-- Ensure existing rows start at version 1 (no-op if column already existed).
UPDATE users SET token_version = 1 WHERE token_version IS NULL OR token_version = 0;
