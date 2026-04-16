-- ============================================================
-- V43: Shift Capability Expansion
-- Adds granular shift capabilities beyond the original
-- read / create / edit triplet.
-- ============================================================

INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('pos.shift.close',            'pos', 'shift', 'close',            'branch', 'active', 'Close an own shift in the active branch.'),
    ('pos.shift.force_close',      'pos', 'shift', 'force_close',      'branch', 'active', 'Force-close any shift in the active branch (manager).'),
    ('pos.shift.approve_variance', 'pos', 'shift', 'approve_variance', 'branch', 'active', 'Approve a variance on a closing shift (manager).')
ON CONFLICT (capability_key) DO UPDATE
SET
    module_id   = EXCLUDED.module_id,
    resource    = EXCLUDED.resource,
    action      = EXCLUDED.action,
    scope_type  = EXCLUDED.scope_type,
    status      = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at  = NOW();

-- ── role grants ─────────────────────────────────────────────

INSERT INTO public.role_grants (
    role_id,
    capability_key,
    scope_type,
    grant_mode,
    grant_version
) VALUES
    -- Owner gets everything
    ('Owner', 'pos.shift.close',            'company', 'allow', 'v1'),
    ('Owner', 'pos.shift.force_close',      'company', 'allow', 'v1'),
    ('Owner', 'pos.shift.approve_variance', 'company', 'allow', 'v1'),
    -- BranchManager gets everything at branch level
    ('BranchManager', 'pos.shift.close',            'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.shift.force_close',      'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.shift.approve_variance', 'branch', 'allow', 'v1'),
    -- Cashier can close own shift only
    ('Cashier', 'pos.shift.close', 'branch', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode    = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
