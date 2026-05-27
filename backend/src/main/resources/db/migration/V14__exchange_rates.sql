-- =====================================================
-- V14: Multi-currency exchange rates (additive)
-- Stores a manual per-company rate from each currency to the company base
-- currency (UZS). Used only for EXPLICIT conversion / reporting — transactions
-- always keep their own original currency and amount. Per-currency balances are
-- derived from the immutable cash-transaction log, so no balance is ever
-- silently converted.
-- =====================================================
CREATE TABLE exchange_rates (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT NOT NULL REFERENCES companies(id),
    currency     VARCHAR(10) NOT NULL,
    rate_to_base NUMERIC(18,6) NOT NULL DEFAULT 1,
    updated_at   TIMESTAMP DEFAULT now(),
    updated_by   BIGINT,
    CONSTRAINT uq_company_currency UNIQUE (company_id, currency)
);
CREATE INDEX idx_fx_company ON exchange_rates(company_id);
