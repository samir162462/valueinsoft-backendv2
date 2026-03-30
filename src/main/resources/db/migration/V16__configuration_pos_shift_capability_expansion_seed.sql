INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('pos.shift.read', 'pos', 'shift', 'read', 'branch', 'active', 'Read shift state and shift history in the active branch.'),
    ('pos.shift.create', 'pos', 'shift', 'create', 'branch', 'active', 'Start shifts in the active branch.'),
    ('pos.shift.edit', 'pos', 'shift', 'edit', 'branch', 'active', 'Close and manage shifts in the active branch.')
ON CONFLICT (capability_key) DO UPDATE
SET
    module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.role_grants (
    role_id,
    capability_key,
    scope_type,
    grant_mode,
    grant_version
) VALUES
    ('Owner', 'pos.shift.read', 'company', 'allow', 'v1'),
    ('Owner', 'pos.shift.create', 'company', 'allow', 'v1'),
    ('Owner', 'pos.shift.edit', 'company', 'allow', 'v1'),
    ('BranchManager', 'pos.shift.read', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.shift.create', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.shift.edit', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.shift.read', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.shift.create', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.shift.edit', 'branch', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
