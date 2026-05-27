-- ─────────────────────────────────────────────────────────────────────────
-- Double-entry accounting engine: Chart of Accounts + immutable Journal
-- ─────────────────────────────────────────────────────────────────────────

CREATE TABLE accounts (
    id             BIGSERIAL PRIMARY KEY,
    company_id     BIGINT       NOT NULL,
    code           VARCHAR(20)  NOT NULL,
    name           VARCHAR(120) NOT NULL,
    type           VARCHAR(20)  NOT NULL,   -- ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
    normal_balance VARCHAR(6)   NOT NULL,   -- DEBIT, CREDIT
    is_system      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_accounts_company_code UNIQUE (company_id, code)
);
CREATE INDEX idx_accounts_company ON accounts(company_id);

CREATE TABLE journal_entries (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT       NOT NULL,
    entry_date           TIMESTAMP    NOT NULL,
    memo                 VARCHAR(500),
    source_type          VARCHAR(40),         -- SALE, PURCHASE, CASH, PAYMENT, MANUAL, REVERSAL
    source_id            BIGINT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'posted',  -- posted | reversed
    reverses_entry_id    BIGINT,              -- this entry reverses that entry
    reversed_by_entry_id BIGINT,              -- that entry reverses this one
    created_by           BIGINT,
    created_at           TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_je_company ON journal_entries(company_id);
CREATE INDEX idx_je_source  ON journal_entries(company_id, source_type, source_id);
CREATE INDEX idx_je_date    ON journal_entries(company_id, entry_date);

CREATE TABLE journal_lines (
    id         BIGSERIAL PRIMARY KEY,
    entry_id   BIGINT        NOT NULL REFERENCES journal_entries(id),
    account_id BIGINT        NOT NULL REFERENCES accounts(id),
    debit      NUMERIC(18,2) NOT NULL DEFAULT 0,
    credit     NUMERIC(18,2) NOT NULL DEFAULT 0,
    memo       VARCHAR(255),
    CONSTRAINT chk_jl_nonneg CHECK (debit >= 0 AND credit >= 0)
);
CREATE INDEX idx_jl_entry   ON journal_lines(entry_id);
CREATE INDEX idx_jl_account ON journal_lines(account_id);

-- Seed the standard chart of accounts for every existing company.
INSERT INTO accounts (company_id, code, name, type, normal_balance, is_system)
SELECT c.id, a.code, a.name, a.type, a.normal_balance, TRUE
FROM companies c
CROSS JOIN (VALUES
    ('1000', 'Cash',                 'ASSET',     'DEBIT'),
    ('1010', 'Bank',                 'ASSET',     'DEBIT'),
    ('1100', 'Accounts Receivable',  'ASSET',     'DEBIT'),
    ('1200', 'Inventory',            'ASSET',     'DEBIT'),
    ('2000', 'Accounts Payable',     'LIABILITY', 'CREDIT'),
    ('2100', 'Tax Payable',          'LIABILITY', 'CREDIT'),
    ('3000', 'Owner Equity',         'EQUITY',    'CREDIT'),
    ('4000', 'Sales Revenue',        'REVENUE',   'CREDIT'),
    ('5000', 'Cost of Goods Sold',   'EXPENSE',   'DEBIT'),
    ('6000', 'Operating Expenses',   'EXPENSE',   'DEBIT'),
    ('6100', 'Customs Expenses',     'EXPENSE',   'DEBIT'),
    ('6200', 'Logistics Expenses',   'EXPENSE',   'DEBIT'),
    ('7000', 'FX Difference',        'EXPENSE',   'DEBIT')
) AS a(code, name, type, normal_balance);
