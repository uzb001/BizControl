-- =====================================================
-- V8: Money Transactions consolidation + ERP statuses
-- =====================================================

-- 1. Rename cash_transactions → money_transactions
ALTER TABLE cash_transactions RENAME TO money_transactions;

-- 2. Add payment_source to bank_transactions (it was missing)
ALTER TABLE bank_transactions ADD COLUMN IF NOT EXISTS payment_source VARCHAR(20) DEFAULT 'bank';

-- 3. Migrate bank_transactions → money_transactions (deduplicate by amount+date+company+type)
INSERT INTO money_transactions (
    company_id, transaction_type, payment_source, amount, currency, category,
    related_sale_id, related_purchase_id, related_customer_id, related_supplier_id,
    transaction_date, note, status, created_at, updated_at, created_by, updated_by
)
SELECT
    bt.company_id, bt.transaction_type, 'bank', bt.amount, bt.currency, bt.category,
    bt.related_sale_id, bt.related_purchase_id, bt.related_customer_id, bt.related_supplier_id,
    bt.transaction_date, bt.note, bt.status, bt.created_at, bt.updated_at, bt.created_by, bt.updated_by
FROM bank_transactions bt
WHERE bt.related_sale_id IS NULL
  AND bt.related_purchase_id IS NULL;
-- Note: sale/purchase bank entries are already in money_transactions via SaleService/PurchaseService

-- 4. Drop bank_transactions
DROP TABLE IF EXISTS bank_transactions;

-- 5. Re-index money_transactions
DROP INDEX IF EXISTS idx_cash_transactions_company;
DROP INDEX IF EXISTS idx_cash_transactions_date;
CREATE INDEX IF NOT EXISTS idx_money_tx_company  ON money_transactions(company_id);
CREATE INDEX IF NOT EXISTS idx_money_tx_date     ON money_transactions(transaction_date);
CREATE INDEX IF NOT EXISTS idx_money_tx_source   ON money_transactions(payment_source);
CREATE INDEX IF NOT EXISTS idx_money_tx_sale     ON money_transactions(related_sale_id);
CREATE INDEX IF NOT EXISTS idx_money_tx_purchase ON money_transactions(related_purchase_id);
CREATE INDEX IF NOT EXISTS idx_money_tx_status   ON money_transactions(status);

-- 6. ERP status columns — already added by V4, ensure they exist
ALTER TABLE sales     ADD COLUMN IF NOT EXISTS doc_status VARCHAR(20) DEFAULT 'posted';
ALTER TABLE purchases ADD COLUMN IF NOT EXISTS doc_status VARCHAR(20) DEFAULT 'posted';

-- 7. Approval foreign keys on sales/purchases
ALTER TABLE sales     ADD COLUMN IF NOT EXISTS pending_approval_id BIGINT;
ALTER TABLE purchases ADD COLUMN IF NOT EXISTS pending_approval_id BIGINT;

-- 8. Backfill existing rows so doc_status is consistent with status
UPDATE sales     SET doc_status = CASE WHEN status = 'cancelled' THEN 'cancelled' ELSE 'posted' END WHERE doc_status IS NULL;
UPDATE purchases SET doc_status = CASE WHEN status = 'cancelled' THEN 'cancelled' ELSE 'posted' END WHERE doc_status IS NULL;

-- 9. Lock-state for documents (prevent editing locked records)
ALTER TABLE sales     ADD COLUMN IF NOT EXISTS locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE purchases ADD COLUMN IF NOT EXISTS locked BOOLEAN NOT NULL DEFAULT FALSE;

-- 10. Add missing permission codes for edit operations

INSERT INTO permissions (code, group_name, description) VALUES
    ('sales.edit', 'sales', 'Edit a posted sale')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, group_name, description) VALUES
    ('purchases.edit', 'purchases', 'Edit a posted purchase')
ON CONFLICT (code) DO NOTHING;