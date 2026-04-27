-- V65__restore_package_capabilities.sql
-- Fix the capability decrease by ensuring all modules from the feature matrix are correctly assigned
-- Using specific module IDs expected by the frontend appShellAccess configuration

-- 1. Clear existing policies for the 5 tiers to rebuild correctly
DELETE FROM public.package_module_policies WHERE package_id IN ('start', 'shop', 'growth', 'business', 'enterprise');

-- 2. START Plan (Base for all)
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('start', 'pos', TRUE, 'basic', '{"max_orders": 1000, "max_branches": 1, "max_users": 2}'::jsonb, 'v1'),
    ('start', 'pos_checkout', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'inventory', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'inventory_catalog', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'inventory_item_management', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'inventory_categories', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'dashboard', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'user_management', TRUE, 'basic', '{"max_users": 2}'::jsonb, 'v1'),
    ('start', 'company_billing_settings', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'clients_management', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'suppliers_management', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'attendance', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'company_settings', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'profile', TRUE, 'basic', '{}'::jsonb, 'v1');

-- 3. SHOP Plan (Start + Standard features)
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('shop', 'pos', TRUE, 'standard', '{"max_orders": 3000, "max_branches": 1, "max_users": 5}'::jsonb, 'v1'),
    ('shop', 'pos_checkout', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'inventory', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'inventory_catalog', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'inventory_item_management', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'inventory_categories', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'dashboard', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'user_management', TRUE, 'standard', '{"max_users": 5}'::jsonb, 'v1'),
    ('shop', 'company_billing_settings', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'clients_management', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'suppliers_management', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'attendance', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'company_settings', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'profile', TRUE, 'standard', '{}'::jsonb, 'v1');

-- 4. GROWTH Plan (Shop + Multi-branch + CRM)
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('growth', 'pos', TRUE, 'advanced', '{"max_orders": 8000, "max_branches": 2, "max_users": 10}'::jsonb, 'v1'),
    ('growth', 'pos_checkout', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'inventory', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'inventory_catalog', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'inventory_item_management', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'inventory_transactions', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('growth', 'inventory_bounced_back', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('growth', 'dashboard', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'user_management', TRUE, 'advanced', '{"max_users": 10}'::jsonb, 'v1'),
    ('growth', 'clients_management', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'suppliers_management', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('growth', 'attendance', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'company_settings', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'profile', TRUE, 'advanced', '{}'::jsonb, 'v1');

-- 5. BUSINESS Plan (Growth + Finance + Repair)
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('business', 'pos', TRUE, 'advanced_plus', '{"max_orders": 20000, "max_branches": 5, "max_users": 25}'::jsonb, 'v1'),
    ('business', 'pos_checkout', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('business', 'inventory', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('business', 'inventory_catalog', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('business', 'inventory_item_management', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('business', 'dashboard', TRUE, 'multi_branch', '{}'::jsonb, 'v1'),
    ('business', 'user_management', TRUE, 'advanced', '{"max_users": 25}'::jsonb, 'v1'),
    ('business', 'clients_management', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('business', 'suppliers_management', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('business', 'attendance', TRUE, 'advanced_plus', '{}'::jsonb, 'v1'),
    ('business', 'finance', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('business', 'finance_sales_reports', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('business', 'finance_expenses', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('business', 'repair_center', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('business', 'company_settings', TRUE, 'advanced', '{}'::jsonb, 'v1');

-- 6. ENTERPRISE Plan (Full)
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('enterprise', 'pos', TRUE, 'custom', '{"max_orders": 50000, "max_branches": 10, "max_users": 50}'::jsonb, 'v1'),
    ('enterprise', 'pos_checkout', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'inventory', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'inventory_catalog', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'dashboard', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'user_management', TRUE, 'custom', '{"max_users": 50}'::jsonb, 'v1'),
    ('enterprise', 'clients_management', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'suppliers_management', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'attendance', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'finance', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('enterprise', 'repair_center', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('enterprise', 'api_access', TRUE, 'full', '{}'::jsonb, 'v1'),
    ('enterprise', 'company_settings', TRUE, 'custom', '{}'::jsonb, 'v1');
