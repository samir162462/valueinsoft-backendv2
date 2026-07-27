-- Multiple platform-wide notification worker shutdown schedules.
--
-- PostgreSQL is the durable configuration/audit source. Runtime schedule evaluation is
-- performed from the Redis-backed in-memory snapshot so an active shutdown window never
-- needs a database poll to remain active or to end.

CREATE TABLE public.notification_shutdown_schedule (
    schedule_uuid      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(120) NOT NULL,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    quiet_start        TIME         NOT NULL,
    quiet_end          TIME         NOT NULL,
    timezone           VARCHAR(100) NOT NULL,
    days_of_week       SMALLINT[]   NOT NULL DEFAULT ARRAY[1,2,3,4,5,6,7]::SMALLINT[],
    effective_from     DATE,
    effective_until    DATE,
    reason             VARCHAR(500) NOT NULL,
    created_by_user_id INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by_user_id INTEGER      NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_nss_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_nss_non_zero_window CHECK (quiet_start <> quiet_end),
    CONSTRAINT chk_nss_timezone CHECK (BTRIM(timezone) <> ''),
    CONSTRAINT chk_nss_days_not_empty CHECK (cardinality(days_of_week) > 0),
    CONSTRAINT chk_nss_days_range CHECK (
        days_of_week <@ ARRAY[1,2,3,4,5,6,7]::SMALLINT[]),
    CONSTRAINT chk_nss_effective_range CHECK (
        effective_until IS NULL OR effective_from IS NULL
        OR effective_until >= effective_from),
    CONSTRAINT chk_nss_reason CHECK (BTRIM(reason) <> '')
);

CREATE INDEX idx_nss_enabled
    ON public.notification_shutdown_schedule (enabled, updated_at DESC)
    WHERE enabled = TRUE;

CREATE INDEX idx_nss_effective_range
    ON public.notification_shutdown_schedule (effective_from, effective_until)
    WHERE enabled = TRUE;

CREATE TABLE public.notification_shutdown_schedule_audit (
    audit_id          BIGSERIAL    PRIMARY KEY,
    schedule_uuid     UUID         NOT NULL,
    action            VARCHAR(10)  NOT NULL,
    old_row           JSONB,
    new_row           JSONB,
    actor_user_id     INTEGER      NOT NULL DEFAULT 0,
    occurred_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_nssa_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT chk_nssa_payload CHECK (old_row IS NOT NULL OR new_row IS NOT NULL)
);

CREATE INDEX idx_nssa_schedule
    ON public.notification_shutdown_schedule_audit (schedule_uuid, occurred_at DESC);

CREATE OR REPLACE FUNCTION public.audit_notification_shutdown_schedule()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO public.notification_shutdown_schedule_audit (
            schedule_uuid, action, new_row, actor_user_id
        ) VALUES (
            NEW.schedule_uuid, 'CREATE', to_jsonb(NEW), NEW.updated_by_user_id
        );
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO public.notification_shutdown_schedule_audit (
            schedule_uuid, action, old_row, new_row, actor_user_id
        ) VALUES (
            NEW.schedule_uuid, 'UPDATE', to_jsonb(OLD), to_jsonb(NEW),
            NEW.updated_by_user_id
        );
        RETURN NEW;
    END IF;

    INSERT INTO public.notification_shutdown_schedule_audit (
        schedule_uuid, action, old_row, actor_user_id
    ) VALUES (
        OLD.schedule_uuid, 'DELETE', to_jsonb(OLD), OLD.updated_by_user_id
    );
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_notification_shutdown_schedule_audit
AFTER INSERT OR UPDATE OR DELETE ON public.notification_shutdown_schedule
FOR EACH ROW EXECUTE FUNCTION public.audit_notification_shutdown_schedule();

COMMENT ON TABLE public.notification_shutdown_schedule IS
    'Durable platform-admin schedules that park every notification database worker. '
    'Rows are mirrored to Redis; workers never poll this table.';
COMMENT ON COLUMN public.notification_shutdown_schedule.days_of_week IS
    'ISO-8601 day numbers: Monday=1 through Sunday=7; the day refers to quiet_start.';
COMMENT ON TABLE public.notification_shutdown_schedule_audit IS
    'Immutable before/after history for platform-admin shutdown schedule changes.';
