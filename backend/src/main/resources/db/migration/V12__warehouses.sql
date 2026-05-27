-- =====================================================
-- V12: Multi-warehouse inventory
-- Adds warehouses, per-warehouse stock balances and stock transfers, wires
-- warehouse_id onto stock movements / sales / purchases, seeds a default
-- "Main Warehouse" per existing company and backfills balances from the
-- product-level current_stock so total stock = sum(warehouse stock).
-- =====================================================

-- ── Tables ───────────────────────────────────────────────────────────────────
CREATE TABLE warehouses (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT NOT NULL REFERENCES companies(id),
    name               VARCHAR(255) NOT NULL,
    code               VARCHAR(50),
    location           VARCHAR(255),
    responsible_person VARCHAR(255),
    phone              VARCHAR(50),
    type               VARCHAR(30)  NOT NULL DEFAULT 'main',
    status             VARCHAR(20)  NOT NULL DEFAULT 'active',
    note               TEXT,
    created_at         TIMESTAMP DEFAULT now(),
    updated_at         TIMESTAMP DEFAULT now(),
    created_by         BIGINT
);
CREATE INDEX idx_warehouses_company ON warehouses(company_id);

CREATE TABLE warehouse_stock (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT NOT NULL,
    warehouse_id      BIGINT NOT NULL REFERENCES warehouses(id),
    product_id        BIGINT NOT NULL REFERENCES products(id),
    quantity          NUMERIC(18,2) NOT NULL DEFAULT 0,
    reserved_quantity NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at        TIMESTAMP DEFAULT now(),
    updated_at        TIMESTAMP DEFAULT now(),
    CONSTRAINT uq_warehouse_product UNIQUE (warehouse_id, product_id)
);
CREATE INDEX idx_wstock_company ON warehouse_stock(company_id);
CREATE INDEX idx_wstock_product ON warehouse_stock(product_id);

CREATE TABLE stock_transfers (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT NOT NULL,
    from_warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
    to_warehouse_id   BIGINT NOT NULL REFERENCES warehouses(id),
    product_id        BIGINT NOT NULL REFERENCES products(id),
    quantity          NUMERIC(18,2) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'completed',
    note              TEXT,
    created_by        BIGINT,
    created_at        TIMESTAMP DEFAULT now(),
    completed_at      TIMESTAMP
);
CREATE INDEX idx_transfers_company ON stock_transfers(company_id);

-- ── Wire warehouse_id onto existing tables ───────────────────────────────────
ALTER TABLE stock_movements ADD COLUMN warehouse_id BIGINT;
ALTER TABLE sales           ADD COLUMN warehouse_id BIGINT;
ALTER TABLE purchases       ADD COLUMN warehouse_id BIGINT;

-- ── Seed a default Main warehouse per existing company ───────────────────────
INSERT INTO warehouses (company_id, name, code, type, status, note)
SELECT id, 'Main Warehouse', 'MAIN', 'main', 'active', 'Default warehouse (auto-created)'
FROM companies;

-- ── Backfill per-warehouse stock from product-level current_stock ────────────
INSERT INTO warehouse_stock (company_id, warehouse_id, product_id, quantity, reserved_quantity)
SELECT p.company_id, w.id, p.id, COALESCE(p.current_stock, 0), 0
FROM products p
JOIN warehouses w ON w.company_id = p.company_id AND w.code = 'MAIN';

-- ── Point existing movements / docs at the Main warehouse ────────────────────
UPDATE stock_movements sm
SET warehouse_id = w.id
FROM warehouses w
WHERE w.company_id = sm.company_id AND w.code = 'MAIN' AND sm.warehouse_id IS NULL;

UPDATE sales s
SET warehouse_id = w.id
FROM warehouses w
WHERE w.company_id = s.company_id AND w.code = 'MAIN' AND s.warehouse_id IS NULL;

UPDATE purchases pu
SET warehouse_id = w.id
FROM warehouses w
WHERE w.company_id = pu.company_id AND w.code = 'MAIN' AND pu.warehouse_id IS NULL;

-- ── Permissions ──────────────────────────────────────────────────────────────
INSERT INTO permissions (code, group_name, description) VALUES
    ('warehouses.view',                'warehouses', 'View warehouses'),
    ('warehouses.create',              'warehouses', 'Create warehouses'),
    ('warehouses.edit',                'warehouses', 'Edit warehouses'),
    ('warehouses.archive',            'warehouses', 'Archive warehouses'),
    ('warehouse_stock.view',           'warehouses', 'View warehouse stock'),
    ('warehouse_stock.adjust',         'warehouses', 'Adjust warehouse stock (IN/OUT/SET)'),
    ('warehouse_stock.transfer',       'warehouses', 'Transfer stock between warehouses'),
    ('warehouse_stock.view_movements', 'warehouses', 'View warehouse stock movements'),
    ('warehouse_stock.export',         'warehouses', 'Export warehouse stock to Excel')
ON CONFLICT (code) DO NOTHING;

-- ── Grant to existing companies' roles (matched by role CODE) ────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE p.code LIKE 'warehouse%'
  AND (
        r.code IN ('OWNER', 'ADMIN')
     OR (r.code = 'MANAGER'    AND p.code IN ('warehouses.view','warehouses.create','warehouses.edit','warehouse_stock.view','warehouse_stock.adjust','warehouse_stock.transfer','warehouse_stock.view_movements','warehouse_stock.export'))
     OR (r.code = 'WAREHOUSE'  AND p.code IN ('warehouses.view','warehouse_stock.view','warehouse_stock.adjust','warehouse_stock.transfer','warehouse_stock.view_movements','warehouse_stock.export'))
     OR (r.code = 'ACCOUNTANT' AND p.code IN ('warehouses.view','warehouse_stock.view','warehouse_stock.export'))
     OR (r.code = 'SELLER'     AND p.code IN ('warehouse_stock.view'))
     OR (r.code = 'CASHIER'    AND p.code IN ('warehouse_stock.view'))
     OR (r.code = 'AUDITOR'    AND p.code IN ('warehouses.view','warehouse_stock.view','warehouse_stock.view_movements','warehouse_stock.export'))
     OR (r.code = 'READ_ONLY'  AND p.code IN ('warehouse_stock.view'))
  )
ON CONFLICT DO NOTHING;
