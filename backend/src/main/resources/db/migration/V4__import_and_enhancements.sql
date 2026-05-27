-- =====================================================
-- V4: Import Batches + Document Status + User Invitations
-- =====================================================

-- Import Batches (tracks every bulk import)
CREATE TABLE IF NOT EXISTS import_batches (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    module VARCHAR(50) NOT NULL,           -- products, customers, suppliers
    file_name VARCHAR(500),
    total_rows INT DEFAULT 0,
    success_rows INT DEFAULT 0,
    failed_rows INT DEFAULT 0,
    duplicate_rows INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'pending',  -- pending, preview, confirmed, rolled_back, failed
    rollback_data TEXT,                    -- JSON array of created IDs for rollback
    error_summary TEXT,                    -- JSON array of {row, error} objects
    created_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id)
);

-- User Invitations (pending invites before user signs up)
CREATE TABLE IF NOT EXISTS user_invitations (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    invited_by BIGINT NOT NULL REFERENCES users(id),
    email VARCHAR(255),
    phone VARCHAR(50),
    role VARCHAR(20) NOT NULL DEFAULT 'STAFF',
    token VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) DEFAULT 'pending', -- pending, accepted, expired
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP DEFAULT (NOW() + INTERVAL '7 days'),
    accepted_at TIMESTAMP
);

-- Add doc_status to sales (draft → posted → cancelled/reversed)
ALTER TABLE sales ADD COLUMN IF NOT EXISTS doc_status VARCHAR(20) DEFAULT 'posted';
-- Backfill existing rows
UPDATE sales SET doc_status = CASE
    WHEN status = 'cancelled' THEN 'cancelled'
    ELSE 'posted'
END WHERE doc_status IS NULL OR doc_status = 'posted';

-- Add doc_status to purchases
ALTER TABLE purchases ADD COLUMN IF NOT EXISTS doc_status VARCHAR(20) DEFAULT 'posted';
UPDATE purchases SET doc_status = CASE
    WHEN status = 'cancelled' THEN 'cancelled'
    ELSE 'posted'
END WHERE doc_status IS NULL OR doc_status = 'posted';

-- Indexes
CREATE INDEX IF NOT EXISTS idx_import_batches_company ON import_batches(company_id);
CREATE INDEX IF NOT EXISTS idx_import_batches_status ON import_batches(status);
CREATE INDEX IF NOT EXISTS idx_user_invitations_company ON user_invitations(company_id);
CREATE INDEX IF NOT EXISTS idx_user_invitations_token ON user_invitations(token);
