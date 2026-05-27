-- =====================================================
-- V9: Approval deferred action payload + cancel gates
-- =====================================================

-- 1. Add pending_action_payload to approval_requests
--    Stores the serialised JSON of the action to execute when approved.
ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS pending_action_payload TEXT;

-- 2. New permission codes
INSERT INTO permissions (code, group_name, description) VALUES
    ('sales.cancel', 'sales', 'Cancel a posted sale (requires approval)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, group_name, description) VALUES
    ('purchases.cancel', 'purchases', 'Cancel a posted purchase (requires approval)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, group_name, description) VALUES
    ('stock.adjust', 'stock', 'Manual stock adjustment (large adjustments need approval)')
ON CONFLICT (code) DO NOTHING;

-- 3. Grant the new permissions to the OWNER role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'OWNER'
  AND p.code IN ('sales.cancel', 'purchases.cancel', 'stock.adjust')
ON CONFLICT DO NOTHING;

-- 4. Grant permissions to MANAGER role too
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MANAGER'
  AND p.code IN ('sales.cancel', 'purchases.cancel', 'stock.adjust')
ON CONFLICT DO NOTHING;