-- =====================================================
-- V13: Production / Manufacturing module (additive)
-- New tables only — no existing table is modified. Integrates with products,
-- warehouses, stock_movements, accounting (journal) via service-layer calls.
-- =====================================================

-- ── BOM templates / recipes ──────────────────────────────────────────────────
CREATE TABLE bom_templates (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT NOT NULL REFERENCES companies(id),
    product_id      BIGINT NOT NULL REFERENCES products(id),
    name            VARCHAR(255) NOT NULL,
    version         VARCHAR(40) DEFAULT 'v1',
    output_quantity NUMERIC(18,3) NOT NULL DEFAULT 1,
    unit            VARCHAR(40) DEFAULT 'piece',
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    note            TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMP DEFAULT now(),
    updated_at      TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_bom_company ON bom_templates(company_id);
CREATE INDEX idx_bom_product ON bom_templates(product_id);
CREATE INDEX idx_bom_status  ON bom_templates(status);

CREATE TABLE bom_components (
    id                     BIGSERIAL PRIMARY KEY,
    bom_template_id        BIGINT NOT NULL REFERENCES bom_templates(id) ON DELETE CASCADE,
    component_product_id   BIGINT NOT NULL REFERENCES products(id),
    quantity               NUMERIC(18,3) NOT NULL,
    unit                   VARCHAR(40) DEFAULT 'piece',
    waste_percent          NUMERIC(6,2) DEFAULT 0,
    alternative_component_id BIGINT REFERENCES products(id),
    is_optional            BOOLEAN DEFAULT false,
    note                   TEXT
);
CREATE INDEX idx_bomcomp_template ON bom_components(bom_template_id);

-- ── Production orders ─────────────────────────────────────────────────────────
CREATE TABLE production_orders (
    id                       BIGSERIAL PRIMARY KEY,
    company_id               BIGINT NOT NULL REFERENCES companies(id),
    order_number             VARCHAR(60) NOT NULL,
    product_id               BIGINT NOT NULL REFERENCES products(id),
    bom_template_id          BIGINT REFERENCES bom_templates(id),
    planned_quantity         NUMERIC(18,3) NOT NULL,
    completed_quantity       NUMERIC(18,3) DEFAULT 0,
    unit                     VARCHAR(40) DEFAULT 'piece',
    status                   VARCHAR(20) NOT NULL DEFAULT 'draft',
    source_warehouse_id      BIGINT REFERENCES warehouses(id),
    production_warehouse_id   BIGINT REFERENCES warehouses(id),
    finished_goods_warehouse_id BIGINT REFERENCES warehouses(id),
    responsible_user_id      BIGINT,
    planned_start_date       TIMESTAMP,
    planned_end_date         TIMESTAMP,
    actual_start_date        TIMESTAMP,
    actual_end_date          TIMESTAMP,
    total_cost               NUMERIC(18,2) DEFAULT 0,
    cost_per_unit            NUMERIC(18,4) DEFAULT 0,
    currency                 VARCHAR(10) DEFAULT 'UZS',
    note                     TEXT,
    created_by               BIGINT,
    created_at               TIMESTAMP DEFAULT now(),
    updated_at               TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_po_company ON production_orders(company_id);
CREATE INDEX idx_po_status  ON production_orders(status);
CREATE INDEX idx_po_product ON production_orders(product_id);
CREATE INDEX idx_po_created ON production_orders(created_at);

CREATE TABLE production_order_components (
    id                   BIGSERIAL PRIMARY KEY,
    production_order_id   BIGINT NOT NULL REFERENCES production_orders(id) ON DELETE CASCADE,
    product_id           BIGINT NOT NULL REFERENCES products(id),
    warehouse_id         BIGINT REFERENCES warehouses(id),
    required_quantity    NUMERIC(18,3) NOT NULL DEFAULT 0,
    consumed_quantity    NUMERIC(18,3) NOT NULL DEFAULT 0,
    unit                 VARCHAR(40) DEFAULT 'piece',
    unit_cost            NUMERIC(18,4) DEFAULT 0,
    total_cost           NUMERIC(18,2) DEFAULT 0,
    waste_percent        NUMERIC(6,2) DEFAULT 0,
    waste_quantity       NUMERIC(18,3) DEFAULT 0
);
CREATE INDEX idx_poc_order ON production_order_components(production_order_id);
CREATE INDEX idx_poc_product ON production_order_components(product_id);

CREATE TABLE production_steps (
    id                   BIGSERIAL PRIMARY KEY,
    production_order_id   BIGINT NOT NULL REFERENCES production_orders(id) ON DELETE CASCADE,
    step_name            VARCHAR(255) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'pending',
    responsible_user_id  BIGINT,
    started_at           TIMESTAMP,
    completed_at         TIMESTAMP,
    duration_minutes     INTEGER,
    sort_order           INTEGER DEFAULT 0,
    note                 TEXT
);
CREATE INDEX idx_pstep_order ON production_steps(production_order_id);

CREATE TABLE production_costs (
    id                   BIGSERIAL PRIMARY KEY,
    production_order_id   BIGINT NOT NULL REFERENCES production_orders(id) ON DELETE CASCADE,
    cost_type            VARCHAR(30) NOT NULL,
    amount               NUMERIC(18,2) NOT NULL DEFAULT 0,
    currency             VARCHAR(10) DEFAULT 'UZS',
    note                 TEXT,
    created_by           BIGINT,
    created_at           TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_pcost_order ON production_costs(production_order_id);

CREATE TABLE waste_records (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT NOT NULL REFERENCES companies(id),
    production_order_id   BIGINT REFERENCES production_orders(id) ON DELETE CASCADE,
    product_id           BIGINT NOT NULL REFERENCES products(id),
    warehouse_id         BIGINT REFERENCES warehouses(id),
    quantity             NUMERIC(18,3) NOT NULL,
    unit                 VARCHAR(40) DEFAULT 'piece',
    reason               VARCHAR(255),
    cost_impact          NUMERIC(18,2) DEFAULT 0,
    created_by           BIGINT,
    created_at           TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_waste_company ON waste_records(company_id);
CREATE INDEX idx_waste_order   ON waste_records(production_order_id);

-- ── Permissions ──────────────────────────────────────────────────────────────
INSERT INTO permissions (code, group_name, description) VALUES
    ('production.view',          'production', 'View production orders'),
    ('production.create',        'production', 'Create production orders'),
    ('production.edit',          'production', 'Edit production orders'),
    ('production.cancel',        'production', 'Cancel production orders'),
    ('production.start',         'production', 'Start production orders'),
    ('production.complete',      'production', 'Complete production orders'),
    ('production.quality_check', 'production', 'Move orders to quality check'),
    ('production.export',        'production', 'Export production data'),
    ('production.view_cost',     'production', 'View production cost figures'),
    ('bom.view',                 'production', 'View BOM / recipes'),
    ('bom.create',               'production', 'Create BOM / recipes'),
    ('bom.edit',                 'production', 'Edit BOM / recipes'),
    ('bom.delete',               'production', 'Delete BOM / recipes'),
    ('bom.export',               'production', 'Export BOM / recipes'),
    ('production_waste.view',     'production', 'View production waste'),
    ('production_waste.create',   'production', 'Record production waste')
ON CONFLICT (code) DO NOTHING;

-- ── Grant to existing companies' roles (by role CODE) ────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE (p.code LIKE 'production.%' OR p.code LIKE 'bom.%' OR p.code LIKE 'production_waste.%')
  AND (
        r.code IN ('OWNER', 'ADMIN')
     OR (r.code = 'MANAGER'    AND p.code IN ('production.view','production.create','production.edit','production.start','production.complete','production.quality_check','production.export','production.view_cost','bom.view','bom.create','bom.edit','bom.export','production_waste.view','production_waste.create'))
     OR (r.code = 'WAREHOUSE'  AND p.code IN ('production.view','production.start','production.complete','production.quality_check','bom.view','production_waste.view','production_waste.create'))
     OR (r.code = 'ACCOUNTANT' AND p.code IN ('production.view','production.view_cost','production.export','bom.view','production_waste.view'))
     OR (r.code = 'AUDITOR'    AND p.code IN ('production.view','production.view_cost','production.export','bom.view','production_waste.view'))
     OR (r.code = 'READ_ONLY'  AND p.code IN ('production.view','bom.view'))
  )
ON CONFLICT DO NOTHING;
