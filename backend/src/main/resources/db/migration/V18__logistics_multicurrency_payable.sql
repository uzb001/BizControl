-- =====================================================
-- V18: Multi-currency expenses + logistics payable
-- Each expense keeps its original amount + currency and now also records the
-- exchange rate to the order currency and the converted amount in that
-- currency. Allocation math, journals and balance roll-ups all use
-- {@code converted_amount}; the raw {@code amount} stays the source of truth
-- for the cash transaction that hits the books.
--
-- Logistics payable: an expense may be paid in full, partially or not at all
-- at confirmation. The unpaid portion becomes a posting against the new
-- LOGISTICS_PAYABLE account (separate from supplier A/P — supplier debt is
-- never mixed with logistics). Subsequent payments are linked rows so we can
-- replay the full lifecycle.
-- Additive only.
-- =====================================================

-- ── Multi-currency columns ───────────────────────────────────────────
ALTER TABLE logistics_expenses
    ADD COLUMN exchange_rate    NUMERIC(18,6)  NOT NULL DEFAULT 1,
    ADD COLUMN converted_amount NUMERIC(18,2)  NOT NULL DEFAULT 0;

-- Backfill existing rows: same-currency expenses keep rate=1 and converted = amount
UPDATE logistics_expenses SET converted_amount = amount;

-- ── Payable columns on expenses ──────────────────────────────────────
ALTER TABLE logistics_expenses
    ADD COLUMN paid_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN payment_status  VARCHAR(20)   NOT NULL DEFAULT 'paid';
-- payment_status ∈ {paid | partial | unpaid} — enforced at the service layer

-- Backfill existing confirmed expenses: they had cash_transaction_id set so they
-- were paid at confirmation in V16. Mark them paid for consistency.
UPDATE logistics_expenses
SET paid_amount = amount, payment_status = 'paid'
WHERE cash_transaction_id IS NOT NULL;

-- ── Logistics payable payments (settlement history) ──────────────────
CREATE TABLE logistics_expense_payments (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT NOT NULL REFERENCES companies(id),
    logistics_order_id  BIGINT NOT NULL REFERENCES logistics_orders(id) ON DELETE CASCADE,
    logistics_expense_id BIGINT NOT NULL REFERENCES logistics_expenses(id) ON DELETE CASCADE,
    amount              NUMERIC(18,2) NOT NULL,
    currency            VARCHAR(10) NOT NULL,
    exchange_rate       NUMERIC(18,6) NOT NULL DEFAULT 1,
    converted_amount    NUMERIC(18,2) NOT NULL,
    payment_source      VARCHAR(20) NOT NULL,
    cash_transaction_id BIGINT,
    journal_entry_id    BIGINT,
    note                TEXT,
    paid_at             TIMESTAMP DEFAULT now(),
    paid_by             BIGINT
);
CREATE INDEX idx_logexp_pay_order   ON logistics_expense_payments(logistics_order_id);
CREATE INDEX idx_logexp_pay_expense ON logistics_expense_payments(logistics_expense_id);

-- ── Permissions ──────────────────────────────────────────────────────
INSERT INTO permissions (code, group_name, description) VALUES
    ('logistics.pay_payable', 'logistics', 'Settle (pay) an unpaid or partial logistics expense')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE p.code = 'logistics.pay_payable'
  AND r.code IN ('OWNER', 'ADMIN', 'MANAGER', 'ACCOUNTANT')
ON CONFLICT DO NOTHING;
