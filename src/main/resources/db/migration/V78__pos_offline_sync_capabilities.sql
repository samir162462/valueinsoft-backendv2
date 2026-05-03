-- ==========================================================
-- V78 - POS Offline Sync Capabilities
-- ==========================================================
-- Adds branch-scoped capabilities used by PosOfflineSyncController.
-- Retry is intentionally granted only to Owner and BranchManager.
-- ==========================================================

INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('pos.device.register', 'pos', 'device', 'register', 'branch', 'active', 'Register POS devices in the active branch.'),
    ('pos.device.heartbeat', 'pos', 'device', 'heartbeat', 'branch', 'active', 'Submit POS device heartbeat in the active branch.'),
    ('pos.bootstrap.read', 'pos', 'bootstrap', 'read', 'branch', 'active', 'Read offline POS bootstrap data in the active branch.'),
    ('pos.offline.sync', 'pos', 'offline_sync', 'sync', 'branch', 'active', 'Upload offline POS sync batches in the active branch.'),
    ('pos.offline.status', 'pos', 'offline_sync', 'status', 'branch', 'active', 'Read offline POS sync batch status in the active branch.'),
    ('pos.offline.errors', 'pos', 'offline_sync', 'errors', 'branch', 'active', 'Read offline POS sync errors in the active branch.'),
    ('pos.offline.retry', 'pos', 'offline_sync', 'retry', 'branch', 'active', 'Retry failed offline POS order imports in the active branch.')
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
) VALUES
    ('Owner', 'pos.device.register', 'company', 'allow', 'v1'),
    ('Owner', 'pos.device.heartbeat', 'company', 'allow', 'v1'),
    ('Owner', 'pos.bootstrap.read', 'company', 'allow', 'v1'),
    ('Owner', 'pos.offline.sync', 'company', 'allow', 'v1'),
    ('Owner', 'pos.offline.status', 'company', 'allow', 'v1'),
    ('Owner', 'pos.offline.errors', 'company', 'allow', 'v1'),
    ('Owner', 'pos.offline.retry', 'company', 'allow', 'v1'),
    ('BranchManager', 'pos.device.register', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.device.heartbeat', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.bootstrap.read', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.offline.sync', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.offline.status', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.offline.errors', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.offline.retry', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.device.heartbeat', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.bootstrap.read', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.offline.sync', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.offline.status', 'branch', 'allow', 'v1'),
    ('Cashier', 'pos.offline.errors', 'branch', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
