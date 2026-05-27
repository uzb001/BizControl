-- =====================================================
-- V17: Logistics reversal (safe undo of a confirmed order)
-- Confirmed orders stay immutable; reversal is a separate, audited action.
-- It restores stock from destination → source, marks the original
-- cash transactions as reversed, posts a mirror journal entry via
-- JournalService.reverseBySource("LOGISTICS", ...), and flips the order's
-- status to 'reversed' with full traceability columns. Idempotent: a
-- second reversal attempt is blocked by the service layer.
-- Additive only.
-- =====================================================

ALTER TABLE logistics_orders
    ADD COLUMN reversed_at TIMESTAMP,
    ADD COLUMN reversed_by BIGINT,
    ADD COLUMN reversal_reason TEXT;

-- 'draft' | 'confirmed' | 'cancelled' | 'reversed'  — enforced at the service
-- layer (no DB CHECK constraint so older rows aren't disturbed).

-- ── Permissions ──────────────────────────────────────────────────────
INSERT INTO permissions (code, group_name, description) VALUES
    ('logistics.reverse', 'logistics', 'Reverse a confirmed logistics order (restores stock + reverses cash + posts reversal journal)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE p.code = 'logistics.reverse'
  AND r.code IN ('OWNER', 'ADMIN')
ON CONFLICT DO NOTHING;
