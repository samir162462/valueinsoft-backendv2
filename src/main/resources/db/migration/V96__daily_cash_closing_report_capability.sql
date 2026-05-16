INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('finance.reports.daily_cash_closing.view', 'finance', 'daily_cash_closing_report', 'view', 'branch', 'active', 'View daily sales and cash closing PDF report.')
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
    ('Owner', 'finance.reports.daily_cash_closing.view', 'company', 'allow', 'v1'),
    ('Accountant', 'finance.reports.daily_cash_closing.view', 'company', 'allow', 'v1'),
    ('BranchManager', 'finance.reports.daily_cash_closing.view', 'branch', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
