INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
)
VALUES
    (
        'platform.billing.balance.write',
        'web_admin',
        'billing',
        'balance_write',
        'global_admin',
        'active',
        'Create audited company billing balance credits and top-ups.'
    )
ON CONFLICT (capability_key) DO UPDATE SET
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
)
VALUES
    ('SupportAdmin', 'platform.billing.balance.write', 'global_admin', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
