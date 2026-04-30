-- V69__enterprise_full_access_and_owner_grants.sql

-- 1. Enable missing modules for START, SHOP, GROWTH, BUSINESS, and ENTERPRISE plans
-- This ensures all sidebar items in Aside.js are visible based on the feature matrix
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    -- START Plan
    ('start', 'inventory_transactions', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'inventory_bounced_back', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'client_vouchers', TRUE, 'basic', '{}'::jsonb, 'v1'),
    
    -- SHOP Plan
    ('shop', 'inventory_transactions', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'inventory_bounced_back', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'client_vouchers', TRUE, 'standard', '{}'::jsonb, 'v1'),
    
    -- GROWTH Plan
    ('growth', 'client_vouchers', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('growth', 'inventory_scanner', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    
    -- BUSINESS Plan
    ('business', 'client_vouchers', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('business', 'inventory_scanner', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('business', 'inventory_transactions', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    ('business', 'inventory_bounced_back', TRUE, 'advanced', '{}'::jsonb, 'v1'),
    
    -- ENTERPRISE Plan
    ('enterprise', 'inventory_item_management', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'inventory_transactions', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'inventory_bounced_back', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'pos_returns', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'inventory_categories', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'finance_sales_reports', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'finance_expenses', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'client_vouchers', TRUE, 'custom', '{}'::jsonb, 'v1'),
    ('enterprise', 'inventory_scanner', TRUE, 'custom', '{}'::jsonb, 'v1')
ON CONFLICT (package_id, module_id) DO UPDATE SET 
    enabled = EXCLUDED.enabled,
    mode = EXCLUDED.mode;

-- 2. Grant inventory read access to Accountant so they can see the Inventory List and History
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
VALUES 
    ('Accountant', 'inventory.item.read', 'company', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO NOTHING;

-- 3. Ensure InventoryClerk can edit adjustments (required for the Damages List view)
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
VALUES 
    ('InventoryClerk', 'inventory.adjustment.edit', 'branch', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO NOTHING;

-- 4. Grant ALL platform capabilities to Owner role for full authority
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'Owner', c.capability_key, s.scope_type, 'allow', 'v1'
FROM public.platform_capabilities c
CROSS JOIN (SELECT unnest(ARRAY['company', 'branch']) as scope_type) s
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET grant_mode = 'allow';
