INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('profile.self.performance', 'profile', 'self', 'performance', 'self', 'active', 'View own sales performance metrics.')
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
    ('Owner', 'profile.self.performance', 'self', 'allow', 'v1'),
    ('BranchManager', 'profile.self.performance', 'self', 'allow', 'v1'),
    ('Cashier', 'profile.self.performance', 'self', 'allow', 'v1'),
    ('InventoryClerk', 'profile.self.performance', 'self', 'allow', 'v1'),
    ('Accountant', 'profile.self.performance', 'self', 'allow', 'v1'),
    ('SupportAdmin', 'profile.self.performance', 'self', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
