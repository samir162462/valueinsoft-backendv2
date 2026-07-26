-- Phase 3 device self-management and lookup support.

INSERT INTO public.platform_capabilities (
    capability_key, module_id, resource, action, scope_type, status, description
) VALUES (
    'notification.device.manage.self',
    'profile',
    'notification_device',
    'manage',
    'self',
    'active',
    'Register, rotate and revoke push devices for the authenticated user.'
)
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
SELECT role_id, 'notification.device.manage.self', 'self', 'allow', 'v1'
FROM public.role_definitions
WHERE status = 'active'
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;

CREATE INDEX IF NOT EXISTS idx_npo_uuid_lookup
    ON public.notification_push_outbox (outbox_uuid);

CREATE INDEX IF NOT EXISTS idx_nd_identity_lookup
    ON public.notification_device (
        install_id, provider, app_bundle_id, apns_environment, user_id, company_id
    );

ALTER TABLE public.notification_push_outbox
    DROP CONSTRAINT IF EXISTS chk_npo_cancel_reason;

ALTER TABLE public.notification_push_outbox
    ADD CONSTRAINT chk_npo_cancel_reason CHECK (
        cancelled_reason IS NULL OR cancelled_reason IN
        ('QUIET_HOURS','DND','PREFERENCE_MUTED','MIN_PRIORITY','DEVICE_REVOKED',
         'DEVICE_BINDING_CHANGED','BROADCAST_CANCELLED','TEMPLATE_MISSING',
         'PAYLOAD_TOO_LARGE','STALE_ON_RESUME','PROVIDER_DISABLED','CHANNEL_DISABLED',
         'CONTROL_DISABLED'));
