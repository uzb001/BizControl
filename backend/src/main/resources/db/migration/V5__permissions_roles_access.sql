-- ════════════════════════════════════════════════════════════════
--  V5 — Professional RBAC: permissions, roles, role_permissions,
--        access_logs; update company_users; fix user_invitations
-- ════════════════════════════════════════════════════════════════

-- ── 1. Permissions catalogue (global, read-only seed data) ─────
CREATE TABLE IF NOT EXISTS permissions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    group_name  VARCHAR(50)  NOT NULL,
    description VARCHAR(255)
);

INSERT INTO permissions (code, group_name, description) VALUES
  -- Dashboard
  ('dashboard.view',               'dashboard',  'View dashboard'),
  -- Products
  ('products.view',                'products',   'View products list'),
  ('products.create',              'products',   'Create new product'),
  ('products.edit',                'products',   'Edit existing product'),
  ('products.delete',              'products',   'Deactivate / delete product'),
  ('products.export',              'products',   'Export products'),
  ('products.view_purchase_price', 'products',   'See purchase (cost) price'),
  -- Stock
  ('stock.view',                   'stock',      'View stock levels'),
  ('stock.adjust',                 'stock',      'Adjust stock manually'),
  ('stock.view_movements',         'stock',      'View stock movement history'),
  ('stock.export',                 'stock',      'Export stock data'),
  -- Sales
  ('sales.view',                   'sales',      'View sales list'),
  ('sales.create',                 'sales',      'Create new sale'),
  ('sales.edit',                   'sales',      'Edit draft sale'),
  ('sales.cancel',                 'sales',      'Cancel sale'),
  ('sales.add_payment',            'sales',      'Record payment on sale'),
  ('sales.view_profit',            'sales',      'See profit margin on sales'),
  ('sales.discount',               'sales',      'Apply discount'),
  ('sales.export',                 'sales',      'Export sales'),
  -- Purchases
  ('purchases.view',               'purchases',  'View purchases list'),
  ('purchases.create',             'purchases',  'Create new purchase'),
  ('purchases.edit',               'purchases',  'Edit draft purchase'),
  ('purchases.cancel',             'purchases',  'Cancel purchase'),
  ('purchases.add_payment',        'purchases',  'Record payment on purchase'),
  ('purchases.export',             'purchases',  'Export purchases'),
  -- Customers
  ('customers.view',               'customers',  'View customers list'),
  ('customers.create',             'customers',  'Create customer'),
  ('customers.edit',               'customers',  'Edit customer'),
  ('customers.delete',             'customers',  'Delete / deactivate customer'),
  ('customers.view_debt',          'customers',  'See customer debt amount'),
  ('customers.export',             'customers',  'Export customers'),
  -- Suppliers
  ('suppliers.view',               'suppliers',  'View suppliers list'),
  ('suppliers.create',             'suppliers',  'Create supplier'),
  ('suppliers.edit',               'suppliers',  'Edit supplier'),
  ('suppliers.delete',             'suppliers',  'Delete / deactivate supplier'),
  ('suppliers.view_debt',          'suppliers',  'See supplier debt amount'),
  ('suppliers.export',             'suppliers',  'Export suppliers'),
  -- Cashbox
  ('cashbox.view',                 'cashbox',    'View cash transactions'),
  ('cashbox.create_income',        'cashbox',    'Record cash income'),
  ('cashbox.create_expense',       'cashbox',    'Record cash expense'),
  ('cashbox.edit',                 'cashbox',    'Edit cash transaction'),
  ('cashbox.cancel',               'cashbox',    'Cancel cash transaction'),
  ('cashbox.export',               'cashbox',    'Export cashbox data'),
  -- Bank
  ('bank.view',                    'bank',       'View bank transactions'),
  ('bank.create',                  'bank',       'Create bank transaction'),
  ('bank.edit',                    'bank',       'Edit bank transaction'),
  ('bank.export',                  'bank',       'Export bank data'),
  -- Debts
  ('debts.view_customer',          'debts',      'View customer debts'),
  ('debts.view_supplier',          'debts',      'View supplier debts'),
  ('debts.add_payment',            'debts',      'Record debt payment'),
  ('debts.close',                  'debts',      'Close / write off debt'),
  ('debts.export',                 'debts',      'Export debts'),
  -- Reports
  ('reports.view',                 'reports',    'View reports'),
  ('reports.view_profit',          'reports',    'See profit in reports'),
  ('reports.view_money_leak',      'reports',    'View money-leak report'),
  ('reports.view_customer_scores', 'reports',    'View customer trust scores'),
  ('reports.view_dead_stock',      'reports',    'View dead-stock report'),
  ('reports.export',               'reports',    'Export reports'),
  -- Import
  ('import.view',                  'import',     'View import history'),
  ('import.upload',                'import',     'Upload import file'),
  ('import.confirm',               'import',     'Confirm import batch'),
  ('import.rollback',              'import',     'Rollback import batch'),
  -- Export (global)
  ('export.all',                   'export',     'Use all export features'),
  -- Users / Roles
  ('users.view',                   'users',      'View company users'),
  ('users.invite',                 'users',      'Invite new user'),
  ('users.change_role',            'users',      'Change user role'),
  ('users.deactivate',             'users',      'Deactivate user'),
  ('users.remove',                 'users',      'Remove user from company'),
  ('roles.view',                   'roles',      'View roles list'),
  ('roles.create',                 'roles',      'Create custom role'),
  ('roles.edit',                   'roles',      'Edit role name/description'),
  ('roles.delete',                 'roles',      'Delete custom role'),
  ('roles.assign_permissions',     'roles',      'Assign permissions to role'),
  -- Settings
  ('settings.view',                'settings',   'View company settings'),
  ('settings.edit_company',        'settings',   'Edit company profile'),
  ('settings.billing',             'settings',   'View/edit billing'),
  ('settings.security',            'settings',   'Manage security settings'),
  -- Audit
  ('audit.view',                   'audit',      'View audit logs'),
  ('audit.export',                 'audit',      'Export audit logs'),
  -- Daily close
  ('daily_close.view',             'daily_close','View daily close records'),
  ('daily_close.create',           'daily_close','Create / finalize daily close'),
  -- Approvals
  ('approvals.view',               'approvals',  'View approval requests'),
  ('approvals.request',            'approvals',  'Submit actions that require approval'),
  ('approvals.decide',             'approvals',  'Approve or reject pending requests')
