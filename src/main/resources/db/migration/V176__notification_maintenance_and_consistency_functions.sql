-- Partition creation, bounded dedup cleanup, retention overrides, and read-only
-- consistency checks. Destructive feed retention remains in the Phase 8 Java worker.

CREATE TABLE IF NOT EXISTS public.notification_tenant_retention (
    schema_name   TEXT        NOT NULL,
    category      TEXT        NOT NULL,
    retention_days INTEGER    NOT NULL,
    updated_by_user_id INTEGER,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (schema_name, category),
    CONSTRAINT chk_ntr_schema CHECK (schema_name ~ '^c_[0-9]+$'),
    CONSTRAINT chk_ntr_category CHECK (category IN
        ('operational','financial','hr','security','system','marketing')),
    CONSTRAINT chk_ntr_days CHECK (retention_days BETWEEN 7 AND 3650)
);

CREATE OR REPLACE FUNCTION public.notification_partition_maintenance(
    p_months_ahead INTEGER DEFAULT 3
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    month_start DATE := date_trunc('month', CURRENT_DATE)::DATE;
    week_start DATE := date_trunc('week', CURRENT_DATE)::DATE;
    part_start DATE;
    part_end DATE;
    part_name TEXT;
    offset_no INTEGER;
    created_count INTEGER := 0;
BEGIN
    IF p_months_ahead < 0 OR p_months_ahead > 24 THEN
        RAISE EXCEPTION 'months ahead must be between 0 and 24'
            USING ERRCODE = '22023';
    END IF;

    PERFORM pg_advisory_xact_lock(
        hashtextextended('notif:0:lock:partition-maint', 0));

    FOR offset_no IN 0..p_months_ahead LOOP
        part_start := (month_start + make_interval(months => offset_no))::DATE;
        part_end := (month_start + make_interval(months => offset_no + 1))::DATE;
        part_name := 'notification_push_outbox_' || to_char(part_start, 'YYYY_MM');
        IF to_regclass('public.' || part_name) IS NULL THEN
            EXECUTE format(
                'CREATE TABLE public.%I PARTITION OF public.notification_push_outbox '
                || 'FOR VALUES FROM (%L) TO (%L)',
                part_name,
                part_start::TIMESTAMP AT TIME ZONE 'UTC',
                part_end::TIMESTAMP AT TIME ZONE 'UTC');
            created_count := created_count + 1;
        END IF;
    END LOOP;

    -- Weekly attempt partitions cover the same horizon, plus the partial final month.
    FOR offset_no IN 0..(p_months_ahead * 5 + 4) LOOP
        part_start := week_start + (offset_no * 7);
        part_end := week_start + ((offset_no + 1) * 7);
        part_name := 'notification_delivery_attempt_' || to_char(part_start, 'IYYY_IW');
        IF to_regclass('public.' || part_name) IS NULL THEN
            EXECUTE format(
                'CREATE TABLE public.%I PARTITION OF public.notification_delivery_attempt '
                || 'FOR VALUES FROM (%L) TO (%L)',
                part_name,
                part_start::TIMESTAMP AT TIME ZONE 'UTC',
                part_end::TIMESTAMP AT TIME ZONE 'UTC');
            created_count := created_count + 1;
        END IF;
    END LOOP;

    RETURN created_count;
END;
$$;

CREATE OR REPLACE FUNCTION public.notification_purge_dedup(
    p_retention_days INTEGER DEFAULT 30
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    IF p_retention_days < 1 OR p_retention_days > 365 THEN
        RAISE EXCEPTION 'retention days must be between 1 and 365'
            USING ERRCODE = '22023';
    END IF;

    WITH candidates AS (
        SELECT d.delivery_key
        FROM public.notification_delivery_dedup d
        WHERE d.expires_at < NOW()
          AND d.reserved_at < NOW() - make_interval(days => p_retention_days)
          AND NOT EXISTS (
              SELECT 1
              FROM public.notification_push_outbox o
              WHERE o.outbox_uuid = d.outbox_uuid
                AND o.status NOT IN ('sent','dead','cancelled')
          )
        ORDER BY d.expires_at
        LIMIT 5000
        FOR UPDATE SKIP LOCKED
    )
    DELETE FROM public.notification_delivery_dedup d
    USING candidates c
    WHERE d.delivery_key = c.delivery_key;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$;

CREATE OR REPLACE FUNCTION public.notification_archive_dead_letters(
    p_older_than_days INTEGER DEFAULT 30
)
RETURNS TABLE (
    outbox_uuid UUID,
    company_id INTEGER,
    provider TEXT,
    payload JSONB,
    last_error_code TEXT,
    last_error TEXT,
    created_at TIMESTAMPTZ
)
LANGUAGE sql
STABLE
AS $$
    SELECT o.outbox_uuid, o.company_id, o.provider, o.payload,
           o.last_error_code, o.last_error, o.created_at
    FROM public.notification_push_outbox o
    WHERE o.status = 'dead'
      AND o.created_at < NOW() - make_interval(days => p_older_than_days)
    ORDER BY o.created_at, o.outbox_id;
$$;

CREATE OR REPLACE FUNCTION public.notification_check_aggregate_drift(
    p_schema_name TEXT
)
RETURNS TABLE (
    recipient_id BIGINT,
    aggregate_count INTEGER,
    lineage_count BIGINT
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    IF p_schema_name IS NULL OR p_schema_name !~ '^c_[0-9]+$' THEN
        RAISE EXCEPTION 'Invalid tenant schema name: %', p_schema_name
            USING ERRCODE = '22023';
    END IF;
    RETURN QUERY EXECUTE format(
        'SELECT r.recipient_id, r.aggregate_count, COUNT(e.event_id) '
        || 'FROM %I.notification_recipient r '
        || 'LEFT JOIN %I.notification_recipient_event e USING (recipient_id) '
        || 'GROUP BY r.recipient_id, r.aggregate_count '
        || 'HAVING r.aggregate_count <> COUNT(e.event_id)',
        p_schema_name, p_schema_name);
END;
$$;

CREATE OR REPLACE FUNCTION public.notification_check_orphan_reservations()
RETURNS TABLE (
    delivery_key BYTEA,
    company_id INTEGER,
    outbox_uuid UUID,
    reserved_at TIMESTAMPTZ
)
LANGUAGE sql
STABLE
AS $$
    SELECT d.delivery_key, d.company_id, d.outbox_uuid, d.reserved_at
    FROM public.notification_delivery_dedup d
    WHERE (d.outbox_uuid IS NULL
           AND d.reserved_at < NOW() - INTERVAL '5 minutes')
       OR (d.outbox_uuid IS NOT NULL AND NOT EXISTS (
            SELECT 1
            FROM public.notification_push_outbox o
            WHERE o.outbox_uuid = d.outbox_uuid));
$$;

CREATE OR REPLACE FUNCTION public.notification_check_change_sequence(
    p_schema_name TEXT
)
RETURNS TABLE (
    recipient_id BIGINT,
    user_id INTEGER,
    recipient_sequence BIGINT,
    logged_sequence BIGINT
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    IF p_schema_name IS NULL OR p_schema_name !~ '^c_[0-9]+$' THEN
        RAISE EXCEPTION 'Invalid tenant schema name: %', p_schema_name
            USING ERRCODE = '22023';
    END IF;
    RETURN QUERY EXECUTE format(
        'SELECT r.recipient_id, r.user_id, r.change_sequence, MAX(c.change_sequence) '
        || 'FROM %I.notification_recipient r '
        || 'LEFT JOIN %I.notification_feed_change c USING (recipient_id) '
        || 'GROUP BY r.recipient_id, r.user_id, r.change_sequence '
        || 'HAVING MAX(c.change_sequence) IS NULL '
        || 'OR r.change_sequence <> MAX(c.change_sequence)',
        p_schema_name, p_schema_name);
END;
$$;

