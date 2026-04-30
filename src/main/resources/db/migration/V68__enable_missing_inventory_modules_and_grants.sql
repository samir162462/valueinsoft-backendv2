-- V68__enable_missing_inventory_modules_and_grants.sql
-- 1. Enable missing inventory modules for SHOP and START plans to ensure History and Damages are visible
INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version)
VALUES
    ('start', 'inventory_transactions', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('start', 'inventory_bounced_back', TRUE, 'basic', '{}'::jsonb, 'v1'),
    ('shop', 'inventory_transactions', TRUE, 'standard', '{}'::jsonb, 'v1'),
    ('shop', 'inventory_bounced_back', TRUE, 'standard', '{}'::jsonb, 'v1')
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
