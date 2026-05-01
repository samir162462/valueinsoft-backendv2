-- ============================================================
-- V72: Payroll Module Capabilities
-- Registers the payroll module, all 18 capabilities, and
-- grants them to Owner and Admin roles.
-- ============================================================

-- 1. Register Module
INSERT INTO public.platform_modules (module_id, display_name, category, status, default_enabled, description)
VALUES (
    'payroll',
    'Payroll & Salaries',
    'hr',
    'active',
    FALSE,
    'Salary profiles, payroll runs, payments, and finance posting for employee compensation.'
)
ON CONFLICT (module_id) DO UPDATE SET
    display_name   = EXCLUDED.display_name,
    category       = EXCLUDED.category,
    status         = EXCLUDED.status,
    default_enabled = EXCLUDED.default_enabled,
    description    = EXCLUDED.description;

-- 2. Register Capabilities
INSERT INTO public.platform_capabilities (capability_key, module_id, resource, action, scope_type, status, description)
VALUES
    -- Salary Profile
    ('payroll.profile.read',       'payroll', 'salary_profile', 'read',       'company', 'active', 'View employee salary profiles.'),
    ('payroll.profile.create',     'payroll', 'salary_profile', 'create',     'company', 'active', 'Create new salary profiles.'),
    ('payroll.profile.edit',       'payroll', 'salary_profile', 'edit',       'company', 'active', 'Edit existing salary profiles.'),
    ('payroll.profile.deactivate', 'payroll', 'salary_profile', 'deactivate', 'company', 'active', 'Deactivate a salary profile.'),

    -- Adjustments
    ('payroll.adjustment.read',    'payroll', 'adjustment', 'read',    'company', 'active', 'View payroll adjustments (bonuses, penalties).'),
    ('payroll.adjustment.create',  'payroll', 'adjustment', 'create',  'company', 'active', 'Create one-time payroll adjustments.'),
    ('payroll.adjustment.approve', 'payroll', 'adjustment', 'approve', 'company', 'active', 'Approve or reject payroll adjustments.'),

    -- Payroll Run
    ('payroll.run.read',           'payroll', 'payroll_run', 'read',        'company', 'active', 'View payroll runs and employee breakdowns.'),
    ('payroll.run.create',         'payroll', 'payroll_run', 'create',      'company', 'active', 'Generate new payroll runs.'),
    ('payroll.run.recalculate',    'payroll', 'payroll_run', 'recalculate', 'company', 'active', 'Recalculate a draft or calculated payroll run.'),
    ('payroll.run.approve',        'payroll', 'payroll_run', 'approve',     'company', 'active', 'Approve a calculated payroll run.'),
    ('payroll.run.post',           'payroll', 'payroll_run', 'post',        'company', 'active', 'Post approved payroll run to finance journals.'),
    ('payroll.run.reverse',        'payroll', 'payroll_run', 'reverse',     'company', 'active', 'Reverse a posted payroll run via journal reversal.'),

    -- Payments
    ('payroll.payment.read',       'payroll', 'payment', 'read',   'company', 'active', 'View salary payment records.'),
    ('payroll.payment.create',     'payroll', 'payment', 'create', 'company', 'active', 'Record salary payments for employees.'),

    -- Settings
    ('payroll.settings.read',      'payroll', 'settings', 'read', 'company', 'active', 'View payroll settings and account mappings.'),
    ('payroll.settings.edit',      'payroll', 'settings', 'edit', 'company', 'active', 'Update payroll settings and account mappings.'),

    -- Audit
    ('payroll.audit.read',         'payroll', 'audit_log', 'read', 'company', 'active', 'View payroll audit trail.')

ON CONFLICT (capability_key) DO UPDATE SET
    module_id   = EXCLUDED.module_id,
    resource    = EXCLUDED.resource,
    action      = EXCLUDED.action,
    scope_type  = EXCLUDED.scope_type,
    status      = EXCLUDED.status,
    description = EXCLUDED.description;

-- 3. Grant all payroll capabilities to Owner
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode)
SELECT 'Owner', capability_key, scope_type, 'allow'
FROM public.platform_capabilities
WHERE module_id = 'payroll'
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET grant_mode = 'allow';

-- 4. Grant all payroll capabilities to Admin when that role exists.
-- Some installations use SupportAdmin/Owner only, and role_grants has a role FK.
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode)
SELECT rd.role_id, pc.capability_key, pc.scope_type, 'allow'
FROM public.role_definitions rd
CROSS JOIN public.platform_capabilities pc
WHERE rd.role_id = 'Admin'
  AND pc.module_id = 'payroll'
ON CONFLICT (role_id, capability_key, scope_type) DO NOTHING;

-- 5. Grant read-only payroll capabilities to BranchManager when that role exists.
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode)
SELECT rd.role_id, pc.capability_key, pc.scope_type, 'allow'
FROM public.role_definitions rd
CROSS JOIN public.platform_capabilities pc
WHERE rd.role_id = 'BranchManager'
  AND pc.module_id = 'payroll'
  AND pc.action IN ('read')
ON CONFLICT (role_id, capability_key, scope_type) DO NOTHING;

-- 6. Register payroll module in package policies (Enterprise plan full, Business plan basic)
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('enterprise', 'payroll', TRUE,  'custom',   '{}'::jsonb, 'v1'),
    ('business',   'payroll', TRUE,  'advanced', '{}'::jsonb, 'v1'),
    ('growth',     'payroll', FALSE, 'basic',    '{}'::jsonb, 'v1'),
    ('shop',       'payroll', FALSE, 'basic',    '{}'::jsonb, 'v1'),
    ('start',      'payroll', FALSE, 'basic',    '{}'::jsonb, 'v1')
ON CONFLICT (package_id, module_id) DO UPDATE SET
    enabled        = EXCLUDED.enabled,
    mode           = EXCLUDED.mode,
    policy_version = EXCLUDED.policy_version;
