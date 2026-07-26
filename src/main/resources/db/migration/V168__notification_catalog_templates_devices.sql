-- Notification Center Phase 1: catalog, immutable template versions, devices,
-- binding audit, and deterministic helper functions.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.notification_type_catalog (
    type_key                    TEXT        PRIMARY KEY,
    module_id                   TEXT        NOT NULL,
    category                    TEXT        NOT NULL,
    default_priority            TEXT        NOT NULL DEFAULT 'normal',
    default_channel_in_app      BOOLEAN     NOT NULL DEFAULT TRUE,
    default_channel_push        BOOLEAN     NOT NULL DEFAULT TRUE,
    push_preview_policy         TEXT        NOT NULL DEFAULT 'generic_only',
    group_key_template          TEXT,
    aggregation_window_seconds  INTEGER     NOT NULL DEFAULT 0,
    deep_link_template          TEXT        NOT NULL,
    required_capability         TEXT,
    is_user_mutable             BOOLEAN     NOT NULL DEFAULT TRUE,
    bypasses_quiet_hours        BOOLEAN     NOT NULL DEFAULT FALSE,
    retention_days              INTEGER     NOT NULL DEFAULT 180,
    preview_max_chars           INTEGER     NOT NULL DEFAULT 120,
    producer_rate_limit_per_min INTEGER,
    status                      TEXT        NOT NULL DEFAULT 'active',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ntc_module FOREIGN KEY (module_id)
        REFERENCES public.platform_modules (module_id) ON UPDATE CASCADE,
    CONSTRAINT chk_ntc_type_key CHECK (type_key ~ '^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$'),
    CONSTRAINT chk_ntc_category CHECK (category IN
        ('operational','financial','hr','security','system','marketing')),
    CONSTRAINT chk_ntc_priority CHECK (default_priority IN ('critical','high','normal','low')),
    CONSTRAINT chk_ntc_preview_policy CHECK (push_preview_policy IN ('allowed','generic_only','disabled')),
    CONSTRAINT chk_ntc_status CHECK (status IN ('active','deprecated')),
    CONSTRAINT chk_ntc_retention CHECK (retention_days BETWEEN 7 AND 3650),
    CONSTRAINT chk_ntc_agg_window CHECK (aggregation_window_seconds BETWEEN 0 AND 86400),
    CONSTRAINT chk_ntc_preview_len CHECK (preview_max_chars BETWEEN 20 AND 178),
    CONSTRAINT chk_ntc_rate_limit CHECK (
        producer_rate_limit_per_min IS NULL OR producer_rate_limit_per_min > 0),
    CONSTRAINT chk_ntc_agg_needs_group CHECK (
        aggregation_window_seconds = 0 OR group_key_template IS NOT NULL),
    CONSTRAINT chk_ntc_critical_rules CHECK (
        default_priority <> 'critical'
        OR (is_user_mutable = FALSE AND bypasses_quiet_hours = TRUE)),
    CONSTRAINT chk_ntc_sensitive_preview CHECK (
        category NOT IN ('financial','hr','security') OR push_preview_policy <> 'allowed')
);

