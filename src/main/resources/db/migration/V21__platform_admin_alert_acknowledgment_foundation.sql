CREATE TABLE IF NOT EXISTS public.platform_admin_alert_acknowledgments (
    acknowledgment_id BIGSERIAL PRIMARY KEY,
    alert_key TEXT NOT NULL,
    note TEXT NULL,
    acknowledged_by_user_id INTEGER NULL,
    acknowledged_by_user_name TEXT NOT NULL,
    acknowledged_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NULL,
    cleared_at TIMESTAMPTZ NULL,
    cleared_by_user_id INTEGER NULL,
    cleared_by_user_name TEXT NULL,
    source TEXT NOT NULL DEFAULT 'platform_admin',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_platform_admin_alert_ack_acknowledged_by
        FOREIGN KEY (acknowledged_by_user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_platform_admin_alert_ack_cleared_by
        FOREIGN KEY (cleared_by_user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT chk_platform_admin_alert_ack_alert_key
        CHECK (alert_key ~ '^[a-z][a-z0-9_\\.]*$'),
    CONSTRAINT chk_platform_admin_alert_ack_acknowledged_by_name
        CHECK (length(btrim(acknowledged_by_user_name)) > 0),
    CONSTRAINT chk_platform_admin_alert_ack_source
        CHECK (source IN ('platform_admin', 'support', 'system')),
    CONSTRAINT chk_platform_admin_alert_ack_expires_after_ack
        CHECK (expires_at IS NULL OR expires_at > acknowledged_at),
    CONSTRAINT chk_platform_admin_alert_ack_cleared_name
        CHECK (cleared_by_user_name IS NULL OR length(btrim(cleared_by_user_name)) > 0),
    CONSTRAINT chk_platform_admin_alert_ack_cleared_after_ack
        CHECK (cleared_at IS NULL OR cleared_at >= acknowledged_at)
);

COMMENT ON TABLE public.platform_admin_alert_acknowledgments IS
    'Operator acknowledgments for platform overview alerts to suppress acknowledged operational noise for a limited time.';

CREATE INDEX IF NOT EXISTS idx_platform_admin_alert_ack_alert_key
    ON public.platform_admin_alert_acknowledgments (alert_key);

CREATE INDEX IF NOT EXISTS idx_platform_admin_alert_ack_active_lookup
    ON public.platform_admin_alert_acknowledgments (alert_key, acknowledged_at DESC);

CREATE INDEX IF NOT EXISTS idx_platform_admin_alert_ack_acknowledged_at
    ON public.platform_admin_alert_acknowledgments (acknowledged_at DESC);
