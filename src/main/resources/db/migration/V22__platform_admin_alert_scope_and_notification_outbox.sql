ALTER TABLE public.platform_admin_alert_acknowledgments
    ADD COLUMN IF NOT EXISTS target_tenant_id INTEGER NULL,
    ADD COLUMN IF NOT EXISTS target_branch_id INTEGER NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_platform_admin_alert_ack_target_tenant'
    ) THEN
        ALTER TABLE public.platform_admin_alert_acknowledgments
            ADD CONSTRAINT fk_platform_admin_alert_ack_target_tenant
                FOREIGN KEY (target_tenant_id)
                REFERENCES public.tenants (tenant_id)
                ON UPDATE CASCADE
                ON DELETE SET NULL;
    END IF;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_platform_admin_alert_ack_target_branch'
    ) THEN
        ALTER TABLE public.platform_admin_alert_acknowledgments
            ADD CONSTRAINT fk_platform_admin_alert_ack_target_branch
                FOREIGN KEY (target_branch_id)
                REFERENCES public."Branch" ("branchId")
                ON UPDATE CASCADE
                ON DELETE SET NULL;
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_platform_admin_alert_ack_target_tenant
    ON public.platform_admin_alert_acknowledgments (target_tenant_id, acknowledged_at DESC);

CREATE INDEX IF NOT EXISTS idx_platform_admin_alert_ack_target_branch
    ON public.platform_admin_alert_acknowledgments (target_branch_id, acknowledged_at DESC);

CREATE INDEX IF NOT EXISTS idx_platform_admin_alert_ack_scope_lookup
    ON public.platform_admin_alert_acknowledgments (
        alert_key,
        target_tenant_id,
        target_branch_id,
        acknowledged_at DESC
    );

CREATE TABLE IF NOT EXISTS public.platform_alert_notification_outbox (
    notification_id BIGSERIAL PRIMARY KEY,
    alert_key TEXT NOT NULL,
    target_tenant_id INTEGER NULL,
    target_branch_id INTEGER NULL,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'pending',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    requested_by_user_id INTEGER NULL,
    requested_by_user_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ NULL,
    last_error TEXT NULL,
    CONSTRAINT fk_platform_alert_notification_outbox_tenant
        FOREIGN KEY (target_tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_platform_alert_notification_outbox_branch
        FOREIGN KEY (target_branch_id)
        REFERENCES public."Branch" ("branchId")
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_platform_alert_notification_outbox_user
        FOREIGN KEY (requested_by_user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT chk_platform_alert_notification_outbox_alert_key
        CHECK (alert_key ~ '^[a-z][a-z0-9_\\.]*$'),
    CONSTRAINT chk_platform_alert_notification_outbox_event_type
        CHECK (event_type IN ('acknowledged', 'cleared')),
    CONSTRAINT chk_platform_alert_notification_outbox_status
        CHECK (status IN ('pending', 'processing', 'processed', 'failed')),
    CONSTRAINT chk_platform_alert_notification_outbox_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT chk_platform_alert_notification_outbox_payload_object
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT chk_platform_alert_notification_outbox_requested_by_name
        CHECK (length(btrim(requested_by_user_name)) > 0)
);

COMMENT ON TABLE public.platform_alert_notification_outbox IS
    'Outbox queue for future platform alert notifications and operator workflow integrations.';

CREATE INDEX IF NOT EXISTS idx_platform_alert_notification_outbox_status
    ON public.platform_alert_notification_outbox (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_platform_alert_notification_outbox_alert_key
    ON public.platform_alert_notification_outbox (alert_key, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_platform_alert_notification_outbox_scope
    ON public.platform_alert_notification_outbox (
        target_tenant_id,
        target_branch_id,
        created_at DESC
    );
