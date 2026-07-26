-- Durable control-plane recovery copy and audit trail. Redis remains authoritative at runtime.

CREATE SEQUENCE IF NOT EXISTS public.notification_control_version_seq
    AS BIGINT START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS public.notification_control_switch (
    scope              TEXT        NOT NULL,
    component_key      TEXT        NOT NULL,
    enabled            BOOLEAN     NOT NULL DEFAULT TRUE,
    suppression_mode   TEXT        NOT NULL DEFAULT 'SUPPRESS',
    reason             TEXT,
    disabled_until     TIMESTAMPTZ,
    changed_by_user_id INTEGER     NOT NULL,
    changed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    control_version    BIGINT      NOT NULL,
    CONSTRAINT pk_ncs PRIMARY KEY (scope, component_key),
    CONSTRAINT chk_ncs_scope CHECK (scope IN
        ('module','worker','channel','provider','api','tenant','category','type','branch')),
    CONSTRAINT chk_ncs_suppression CHECK (
        suppression_mode IN ('SUPPRESS','QUEUE','CANCEL')),
    CONSTRAINT chk_ncs_reason_required CHECK (enabled = TRUE OR reason IS NOT NULL),
    CONSTRAINT chk_ncs_until CHECK (disabled_until IS NULL OR enabled = FALSE),
    CONSTRAINT chk_ncs_version CHECK (control_version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_ncs_disabled
    ON public.notification_control_switch (scope, enabled)
    WHERE enabled = FALSE;
CREATE INDEX IF NOT EXISTS idx_ncs_expiring
    ON public.notification_control_switch (disabled_until)
    WHERE disabled_until IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.notification_control_audit (
    audit_id              BIGSERIAL   PRIMARY KEY,
    scope                 TEXT        NOT NULL,
    component_key         TEXT        NOT NULL,
    from_enabled          BOOLEAN,
    to_enabled            BOOLEAN     NOT NULL,
    from_suppression      TEXT,
    to_suppression        TEXT        NOT NULL,
    reason                TEXT,
    disabled_until        TIMESTAMPTZ,
    actor_user_id         INTEGER     NOT NULL,
    actor_ip              INET,
    control_version       BIGINT      NOT NULL,
    queue_depth_at_change INTEGER,
    occurred_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_nca_scope CHECK (scope IN
        ('module','worker','channel','provider','api','tenant','category','type','branch')),
    CONSTRAINT chk_nca_from_suppression CHECK (
        from_suppression IS NULL OR from_suppression IN ('SUPPRESS','QUEUE','CANCEL')),
    CONSTRAINT chk_nca_to_suppression CHECK (
        to_suppression IN ('SUPPRESS','QUEUE','CANCEL')),
    CONSTRAINT chk_nca_version CHECK (control_version >= 1),
    CONSTRAINT chk_nca_queue_depth CHECK (
        queue_depth_at_change IS NULL OR queue_depth_at_change >= 0)
);

CREATE INDEX IF NOT EXISTS idx_nca_component
    ON public.notification_control_audit (scope, component_key, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_nca_actor
    ON public.notification_control_audit (actor_user_id, occurred_at DESC);

INSERT INTO public.notification_control_switch (
    scope, component_key, enabled, suppression_mode, reason,
    changed_by_user_id, control_version
) VALUES
    ('module','MODULE',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('module','PUBLISH',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('worker','FANOUT',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('worker','DISPATCH',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('worker','BROADCAST_PLANNING',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('worker','BROADCAST_MATERIALIZE',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('worker','STUCK_CLAIM_REAPER',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('worker','RETENTION',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('worker','DEVICE_REAPER',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('worker','PARTITION_MAINTENANCE',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('worker','CONSISTENCY',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('channel','PUSH',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('channel','IN_APP',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('channel','SSE',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('provider','FCM',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('provider','APNS',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('api','FEED_READ',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('api','DEVICE_REGISTRATION',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq')),
    ('api','BROADCAST_CREATE',TRUE,'SUPPRESS',NULL,0,nextval('public.notification_control_version_seq'))
ON CONFLICT (scope, component_key) DO NOTHING;

INSERT INTO public.platform_capabilities (
    capability_key, module_id, resource, action, scope_type, status, description
) VALUES
    ('notification.control.view',             'web_admin', 'notification_control', 'view',             'global_admin', 'active', 'View notification runtime control state and audit history.'),
    ('notification.control.toggle.component', 'web_admin', 'notification_control', 'toggle_component', 'global_admin', 'active', 'Toggle a notification worker, channel, provider, or API surface.'),
    ('notification.control.toggle.module',    'web_admin', 'notification_control', 'toggle_module',    'global_admin', 'active', 'Toggle the Notification Center master runtime switch.'),
    ('notification.control.toggle.tenant',    'web_admin', 'notification_control', 'toggle_tenant',    'global_admin', 'active', 'Toggle Notification Center processing for a tenant.')
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
SELECT 'SupportAdmin', capability_key, 'global_admin', 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'notification.control.view',
    'notification.control.toggle.component',
    'notification.control.toggle.module',
    'notification.control.toggle.tenant'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;

