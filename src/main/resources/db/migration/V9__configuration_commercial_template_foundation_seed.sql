INSERT INTO public.package_plans (
    package_id,
    display_name,
    status,
    price_code,
    config_version,
    description
) VALUES
    ('starter', 'Starter', 'active', 'starter_monthly_99', 'v1', 'Entry package for single-site teams.'),
    ('pro', 'Pro', 'active', 'pro_monthly_240', 'v1', 'Operational package for stronger branch and reporting control.'),
    ('enterprise', 'Enterprise', 'active', 'enterprise_custom', 'v1', 'Extended package for multi-branch and support-heavy tenants.')
ON CONFLICT (package_id) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    status = EXCLUDED.status,
    price_code = EXCLUDED.price_code,
    config_version = EXCLUDED.config_version,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.package_module_policies (
    package_id,
    module_id,
    enabled,
    mode,
    limits,
    policy_version
) VALUES
    ('starter', 'dashboard', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('starter', 'clients', TRUE, 'standard', '{"max_branches": 1}'::jsonb, 'v1'),
    ('starter', 'users', TRUE, 'basic', '{"max_users": 5}'::jsonb, 'v1'),
    ('starter', 'company_settings', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('starter', 'profile', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('starter', 'onboarding', TRUE, 'guided', '{}'::jsonb, 'v1'),
    ('pro', 'dashboard', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('pro', 'pos', TRUE, 'standard', '{"max_branches": 10}'::jsonb, 'v1'),
    ('pro', 'inventory', TRUE, 'full', '{"max_branches": 10}'::jsonb, 'v1'),
    ('pro', 'clients', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('pro', 'suppliers', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('pro', 'finance', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('pro', 'users', TRUE, 'advanced', '{"max_users": 40}'::jsonb, 'v1'),
    ('enterprise', 'dashboard', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('enterprise', 'pos', TRUE, 'standard', '{"max_branches": 999}'::jsonb, 'v1'),
    ('enterprise', 'inventory', TRUE, 'full', '{"max_branches": 999}'::jsonb, 'v1'),
    ('enterprise', 'clients', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('enterprise', 'suppliers', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('enterprise', 'finance', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('enterprise', 'users', TRUE, 'advanced', '{"max_users": 500}'::jsonb, 'v1'),
    ('enterprise', 'web_admin', FALSE, NULL, '{}'::jsonb, 'v1')
ON CONFLICT (package_id, module_id) DO UPDATE
SET
    enabled = EXCLUDED.enabled,
    mode = EXCLUDED.mode,
    limits = EXCLUDED.limits,
    policy_version = EXCLUDED.policy_version,
    updated_at = NOW();

INSERT INTO public.company_templates (
    template_id,
    display_name,
    business_type,
    status,
    config_version,
    description
) VALUES
    ('single_branch_retail', 'Single-Branch Retail', 'retail', 'active', 'v1', 'Retail setup for one main branch.'),
    ('multi_branch_restaurant', 'Multi-Branch Restaurant', 'restaurant', 'active', 'v1', 'Restaurant setup with branch-oriented operations.'),
    ('service_office', 'Service Office', 'services', 'active', 'v1', 'Service-led business with lower inventory dependency.')
ON CONFLICT (template_id) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    business_type = EXCLUDED.business_type,
    status = EXCLUDED.status,
    config_version = EXCLUDED.config_version,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.company_template_module_defaults (
    template_id,
    module_id,
    enabled,
    recommended,
    mode,
    notes
) VALUES
    ('single_branch_retail', 'pos', TRUE, TRUE, 'standard', 'Sales flow is core to retail.'),
    ('single_branch_retail', 'inventory', TRUE, TRUE, 'full', 'Inventory is expected in retail.'),
    ('single_branch_retail', 'finance', TRUE, TRUE, 'standard', 'Retail reporting usually needs finance visibility.'),
    ('multi_branch_restaurant', 'pos', TRUE, TRUE, 'restaurant', 'Restaurant sales should be active at all branches.'),
    ('multi_branch_restaurant', 'inventory', TRUE, TRUE, 'kitchen_stock', 'Ingredient and kitchen stock tracking is expected.'),
    ('multi_branch_restaurant', 'clients', TRUE, FALSE, 'lite', 'Optional customer accounts and loyalty flows.'),
    ('multi_branch_restaurant', 'finance', TRUE, TRUE, 'standard', 'Branch-level reporting is expected.'),
    ('service_office', 'clients', TRUE, TRUE, 'standard', 'Client records are core to service teams.'),
    ('service_office', 'inventory', FALSE, FALSE, NULL, 'Inventory is optional and usually disabled.'),
    ('service_office', 'finance', TRUE, TRUE, 'standard', 'Services still need income and expense tracking.')
ON CONFLICT (template_id, module_id) DO UPDATE
SET
    enabled = EXCLUDED.enabled,
    recommended = EXCLUDED.recommended,
    mode = EXCLUDED.mode,
    notes = EXCLUDED.notes,
    updated_at = NOW();

INSERT INTO public.company_template_workflow_defaults (
    template_id,
    flag_key,
    flag_value,
    notes
) VALUES
    ('single_branch_retail', 'branch_active_required', 'true'::jsonb, 'The user must operate inside an active branch.'),
    ('single_branch_retail', 'shift_tracking_enabled', 'true'::jsonb, 'Shift control is recommended for retail.'),
    ('multi_branch_restaurant', 'branch_active_required', 'true'::jsonb, 'Restaurant flows are branch-driven.'),
    ('multi_branch_restaurant', 'multi_branch_enabled', 'true'::jsonb, 'This template assumes multiple branches.'),
    ('multi_branch_restaurant', 'kitchen_flow_enabled', 'true'::jsonb, 'Kitchen workflows are enabled by default.'),
    ('service_office', 'branch_active_required', 'false'::jsonb, 'Service office can operate without branch switching.'),
    ('service_office', 'appointment_flow_enabled', 'true'::jsonb, 'Service businesses often need appointment workflows.')
ON CONFLICT (template_id, flag_key) DO UPDATE
SET
    flag_value = EXCLUDED.flag_value,
    notes = EXCLUDED.notes,
    updated_at = NOW();
