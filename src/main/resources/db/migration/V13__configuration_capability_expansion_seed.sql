INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('suppliers.account.create', 'suppliers', 'account', 'create', 'company', 'active', 'Create supplier accounts.'),
    ('suppliers.account.edit', 'suppliers', 'account', 'edit', 'company', 'active', 'Edit supplier accounts.'),
    ('suppliers.account.delete', 'suppliers', 'account', 'delete', 'company', 'active', 'Delete supplier accounts.'),
    ('finance.entry.create', 'finance', 'entry', 'create', 'company', 'active', 'Create finance entries.'),
    ('finance.entry.edit', 'finance', 'entry', 'edit', 'company', 'active', 'Edit finance entries.')
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
    ('Owner', 'suppliers.account.create', 'company', 'allow', 'v1'),
    ('Owner', 'suppliers.account.edit', 'company', 'allow', 'v1'),
    ('Owner', 'suppliers.account.delete', 'company', 'allow', 'v1'),
    ('Owner', 'finance.entry.create', 'company', 'allow', 'v1'),
    ('Owner', 'finance.entry.edit', 'company', 'allow', 'v1'),
    ('Accountant', 'finance.entry.create', 'company', 'allow', 'v1'),
    ('Accountant', 'finance.entry.edit', 'company', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
