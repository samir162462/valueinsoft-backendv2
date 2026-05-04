-- ==========================================================
-- V86 - POS Offline Admin Operational Capability
-- ==========================================================
-- Adds a high-privilege capability for controlled internal/admin
-- offline POS operations. This migration does not grant the
-- capability to Cashier and does not touch runtime offline data.
-- ==========================================================

INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES (
    'pos.offline.admin.process',
    'pos',
    'offline_sync_admin',
    'process',
    'branch',
    'active',
    'Run controlled offline POS operational actions for a tenant branch.'
)
ON CONFLICT (capability_key) DO UPDATE
SET
    module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.role_grants (
    role_id,
    capability_key,
    scope_type,
    grant_mode,
    grant_version
)
SELECT role_id, 'pos.offline.admin.process', scope_type, 'allow', 'v1'
FROM (
    VALUES
        ('Owner', 'company'),
        ('Admin', 'company'),
        ('SupportAdmin', 'global_admin')
) AS grants(role_id, scope_type)
WHERE EXISTS (
    SELECT 1
    FROM public.role_definitions rd
    WHERE rd.role_id = grants.role_id
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
