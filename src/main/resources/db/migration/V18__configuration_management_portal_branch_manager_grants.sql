INSERT INTO public.role_grants (
    role_id,
    capability_key,
    scope_type,
    grant_mode,
    grant_version
) VALUES
    ('BranchManager', 'company.settings.read', 'branch', 'allow', 'v1'),
    ('BranchManager', 'users.account.read', 'branch', 'allow', 'v1'),
    ('BranchManager', 'users.account.edit', 'branch', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