ON CONFLICT (code) DO NOTHING;

-- ── 2. Roles table (per-company) ───────────────────────────────
-- NOTE: V1 already created a minimal roles table (id, name, description, company_id).
-- We use CREATE TABLE IF NOT EXISTS for fresh databases, then ALTER for upgrades.
CREATE TABLE IF NOT EXISTS roles (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT       REFERENCES companies(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(50),
    description VARCHAR(255),
    is_system   BOOLEAN      NOT NULL DEFAULT false,
    color       VARCHAR(20)  DEFAULT '#6366f1',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Add missing columns to existing roles table (safe on both fresh and upgraded databases)
ALTER TABLE roles ADD COLUMN IF NOT EXISTS code        VARCHAR(50);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS is_system   BOOLEAN     NOT NULL DEFAULT false;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS color       VARCHAR(20)          DEFAULT '#6366f1';
ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP   NOT NULL DEFAULT NOW();
ALTER TABLE roles ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP   NOT NULL DEFAULT NOW();

-- Add unique constraint on (company_id, code) safely — drop first if exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'roles_company_id_code_key'
    ) THEN
        ALTER TABLE roles ADD CONSTRAINT roles_company_id_code_key UNIQUE (company_id, code);
    END IF;
END $$;

-- ── 3. Role ↔ Permission junction ─────────────────────────────
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ── 4. Extend company_users ────────────────────────────────────
ALTER TABLE company_users
    ADD COLUMN IF NOT EXISTS role_id     BIGINT REFERENCES roles(id),
    ADD COLUMN IF NOT EXISTS invited_by  BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS joined_at   TIMESTAMP DEFAULT NOW();

-- ── 5. Fix user_invitations ────────────────────────────────────
ALTER TABLE user_invitations
    ADD COLUMN IF NOT EXISTS role_id     BIGINT REFERENCES roles(id),
    ADD COLUMN IF NOT EXISTS token       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS expires_at  TIMESTAMP,
    ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_invitations_token
    ON user_invitations(token) WHERE token IS NOT NULL;

-- ── 6. Access logs ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS access_logs (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT       REFERENCES companies(id),
    user_id     BIGINT       REFERENCES users(id),
    action      VARCHAR(100),
    module      VARCHAR(50),
    result      VARCHAR(20)  NOT NULL DEFAULT 'ALLOWED',
    reason      VARCHAR(500),
    ip_address  VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_access_logs_company_user
    ON access_logs(company_id, user_id, created_at DESC);

-- ── 7. Indexes ─────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_roles_company       ON roles(company_id);
CREATE INDEX IF NOT EXISTS idx_role_perms_role     ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_company_users_roleid ON company_users(role_id);
