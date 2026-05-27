-- =====================================================
-- V16: Logistics & Landed Cost (international import flow)
-- A logistics order moves products from a source warehouse to a destination
-- warehouse and capitalises customs/shipping/broker/insurance costs across
-- the items proportionally to their value. Confirmation performs the stock
-- transfers, creates cash transactions for paid expenses, and posts ONE
-- balanced journal entry to the ledger. All math is preserved in
-- landed_cost_allocations for audit & reporting.
--
-- Backed by existing rails:
--   - countries (V15)            → source/destination country FKs
--   - warehouses (V12)           → source/destination warehouse FKs
--   - products                   → items
--   - suppliers (optional)       → upstream link (NEVER updates supplier debt)
--   - chart of accounts          → uses existing 1000/1010/2000/6000/6100/6200
-- Additive only. No existing flow is altered.
-- =====================================================

CREATE TABLE logistics_orders (
    id                        BIGSERIAL PRIMARY KEY,
    company_id                BIGINT NOT NULL REFERENCES companies(id),
    order_number              VARCHAR(40) NOT NULL,
    source_country_id         BIGINT REFERENCES countries(id),
    source_warehouse_id       BIGINT NOT NULL REFERENCES warehouses(id),
    destination_country_id    BIGINT REFERENCES countries(id),
    destination_warehouse_id  BIGINT NOT NULL REFERENCES warehouses(id),
    supplier_id               BIGINT REFERENCES suppliers(id),
    currency                  VARCHAR(10) NOT NULL DEFAULT 'UZS',
    exchange_rate             NUMERIC(18,6) DEFAULT 1,
    items_value               NUMERIC(18,2) DEFAULT 0,
    expenses_total            NUMERIC(18,2) DEFAULT 0,
    landed_total              NUMERIC(18,2) DEFAULT 0,
    status                    VARCHAR(20) NOT NULL DEFAULT 'draft',
    note                      TEXT,
    confirmed_at              TIMESTAMP,
    confirmed_by              BIGINT,
    cancelled_at              TIMESTAMP,
    cancelled_by              BIGINT,
    created_at                TIMESTAMP DEFAULT now(),
    updated_at                TIMESTAMP DEFAULT now(),
    created_by                BIGINT,
    CONSTRAINT uq_logistics_company_number UNIQUE (company_id, order_number)
);
CREATE INDEX idx_logistics_company        ON logistics_orders(company_id);
CREATE INDEX idx_logistics_status         ON logistics_orders(status);
CREATE INDEX idx_logistics_src_wh         ON logistics_orders(source_warehouse_id);
CREATE INDEX idx_logistics_dst_wh         ON logistics_orders(destination_warehouse_id);
CREATE INDEX idx_logistics_created_at     ON logistics_orders(created_at DESC);

CREATE TABLE logistics_order_items (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT NOT NULL REFERENCES companies(id),
    logistics_order_id  BIGINT NOT NULL REFERENCES logistics_orders(id) ON DELETE CASCADE,
    product_id          BIGINT NOT NULL REFERENCES products(id),
    quantity            NUMERIC(18,4) NOT NULL,
    unit_cost           NUMERIC(18,2) NOT NULL,
    item_value          NUMERIC(18,2) NOT NULL,
    created_at          TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_logistics_items_order ON logistics_order_items(logistics_order_id);
CREATE INDEX idx_logistics_items_prod  ON logistics_order_items(product_id);

CREATE TABLE logistics_expenses (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT NOT NULL REFERENCES companies(id),
    logistics_order_id   BIGINT NOT NULL REFERENCES logistics_orders(id) ON DELETE CASCADE,
    expense_type         VARCHAR(40) NOT NULL,
    amount               NUMERIC(18,2) NOT NULL,
    currency             VARCHAR(10) NOT NULL DEFAULT 'UZS',
    payment_source       VARCHAR(20) NOT NULL DEFAULT 'cash',
    cash_transaction_id  BIGINT,
    note                 TEXT,
    created_at           TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_logistics_expenses_order ON logistics_expenses(logistics_order_id);

CREATE TABLE landed_cost_allocations (
    id                        BIGSERIAL PRIMARY KEY,
    company_id                BIGINT NOT NULL REFERENCES companies(id),
    logistics_order_id        BIGINT NOT NULL REFERENCES logistics_orders(id) ON DELETE CASCADE,
    logistics_order_item_id   BIGINT NOT NULL REFERENCES logistics_order_items(id) ON DELETE CASCADE,
    product_id                BIGINT NOT NULL REFERENCES products(id),
    quantity                  NUMERIC(18,4) NOT NULL,
    item_value                NUMERIC(18,2) NOT NULL,
    allocated_amount          NUMERIC(18,2) NOT NULL,
    unit_landed_cost          NUMERIC(18,4) NOT NULL,
    created_at                TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_logistics_alloc_order ON landed_cost_allocations(logistics_order_id);
CREATE INDEX idx_logistics_alloc_prod  ON landed_cost_allocations(product_id);

-- ── Permissions ──────────────────────────────────────────────────────
INSERT INTO permissions (code, group_name, description) VALUES
    ('logistics.view',     'logistics', 'View logistics orders'),
    ('logistics.create',   'logistics', 'Create / edit draft logistics orders'),
    ('logistics.confirm',  'logistics', 'Confirm a logistics order (transfer stock + book expenses)'),
    ('logistics.cancel',   'logistics', 'Cancel a draft logistics order'),
    ('logistics.export',   'logistics', 'Export logistics orders to Excel'),
    ('logistics.view_landed_cost', 'logistics', 'View landed-cost summary (cost-sensitive)')
ON CONFLICT (code) DO NOTHING;

-- Grant to existing companies' roles (matched by role CODE)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE p.code LIKE 'logistics.%'
  AND (
        r.code IN ('OWNER', 'ADMIN')
     OR (r.code = 'MANAGER'    AND p.code IN ('logistics.view','logistics.create','logistics.confirm','logistics.export','logistics.view_landed_cost'))
     OR (r.code = 'WAREHOUSE'  AND p.code IN ('logistics.view','logistics.export'))
     OR (r.code = 'ACCOUNTANT' AND p.code IN ('logistics.view','logistics.export','logistics.view_landed_cost'))
     OR (r.code = 'AUDITOR'    AND p.code IN ('logistics.view','logistics.export','logistics.view_landed_cost'))
     OR (r.code = 'READ_ONLY'  AND p.code = 'logistics.view')
  )
ON CONFLICT DO NOTHING;
