-- V62__attendance_capabilities.sql
-- Register attendance module and capabilities in the public configuration schema

-- 1. Register Module
INSERT INTO public.platform_modules (module_id, display_name, category, status, default_enabled, description)
VALUES ('attendance', 'Attendance System', 'hr', 'active', TRUE, 'Staff attendance tracking, shifts, and kiosk actions.')
ON CONFLICT (module_id) DO UPDATE SET 
    display_name = EXCLUDED.display_name,
    category = EXCLUDED.category,
    status = EXCLUDED.status,
    default_enabled = EXCLUDED.default_enabled,
    description = EXCLUDED.description;

-- 2. Register Capabilities
INSERT INTO public.platform_capabilities (capability_key, module_id, resource, action, scope_type, status, description)
VALUES 
('hr.dashboard.view', 'attendance', 'dashboard', 'view', 'branch', 'active', 'View HR dashboard for the branch.'),
('hr.employee.read', 'attendance', 'employee', 'read', 'branch', 'active', 'View employees in the branch.'),
('hr.employee.create', 'attendance', 'employee', 'create', 'branch', 'active', 'Register new employees.'),
('hr.shift.read', 'attendance', 'shift', 'read', 'branch', 'active', 'View shift definitions.'),
('hr.shift.create', 'attendance', 'shift', 'create', 'branch', 'active', 'Create new shift definitions.'),
('hr.shift.assign', 'attendance', 'shift', 'assign', 'branch', 'active', 'Assign employees to shifts.'),
('attendance.kiosk.use', 'attendance', 'kiosk', 'use', 'branch', 'active', 'Access and use the attendance kiosk.'),
('attendance.report.view', 'attendance', 'report', 'view', 'branch', 'active', 'View attendance reports.'),
('attendance.correction.create', 'attendance', 'correction', 'create', 'branch', 'active', 'Manually correct attendance records.')
ON CONFLICT (capability_key) DO UPDATE SET
    module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description;

-- 3. Grant to System Roles (Owner, BranchManager)
-- We use a subquery to ensure we only grant what exists
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode)
SELECT 'Owner', capability_key, scope_type, 'allow' 
FROM public.platform_capabilities 
WHERE module_id = 'attendance'
ON CONFLICT (role_id, capability_key, scope_type) DO NOTHING;

INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode)
SELECT 'BranchManager', capability_key, scope_type, 'allow' 
FROM public.platform_capabilities 
WHERE module_id = 'attendance'
ON CONFLICT (role_id, capability_key, scope_type) DO NOTHING;
