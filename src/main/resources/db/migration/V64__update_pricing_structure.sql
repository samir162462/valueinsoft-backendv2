-- V64__update_pricing_structure.sql
-- Register new modules and update package plans with the new structure

-- 1. Register new modules if missing
INSERT INTO public.platform_modules (module_id, display_name, category, status, default_enabled, config_version, description)
VALUES 
    ('repair_center', 'Repair Center', 'operations', 'active', FALSE, 'v1', 'Repair orders, tracking, and service workflows.'),
    ('api_access', 'API Access', 'admin', 'active', FALSE, 'v1', 'External API access for integrations.')
ON CONFLICT (module_id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    category = EXCLUDED.category,
    status = EXCLUDED.status,
    description = EXCLUDED.description;

-- 2. Update/Insert Package Plans
INSERT INTO public.package_plans (package_id, display_name, status, price_code, config_version, description, monthly_price_amount, currency_code, display_order, featured)
VALUES
    ('start', 'Start / بداية', 'active', 'start_monthly_399', 'v1', 'For very small shops that need simple sales, inventory, and cashier control.', 399.00, 'EGP', 10, FALSE),
    ('shop', 'Shop / محل', 'active', 'shop_monthly_749', 'v1', 'For shops that need real daily operation control. Most popular package.', 749.00, 'EGP', 20, TRUE),
    ('growth', 'Growth / نمو', 'active', 'growth_monthly_1249', 'v1', 'For growing shops that need customers, employees, attendance, and stronger control.', 1249.00, 'EGP', 30, FALSE),
    ('business', 'Business / بيزنس', 'active', 'business_monthly_2249', 'v1', 'For serious multi-branch businesses that need finance, service operations, and management visibility.', 2249.00, 'EGP', 40, FALSE),
    ('enterprise', 'Enterprise / مؤسسة', 'active', 'enterprise_custom', 'v1', 'For larger chains and businesses that need customization, integrations, and higher limits.', 3999.00, 'EGP', 50, FALSE)
ON CONFLICT (package_id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    status = EXCLUDED.status,
    price_code = EXCLUDED.price_code,
    description = EXCLUDED.description,
    monthly_price_amount = EXCLUDED.monthly_price_amount,
    display_order = EXCLUDED.display_order,
    featured = EXCLUDED.featured;

-- 3. Retire old plans
UPDATE public.package_plans SET status = 'retired' WHERE package_id IN ('starter', 'pro');

-- 4. Clear old policies for these packages to avoid conflicts
DELETE FROM public.package_module_policies WHERE package_id IN ('start', 'shop', 'growth', 'business', 'enterprise');

-- 5. Insert Module Policies for each plan

-- START Plan
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('start', 'pos', TRUE, 'basic', '{"max_orders": 1000, "max_branches": 1, "max_users": 2}'::jsonb, 'v1'),
    ('start', 'inventory', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'dashboard', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'users', TRUE, 'basic', '{"max_users": 2}'::jsonb, 'v1'),
    ('start', 'company_settings', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'pos_checkout', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'inventory_catalog', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'inventory_item_management', TRUE, 'basic', '{}'::jsonb, 'v1');

-- SHOP Plan
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('shop', 'pos', TRUE, 'standard', '{"max_orders": 3000, "max_branches": 1, "max_users": 5}'::jsonb, 'v1'),
    ('shop', 'inventory', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'dashboard', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'users', TRUE, 'standard', '{"max_users": 5}'::jsonb, 'v1'),
    ('shop', 'company_settings', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'suppliers', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'suppliers_management', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'pos_checkout', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'pos_returns', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'inventory_catalog', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'inventory_item_management', TRUE, 'standard', '{}'::jsonb, 'v1');

-- GROWTH Plan
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('growth', 'pos', TRUE, 'advanced', '{"max_orders": 8000, "max_branches": 2, "max_users": 10}'::jsonb, 'v1'),
    ('growth', 'inventory', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'dashboard', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'users', TRUE, 'advanced', '{"max_users": 10}'::jsonb, 'v1'),
    ('growth', 'company_settings', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'suppliers', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('growth', 'clients', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('growth', 'attendance', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('growth', 'inventory_bounced_back', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('growth', 'pos_checkout', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'pos_returns', TRUE, 'advanced', '{}'::jsonb, 'v1');

-- BUSINESS Plan
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('business', 'pos', TRUE, 'advanced_plus', '{"max_orders": 20000, "max_branches": 5, "max_users": 25}'::jsonb, 'v1'),
    ('business', 'inventory', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('business', 'dashboard', TRUE, 'multi_branch', '{}'::jsonb, 'v1'),
    ('business', 'users', TRUE, 'advanced', '{"max_users": 25}'::jsonb, 'v1'),
    ('business', 'company_settings', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('business', 'suppliers', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('business', 'clients', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('business', 'attendance', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('business', 'finance', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('business', 'repair_center', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('business', 'pos_checkout', TRUE, 'advanced', '{}'::jsonb, 'v1');

-- ENTERPRISE Plan
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('enterprise', 'pos', TRUE, 'custom', '{"max_orders": 50000, "max_branches": 10, "max_users": 50}'::jsonb, 'v1'),
    ('enterprise', 'inventory', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'dashboard', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'users', TRUE, 'custom', '{"max_users": 50}'::jsonb, 'v1'),
    ('enterprise', 'company_settings', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'suppliers', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('enterprise', 'clients', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('enterprise', 'attendance', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('enterprise', 'finance', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('enterprise', 'repair_center', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('enterprise', 'api_access', TRUE, 'full', '{}'::jsonb, 'v1');
