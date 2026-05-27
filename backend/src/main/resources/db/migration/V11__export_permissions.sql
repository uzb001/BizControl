-- =====================================================
-- V11: Excel export permissions
-- Backend export endpoints already require these codes; this seeds them so
-- they appear in the role matrix and can be granted to non-owner roles.
-- (OWNER bypasses permission checks, so OWNER could already export.)
-- =====================================================

INSERT INTO permissions (code, group_name, description) VALUES
    ('products.export',   'products',   'Export products to Excel'),
    ('stock.export',      'stock',      'Export stock to Excel'),
    ('customers.export',  'customers',  'Export customers to Excel'),
    ('suppliers.export',  'suppliers',  'Export suppliers to Excel'),
    ('sales.export',      'sales',      'Export sales to Excel'),
    ('purchases.export',  'purchases',  'Export purchases to Excel'),
    ('cashbox.export',    'cashbox',    'Export cashbox to Excel'),
    ('bank.export',       'bank',       'Export bank transactions to Excel'),
    ('debts.export',      'debts',      'Export debts to Excel'),
    ('reports.export',    'reports',    'Export reports to Excel'),
    ('accounting.export', 'accounting', 'Export accounting statements to Excel'),
    ('audit.export',      'audit',      'Export audit logs to Excel'),
    ('users.export',      'users',      'Export users to Excel')
ON CONFLICT (code) DO NOTHING;

-- Grant every export permission to senior roles across all existing companies.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('OWNER', 'ADMIN', 'MANAGER', 'ACCOUNTANT')
  AND p.code LIKE '%.export'
ON CONFLICT DO NOTHING;
