-- V63__force_attendance_grants.sql
-- Ensure all attendance capabilities are correctly granted to Owner and BranchManager roles

-- 1. Explicitly grant with company scope for Owner (to ensure they have it everywhere)
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode)
SELECT 'Owner', capability_key, 'company', 'allow' 
FROM public.platform_capabilities 
WHERE module_id = 'attendance'
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET grant_mode = 'allow';

-- 2. Explicitly grant with branch scope for Owner
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode)
SELECT 'Owner', capability_key, 'branch', 'allow' 
FROM public.platform_capabilities 
WHERE module_id = 'attendance'
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET grant_mode = 'allow';

-- 3. Explicitly grant with branch scope for BranchManager
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode)
SELECT 'BranchManager', capability_key, 'branch', 'allow' 
FROM public.platform_capabilities 
WHERE module_id = 'attendance'
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET grant_mode = 'allow';

-- 4. Enable the module for all existing tenants by default (in case default_enabled was missed)
INSERT INTO public.tenant_module_overrides (tenant_id, module_id, enabled, reason, source)
SELECT c.id, 'attendance', TRUE, 'attendance_system_launch', 'migration' 
FROM public."Company" c
JOIN public.tenants t ON t.tenant_id = c.id
ON CONFLICT (tenant_id, module_id) DO NOTHING;
