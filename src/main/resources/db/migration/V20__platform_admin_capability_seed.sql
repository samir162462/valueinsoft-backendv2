INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('platform.admin.write', 'web_admin', 'admin', 'write', 'global_admin', 'active', 'Write platform admin configuration and execute platform admin actions.'),
    ('platform.company.read', 'web_admin', 'company', 'read', 'global_admin', 'active', 'Inspect tenant companies and company 360 views.'),
    ('platform.company.lifecycle.write', 'web_admin', 'company', 'lifecycle_write', 'global_admin', 'active', 'Suspend, resume, and otherwise manage company lifecycle state.'),
    ('platform.branch.read', 'web_admin', 'branch', 'read', 'global_admin', 'active', 'Inspect branches across all tenants.'),
    ('platform.branch.lifecycle.write', 'web_admin', 'branch', 'lifecycle_write', 'global_admin', 'active', 'Lock, unlock, and manage branch lifecycle state.'),
    ('platform.configuration.read', 'web_admin', 'configuration', 'read', 'global_admin', 'active', 'Inspect tenant configuration, assignments, overrides, and effective configuration.'),
    ('platform.configuration.write', 'web_admin', 'configuration', 'write', 'global_admin', 'active', 'Change platform-level configuration controls for tenants.'),
    ('platform.billing.read', 'web_admin', 'billing', 'read', 'global_admin', 'active', 'Inspect platform billing, subscriptions, and revenue summaries.'),
    ('platform.audit.read', 'web_admin', 'audit', 'read', 'global_admin', 'active', 'Read platform audit and lifecycle events.'),
    ('platform.support.read', 'web_admin', 'support', 'read', 'global_admin', 'active', 'Read platform support notes and support context.'),
    ('platform.support.write', 'web_admin', 'support', 'write', 'global_admin', 'active', 'Create and manage platform support notes.')
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
    ('SupportAdmin', 'platform.admin.write', 'global_admin', 'allow', 'v1'),
    ('SupportAdmin', 'platform.company.read', 'global_admin', 'allow', 'v1'),
    ('SupportAdmin', 'platform.company.lifecycle.write', 'global_admin', 'allow', 'v1'),
    ('SupportAdmin', 'platform.branch.read', 'global_admin', 'allow', 'v1'),
    ('SupportAdmin', 'platform.branch.lifecycle.write', 'global_admin', 'allow', 'v1'),
    ('SupportAdmin', 'platform.configuration.read', 'global_admin', 'allow', 'v1'),
    ('SupportAdmin', 'platform.configuration.write', 'global_admin', 'allow', 'v1'),
    ('SupportAdmin', 'platform.billing.read', 'global_admin', 'allow', 'v1'),
    ('SupportAdmin', 'platform.audit.read', 'global_admin', 'allow', 'v1'),
    ('SupportAdmin', 'platform.support.read', 'global_admin', 'allow', 'v1'),
    ('SupportAdmin', 'platform.support.write', 'global_admin', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
