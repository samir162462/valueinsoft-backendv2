INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('pos.sale.read', 'pos', 'sale', 'read', 'branch', 'active', 'Read sales in the active branch.'),
    ('pos.sale.edit', 'pos', 'sale', 'edit', 'branch', 'active', 'Edit sales and sale corrections in the active branch.')
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
    ('Owner', 'pos.sale.create', 'company', 'allow', 'v1'),
    ('Owner', 'pos.sale.read', 'company', 'allow', 'v1'),
    ('Owner', 'pos.sale.edit', 'company', 'allow', 'v1'),
    ('BranchManager', 'pos.sale.read', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.sale.edit', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.sale.read', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.sale.edit', 'branch', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