CREATE TABLE IF NOT EXISTS public.notification_template (
    template_id          BIGSERIAL   PRIMARY KEY,
    type_key             TEXT        NOT NULL,
    locale               TEXT        NOT NULL,
    template_version     INTEGER     NOT NULL,
    title_template       TEXT        NOT NULL,
    body_template        TEXT        NOT NULL,
    preview_template     TEXT        NOT NULL,
    preview_generic      TEXT        NOT NULL,
    preview_reviewed_by  INTEGER,
    preview_reviewed_at  TIMESTAMPTZ,
    status               TEXT        NOT NULL DEFAULT 'draft',
    published_at         TIMESTAMPTZ,
    retired_at           TIMESTAMPTZ,
    created_by           INTEGER,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_nt_type FOREIGN KEY (type_key)
        REFERENCES public.notification_type_catalog (type_key) ON UPDATE CASCADE,
    CONSTRAINT uq_nt_type_locale_version UNIQUE (type_key, locale, template_version),
    CONSTRAINT chk_nt_locale CHECK (locale ~ '^[a-z]{2}(-[A-Z]{2})?$'),
    CONSTRAINT chk_nt_status CHECK (status IN ('draft','published','retired')),
    CONSTRAINT chk_nt_version CHECK (template_version >= 1),
    CONSTRAINT chk_nt_published CHECK (status <> 'published' OR published_at IS NOT NULL),
    CONSTRAINT chk_nt_preview_reviewed CHECK (
        status <> 'published'
        OR (preview_reviewed_by IS NOT NULL AND preview_reviewed_at IS NOT NULL)),
    CONSTRAINT chk_nt_preview_generic_static CHECK (preview_generic !~ '\{'),
    CONSTRAINT chk_nt_preview_len CHECK (char_length(preview_generic) BETWEEN 5 AND 178)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nt_published_per_type_locale
    ON public.notification_template (type_key, locale)
    WHERE status = 'published';
CREATE INDEX IF NOT EXISTS idx_nt_lookup
    ON public.notification_template (type_key, locale, status);

CREATE TABLE IF NOT EXISTS public.notification_device (
    device_id            BIGSERIAL   PRIMARY KEY,
    device_uuid          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id              INTEGER     NOT NULL,
    company_id           INTEGER     NOT NULL,
    branch_id            INTEGER,
    install_id           TEXT        NOT NULL,
    provider             TEXT        NOT NULL,
    app_bundle_id        TEXT        NOT NULL,
    apns_environment     TEXT        NOT NULL DEFAULT 'none',
    platform             TEXT        NOT NULL,
    binding_version      BIGINT      NOT NULL DEFAULT 1,
    push_token_enc       BYTEA       NOT NULL,
    token_key_id         TEXT        NOT NULL,
    token_hash           BYTEA       NOT NULL,
    app_version          TEXT,
    os_version           TEXT,
    payload_version_max  INTEGER     NOT NULL DEFAULT 1,
    locale               TEXT        NOT NULL DEFAULT 'en',
    timezone             TEXT        NOT NULL DEFAULT 'UTC',
    status               TEXT        NOT NULL DEFAULT 'active',
    consecutive_failures INTEGER     NOT NULL DEFAULT 0,
    invalidated_at       TIMESTAMPTZ,
    registered_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_rotated_at      TIMESTAMPTZ,
    revoked_at           TIMESTAMPTZ,
    revoked_reason       TEXT,

    CONSTRAINT fk_nd_user FOREIGN KEY (user_id)
        REFERENCES public.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_nd_company FOREIGN KEY (company_id)
        REFERENCES public.tenants (tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_nd_uuid UNIQUE (device_uuid),
    CONSTRAINT uq_nd_identity UNIQUE
        (install_id, provider, app_bundle_id, apns_environment, user_id, company_id),
    CONSTRAINT chk_nd_provider CHECK (provider IN ('fcm','apns','webpush')),
    CONSTRAINT chk_nd_platform CHECK (platform IN ('ios','android','web')),
    CONSTRAINT chk_nd_status CHECK (status IN ('active','stale','revoked')),
    CONSTRAINT chk_nd_apns_env CHECK (
        (provider = 'apns' AND apns_environment IN ('sandbox','production'))
        OR (provider <> 'apns' AND apns_environment = 'none')),
    CONSTRAINT chk_nd_failures CHECK (consecutive_failures >= 0),
    CONSTRAINT chk_nd_binding CHECK (binding_version >= 1),
    CONSTRAINT chk_nd_payload_version CHECK (payload_version_max >= 1),
    CONSTRAINT chk_nd_revoked CHECK (status <> 'revoked' OR revoked_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_nd_fanout
    ON public.notification_device (user_id, company_id)
    WHERE status = 'active';
CREATE INDEX IF NOT EXISTS idx_nd_reaper
    ON public.notification_device (status, last_seen_at)
    WHERE status IN ('stale','revoked');

CREATE TABLE IF NOT EXISTS public.notification_device_binding_audit (
    audit_id        BIGSERIAL   PRIMARY KEY,
    device_id       BIGINT      NOT NULL
        REFERENCES public.notification_device (device_id) ON DELETE CASCADE,
    company_id      INTEGER     NOT NULL,
    from_version    BIGINT      NOT NULL,
    to_version      BIGINT      NOT NULL,
    from_user_id    INTEGER,
    to_user_id      INTEGER,
    reason          TEXT        NOT NULL,
    actor_user_id   INTEGER,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ndba_reason CHECK (reason IN
        ('logout','shift_close','company_switch','user_switch','support_revocation',
         'token_reassigned','token_rotated','provider_invalidated','reactivated')),
    CONSTRAINT chk_ndba_version CHECK (to_version > from_version)
);

CREATE INDEX IF NOT EXISTS idx_ndba_device
    ON public.notification_device_binding_audit (device_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION public.notification_priority_rank(p TEXT)
RETURNS INTEGER
LANGUAGE sql
IMMUTABLE
STRICT
AS $$
    SELECT CASE p
        WHEN 'critical' THEN 0
        WHEN 'high' THEN 1
        WHEN 'normal' THEN 2
        WHEN 'low' THEN 3
        ELSE 4
    END;
$$;

CREATE OR REPLACE FUNCTION public.notification_delivery_key(
    p_company_id INTEGER,
    p_event_id BIGINT,
    p_user_id INTEGER,
    p_device_id BIGINT,
    p_channel TEXT,
    p_payload_version INTEGER
)
RETURNS BYTEA
LANGUAGE sql
IMMUTABLE
STRICT
AS $$
    SELECT digest(
        p_company_id::TEXT || '|' || p_event_id::TEXT || '|' || p_user_id::TEXT || '|' ||
        p_device_id::TEXT || '|' || p_channel || '|' || p_payload_version::TEXT,
        'sha256'
    );
$$;

