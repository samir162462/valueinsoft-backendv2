-- Completes the HR leave-request producer seam used by the notification pilot.
-- Database changes remain Flyway-owned.

INSERT INTO public.platform_capabilities (
    capability_key, module_id, resource, action, scope_type, status, description
) VALUES
    ('hr.leave.self',   'attendance', 'leave_request', 'create_self', 'self',   'active',
     'Create and view the authenticated employee annual-leave requests.'),
    ('hr.leave.manage', 'attendance', 'leave_request', 'manage',      'branch', 'active',
     'Review and manage annual-leave requests in the assigned branch.')
ON CONFLICT (capability_key) DO UPDATE SET
    module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.role_grants (
    role_id, capability_key, scope_type, grant_mode, grant_version
)
SELECT role_id, 'hr.leave.self', 'self', 'allow', 'v1'
FROM public.role_definitions
WHERE status = 'active'
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;

INSERT INTO public.role_grants (
    role_id, capability_key, scope_type, grant_mode, grant_version
)
SELECT role_id, 'hr.leave.manage', 'branch', 'allow', 'v1'
FROM public.role_definitions
WHERE role_id IN ('Owner', 'BranchManager')
  AND status = 'active'
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
