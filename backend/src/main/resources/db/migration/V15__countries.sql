-- =====================================================
-- V15: Countries (international trade foundation)
-- Adds a Countries entity per company, plus nullable country_id FKs on
-- warehouses and suppliers. Fully additive — no existing flow is altered.
-- Suppliers keep their legacy free-text `country` column for backward compat.
-- =====================================================

CREATE TABLE countries (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT NOT NULL REFERENCES companies(id),
    name        VARCHAR(120) NOT NULL,
    code        VARCHAR(8),
    currency    VARCHAR(10) DEFAULT 'UZS',
    timezone    VARCHAR(60),
    language    VARCHAR(8),
    notes       TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at  TIMESTAMP DEFAULT now(),
    updated_at  TIMESTAMP DEFAULT now(),
    created_by  BIGINT,
    CONSTRAINT uq_country_company_name UNIQUE (company_id, name)
);
CREATE INDEX idx_country_company ON countries(company_id);
CREATE INDEX idx_country_status  ON countries(status);

ALTER TABLE warehouses ADD COLUMN country_id BIGINT REFERENCES countries(id);
ALTER TABLE suppliers  ADD COLUMN country_id BIGINT REFERENCES countries(id);

CREATE INDEX idx_warehouses_country ON warehouses(country_id);
CREATE INDEX idx_suppliers_country  ON suppliers(country_id);

-- ── Permissions ──────────────────────────────────────────────────────
INSERT INTO permissions (code, group_name, description) VALUES
    ('countries.view',    'countries', 'View countries'),
    ('countries.create',  'countries', 'Create countries'),
    ('countries.edit',    'countries', 'Edit countries'),
    ('countries.archive', 'countries', 'Archive / restore / delete countries')
ON CONFLICT (code) DO NOTHING;

-- Grant to existing companies' roles (matched by role CODE)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE p.code LIKE 'countries.%'
  AND (
        r.code IN ('OWNER', 'ADMIN')
     OR (r.code = 'MANAGER'    AND p.code IN ('countries.view','countries.create','countries.edit'))
     OR (r.code = 'WAREHOUSE'  AND p.code = 'countries.view')
     OR (r.code = 'ACCOUNTANT' AND p.code = 'countries.view')
     OR (r.code = 'AUDITOR'    AND p.code = 'countries.view')
     OR (r.code = 'READ_ONLY'  AND p.code = 'countries.view')
  )
ON CONFLICT DO NOTHING;
