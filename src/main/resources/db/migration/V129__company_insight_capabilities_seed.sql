-- ==========================================================
-- V129 - Company Smart Insights: capabilities + role grants
-- ==========================================================
-- Company-scoped capabilities for the Company Smart Insights admin feature.
-- Company admins (Owner) get full access; BranchManager is intentionally NOT
-- granted company-wide visibility by default.
-- ==========================================================

INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('company.insights.view',      'clients', 'company_insights', 'view',      'company', 'active', 'View Company Smart Insights dashboard and alerts.'),
    ('company.insights.ai',        'clients', 'company_insights', 'ai',        'company', 'active', 'Generate AI narrative for Company Smart Insights.'),
    ('company.insights.configure', 'clients', 'company_insights', 'configure', 'company', 'active', 'Configure Company Smart Insights thresholds.'),
    ('company.insights.admin',     'clients', 'company_insights', 'admin',     'company', 'active', 'Trigger recalculation and backfill for Company Smart Insights.')
ON CONFLICT (capability_key) DO UPDATE
SET module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
VALUES
    ('Owner', 'company.insights.view',      'company', 'allow', 'v1'),
    ('Owner', 'company.insights.ai',        'company', 'allow', 'v1'),
    ('Owner', 'company.insights.configure', 'company', 'allow', 'v1'),
    ('Owner', 'company.insights.admin',     'company', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
