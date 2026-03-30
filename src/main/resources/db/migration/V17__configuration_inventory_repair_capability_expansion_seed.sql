INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('inventory.adjustment.edit', 'inventory', 'adjustment', 'edit', 'branch', 'active', 'Edit or reverse stock adjustments in branch scope.'),
    ('pos.repair.read', 'pos', 'repair', 'read', 'branch', 'active', 'Read repair and fix-area records in the active branch.'),
    ('pos.repair.create', 'pos', 'repair', 'create', 'branch', 'active', 'Create repair and fix-area records in the active branch.'),
    ('pos.repair.edit', 'pos', 'repair', 'edit', 'branch', 'active', 'Edit repair and fix-area records in the active branch.')
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
    ('Owner', 'inventory.adjustment.edit', 'company', 'allow', 'v1'),
    ('BranchManager', 'inventory.adjustment.edit', 'branch', 'allow', 'v1'),
    ('InventoryClerk', 'inventory.adjustment.edit', 'branch', 'allow', 'v1'),
    ('Owner', 'pos.repair.read', 'company', 'allow', 'v1'),
    ('Owner', 'pos.repair.create', 'company', 'allow', 'v1'),
    ('Owner', 'pos.repair.edit', 'company', 'allow', 'v1'),
    ('BranchManager', 'pos.repair.read', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.repair.create', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.repair.edit', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.repair.read', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.repair.create', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.repair.edit', 'branch', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
