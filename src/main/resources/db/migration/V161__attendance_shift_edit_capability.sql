-- Shift editing is a distinct privileged action from creating schedules.
INSERT INTO public.platform_capabilities (capability_key, module_id, resource, action, scope_type, status, description)
VALUES ('hr.shift.edit', 'attendance', 'shift', 'edit', 'branch', 'active', 'Edit active shift definitions.')
ON CONFLICT (capability_key) DO UPDATE SET
    module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description;

INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode)
SELECT role_id, capability_key, scope_type, 'allow'
FROM public.platform_capabilities
CROSS JOIN (VALUES ('Owner'), ('BranchManager')) AS roles(role_id)
WHERE capability_key = 'hr.shift.edit'
ON CONFLICT (role_id, capability_key, scope_type) DO NOTHING;
