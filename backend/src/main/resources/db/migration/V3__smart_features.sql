-- =====================================================
-- V3: Smart Features - Saved Views, Daily Close
-- =====================================================

-- Saved Views (server-side saved filter presets)
CREATE TABLE IF NOT EXISTS saved_views (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT NOT NULL REFERENCES companies(id),
    created_by  BIGINT NOT NULL REFERENCES users(id),
    name        VARCHAR(255) NOT NULL,
    module      VARCHAR(50)  NOT NULL,   -- products, sales, purchases, customers, debts
    filter_json TEXT         NOT NULL,   -- JSON of filter params
    column_json TEXT,                    -- JSON of visible column list
    icon        VARCHAR(10),
    is_shared   BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_saved_views_company_module ON saved_views(company_id, module);

-- Daily Close records
CREATE TABLE IF NOT EXISTS daily_closes (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT  NOT NULL REFERENCES companies(id),
    close_date           DATE    NOT NULL,
    expected_cash        DECIMAL(20,2) DEFAULT 0,
    actual_cash          DECIMAL(20,2) DEFAULT 0,
    cash_difference      DECIMAL(20,2) DEFAULT 0,
    total_sales          DECIMAL(20,2) DEFAULT 0,
    total_expenses       DECIMAL(20,2) DEFAULT 0,
    total_profit         DECIMAL(20,2) DEFAULT 0,
    responsible_user_id  BIGINT REFERENCES users(id),
    comment              TEXT,
    status               VARCHAR(20) DEFAULT 'open',   -- open, closed
    closed_at            TIMESTAMP,
    created_at           TIMESTAMP DEFAULT NOW(),
    updated_at           TIMESTAMP DEFAULT NOW(),
    created_by           BIGINT REFERENCES users(id),
    UNIQUE (company_id, close_date)
);

CREATE INDEX IF NOT EXISTS idx_daily_closes_company_date ON daily_closes(company_id, close_date);
