-- ════════════════════════════════════════════════════════════════
--  V6 — Approval Workflows, Temporary Permissions, Smart Audit
-- ════════════════════════════════════════════════════════════════

-- ── 1. Approval requests table ────────────────────────────────
CREATE TABLE IF NOT EXISTS approval_requests (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT      NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    requester_id    BIGINT      NOT NULL REFERENCES users(id),
    approver_id     BIGINT      REFERENCES users(id),

    -- What triggered this approval
    trigger_type    VARCHAR(50)  NOT NULL,
    -- e.g. 'SALE_BELOW_COST', 'HIGH_DISCOUNT', 'LARGE_STOCK_ADJUST',
    --      'SALE_CANCEL', 'PURCHASE_CANCEL', 'LARGE_EXPENSE'

    -- Reference to the entity being approved
    entity_type     VARCHAR(50),   -- 'Sale', 'Purchase', 'StockMovement', 'CashTransaction'
    entity_id       BIGINT,

    -- Details about why approval is needed
    description     TEXT,
    metadata        TEXT,          -- JSON with context (amounts, thresholds, etc.)

    -- Status lifecycle: pending → approved / rejected / cancelled
    status          VARCHAR(20)    NOT NULL DEFAULT 'pending',

    -- Approval decision
    decision_note   TEXT,
    decided_at      TIMESTAMP,

    -- Auto-expire after 24h if no decision
    expires_at      TIMESTAMP,

    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_approval_requests_company
    ON approval_requests(company_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_approval_requests_requester
    ON approval_requests(requester_id, status);
CREATE INDEX IF NOT EXISTS idx_approval_requests_approver
    ON approval_requests(approver_id, status);

-- ── 2. Temporary permissions table ────────────────────────────
CREATE TABLE IF NOT EXISTS temporary_permissions (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT      NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    granted_by      BIGINT      NOT NULL REFERENCES users(id),
    permission_code VARCHAR(100) NOT NULL,
    reason          TEXT,
    granted_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP   NOT NULL,
    revoked_at      TIMESTAMP,
    is_active       BOOLEAN     NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS idx_temp_perms_user_company
    ON temporary_permissions(user_id, company_id, is_active, expires_at);

-- ── 3. Enhance access_logs with risk_level ────────────────────
ALTER TABLE access_logs
    ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20) DEFAULT 'LOW';
    -- Values: 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'

-- Update index to include risk_level for smart audit queries
CREATE INDEX IF NOT EXISTS idx_access_logs_risk
    ON access_logs(company_id, risk_level, created_at DESC);

-- ── 4. Add new permission codes for approval system ───────────
INSERT INTO permissions (code, group_name, description) VALUES
  ('approvals.view',    'approvals', 'View approval requests'),
  ('approvals.decide',  'approvals', 'Approve or reject requests'),
  ('approvals.request', 'approvals', 'Submit approval requests')
ON CONFLICT (code) DO NOTHING;
