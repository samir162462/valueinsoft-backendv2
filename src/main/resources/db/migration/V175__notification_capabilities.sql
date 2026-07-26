-- Notification permissions. The existing application role named SupportAdmin is the
-- platform-administrator role described by the Notification Center blueprint.

INSERT INTO public.platform_capabilities (
    capability_key, module_id, resource, action, scope_type, status, description
) VALUES
    ('notification.feed.read.self',          'profile',   'notification_feed',       'read',            'self',         'active', 'Read the authenticated user notification feed.'),
    ('notification.preference.manage.self',  'profile',   'notification_preference', 'manage',          'self',         'active', 'Manage the authenticated user notification preferences.'),
    ('notification.broadcast.send.branch',   'web_admin', 'notification_broadcast',  'send_branch',     'branch',       'active', 'Send a notification broadcast to a branch.'),
    ('notification.broadcast.send.company',  'web_admin', 'notification_broadcast',  'send_company',    'company',      'active', 'Send a notification broadcast to a company.'),
    ('notification.device.manage.any',       'web_admin', 'notification_device',     'manage',          'company',      'active', 'Inspect and revoke notification devices in company scope.'),
    ('notification.admin.view',              'web_admin', 'notification_delivery',   'view',            'global_admin', 'active', 'Inspect notification delivery state and attempts.'),
    ('notification.admin.retry',             'web_admin', 'notification_delivery',   'retry',           'global_admin', 'active', 'Retry an existing notification delivery.'),
    ('notification.admin.resend',            'web_admin', 'notification_delivery',   'resend',          'global_admin', 'active', 'Resend a delivery as a new notification event.')
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
SELECT role_id, capability_key, 'self', 'allow', 'v1'
FROM public.role_definitions
CROSS JOIN (VALUES
    ('notification.feed.read.self'),
    ('notification.preference.manage.self')
) AS capabilities(capability_key)
WHERE status = 'active'
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;

INSERT INTO public.role_grants (
    role_id, capability_key, scope_type, grant_mode, grant_version
) VALUES
    ('Owner',         'notification.broadcast.send.branch',  'branch',       'allow', 'v1'),
    ('BranchManager', 'notification.broadcast.send.branch',  'branch',       'allow', 'v1'),
    ('Owner',         'notification.broadcast.send.company', 'company',      'allow', 'v1'),
    ('Owner',         'notification.device.manage.any',      'company',      'allow', 'v1'),
    ('SupportAdmin',  'notification.admin.view',             'global_admin', 'allow', 'v1'),
    ('SupportAdmin',  'notification.admin.retry',            'global_admin', 'allow', 'v1'),
    ('SupportAdmin',  'notification.admin.resend',           'global_admin', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
