-- Provider attempt audit (weekly partitions) and durable broadcast planning state.

CREATE TABLE IF NOT EXISTS public.notification_delivery_attempt (
    attempted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    attempt_id          BIGSERIAL   NOT NULL,
    outbox_uuid         UUID        NOT NULL,
    outbox_created_at   TIMESTAMPTZ NOT NULL,
    company_id          INTEGER     NOT NULL,
    broadcast_id        BIGINT,
    device_id           BIGINT      NOT NULL,
    provider            TEXT        NOT NULL,
    attempt_no          INTEGER     NOT NULL,
    http_status         INTEGER,
    provider_message_id TEXT,
    error_code          TEXT,
    error_class         TEXT        NOT NULL,
    retry_after_seconds INTEGER,
    apns_unique_id      TEXT,
    invalidation_at     TIMESTAMPTZ,
    payload_bytes       INTEGER,
    latency_ms          INTEGER     NOT NULL,
    PRIMARY KEY (attempted_at, attempt_id),
    CONSTRAINT chk_nda_provider CHECK (provider IN ('fcm','apns','webpush')),
    CONSTRAINT chk_nda_error_class CHECK (error_class IN
        ('success','retryable','permanent','transport','cancelled')),
    CONSTRAINT chk_nda_attempt_no CHECK (attempt_no >= 1),
    CONSTRAINT chk_nda_http_status CHECK (
        http_status IS NULL OR http_status BETWEEN 100 AND 599),
    CONSTRAINT chk_nda_retry_after CHECK (
        retry_after_seconds IS NULL OR retry_after_seconds >= 0),
    CONSTRAINT chk_nda_payload_bytes CHECK (
        payload_bytes IS NULL OR payload_bytes BETWEEN 1 AND 3800),
    CONSTRAINT chk_nda_latency CHECK (latency_ms >= 0)
) PARTITION BY RANGE (attempted_at);

CREATE INDEX IF NOT EXISTS idx_nda_outbox
    ON public.notification_delivery_attempt (outbox_uuid);
CREATE INDEX IF NOT EXISTS idx_nda_device
    ON public.notification_delivery_attempt (device_id, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_nda_errors
    ON public.notification_delivery_attempt (error_code, attempted_at DESC)
    WHERE error_class <> 'success';
CREATE INDEX IF NOT EXISTS idx_nda_company
    ON public.notification_delivery_attempt (company_id, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_nda_broadcast
    ON public.notification_delivery_attempt (broadcast_id, error_class)
    WHERE broadcast_id IS NOT NULL;

DO $$
DECLARE
    week_start DATE := date_trunc('week', CURRENT_DATE)::DATE;
    part_start DATE;
    part_end DATE;
    part_name TEXT;
    week_offset INTEGER;
BEGIN
    FOR week_offset IN 0..3 LOOP
        part_start := week_start + (week_offset * 7);
        part_end := week_start + ((week_offset + 1) * 7);
        part_name := 'notification_delivery_attempt_' || to_char(part_start, 'IYYY_IW');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS public.%I PARTITION OF public.notification_delivery_attempt '
            || 'FOR VALUES FROM (%L) TO (%L)',
            part_name, part_start::TIMESTAMPTZ, part_end::TIMESTAMPTZ);
    END LOOP;
END;
$$;

CREATE TABLE IF NOT EXISTS public.notification_delivery_attempt_default
    PARTITION OF public.notification_delivery_attempt DEFAULT;

CREATE TABLE IF NOT EXISTS public.notification_broadcast (
    broadcast_id          BIGSERIAL   PRIMARY KEY,
    broadcast_uuid        UUID        NOT NULL DEFAULT gen_random_uuid(),
    scope                 TEXT        NOT NULL,
    company_id            INTEGER,
    branch_id             INTEGER,
    type_key              TEXT        NOT NULL,
    audience_predicate    JSONB       NOT NULL,
    params                JSONB       NOT NULL DEFAULT '{}'::jsonb,
    priority              TEXT        NOT NULL DEFAULT 'normal',
    idempotency_key       TEXT        NOT NULL,
    request_fingerprint   BYTEA       NOT NULL,
    status                TEXT        NOT NULL DEFAULT 'draft',
    targeted_count        INTEGER     NOT NULL DEFAULT 0,
    materialized_count    INTEGER     NOT NULL DEFAULT 0,
    skipped_count         INTEGER     NOT NULL DEFAULT 0,
    outbox_created_count  INTEGER     NOT NULL DEFAULT 0,
    sent_count            INTEGER     NOT NULL DEFAULT 0,
    failed_count          INTEGER     NOT NULL DEFAULT 0,
    cancelled_count       INTEGER     NOT NULL DEFAULT 0,
    dead_count            INTEGER     NOT NULL DEFAULT 0,
    batches_total         INTEGER     NOT NULL DEFAULT 0,
    batches_completed     INTEGER     NOT NULL DEFAULT 0,
    scheduled_at          TIMESTAMPTZ,
    planning_started_at   TIMESTAMPTZ,
    planning_completed_at TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    claimed_by            TEXT,
    claim_expires_at      TIMESTAMPTZ,
    created_by_user_id    INTEGER     NOT NULL,
    confirmed_by_user_id  INTEGER,
    approved_by_user_id   INTEGER,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_nb_uuid UNIQUE (broadcast_uuid),
    CONSTRAINT uq_nb_idem UNIQUE (idempotency_key),
    CONSTRAINT fk_nb_type FOREIGN KEY (type_key)
        REFERENCES public.notification_type_catalog (type_key),
    CONSTRAINT chk_nb_scope CHECK (scope IN ('company','branch','platform')),
    CONSTRAINT chk_nb_status CHECK (status IN
        ('draft','scheduled','planning','materializing','completed',
         'partially_failed','failed','cancelled')),
    CONSTRAINT chk_nb_priority CHECK (priority IN ('critical','high','normal','low')),
    CONSTRAINT chk_nb_audience CHECK (jsonb_typeof(audience_predicate) = 'object'),
    CONSTRAINT chk_nb_params CHECK (jsonb_typeof(params) = 'object'),
    CONSTRAINT chk_nb_company_scope CHECK (scope = 'platform' OR company_id IS NOT NULL),
    CONSTRAINT chk_nb_branch_scope CHECK (scope <> 'branch' OR branch_id IS NOT NULL),
    CONSTRAINT chk_nb_counts CHECK (
        targeted_count >= 0 AND materialized_count >= 0 AND skipped_count >= 0
        AND outbox_created_count >= 0 AND sent_count >= 0 AND failed_count >= 0
        AND cancelled_count >= 0 AND dead_count >= 0
        AND materialized_count + skipped_count <= targeted_count
        AND batches_total >= 0 AND batches_completed >= 0
        AND batches_completed <= batches_total),
    CONSTRAINT chk_nb_planned CHECK (
        status NOT IN ('materializing','completed','partially_failed')
        OR planning_completed_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_nb_claimable
    ON public.notification_broadcast (status, scheduled_at)
    WHERE status IN ('scheduled','planning','materializing');
CREATE INDEX IF NOT EXISTS idx_nb_company
    ON public.notification_broadcast (company_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.notification_broadcast_target (
    broadcast_id   BIGINT      NOT NULL,
    company_id     INTEGER     NOT NULL,
    user_id        INTEGER     NOT NULL,
    branch_id      INTEGER,
    batch_no       INTEGER     NOT NULL,
    status         TEXT        NOT NULL DEFAULT 'pending',
    skip_reason    TEXT,
    recipient_uuid UUID,
    outbox_count   INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at   TIMESTAMPTZ,
    last_error     TEXT,
    CONSTRAINT pk_nbt PRIMARY KEY (broadcast_id, company_id, user_id),
    CONSTRAINT fk_nbt_broadcast FOREIGN KEY (broadcast_id)
        REFERENCES public.notification_broadcast (broadcast_id) ON DELETE CASCADE,
    CONSTRAINT chk_nbt_batch CHECK (batch_no >= 1),
    CONSTRAINT chk_nbt_outbox_count CHECK (outbox_count >= 0),
    CONSTRAINT chk_nbt_status CHECK (status IN ('pending','materialized','skipped','failed')),
    CONSTRAINT chk_nbt_skip CHECK ((status = 'skipped') = (skip_reason IS NOT NULL)),
    CONSTRAINT chk_nbt_skip_reason CHECK (skip_reason IS NULL OR skip_reason IN
        ('user_inactive','left_company','capability_revoked','no_active_device',
         'preference_opted_out','type_deprecated','broadcast_cancelled')),
    CONSTRAINT chk_nbt_materialized CHECK (
        status <> 'materialized' OR recipient_uuid IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_nbt_batch
    ON public.notification_broadcast_target (broadcast_id, batch_no, status);
CREATE INDEX IF NOT EXISTS idx_nbt_status
    ON public.notification_broadcast_target (broadcast_id, status);

CREATE TABLE IF NOT EXISTS public.notification_broadcast_batch (
    batch_id           BIGSERIAL   PRIMARY KEY,
    broadcast_id       BIGINT      NOT NULL,
    company_id         INTEGER     NOT NULL,
    batch_no           INTEGER     NOT NULL,
    target_count       INTEGER     NOT NULL,
    status             TEXT        NOT NULL DEFAULT 'pending',
    attempt_count      INTEGER     NOT NULL DEFAULT 0,
    max_attempts       INTEGER     NOT NULL DEFAULT 5,
    materialized_count INTEGER     NOT NULL DEFAULT 0,
    skipped_count      INTEGER     NOT NULL DEFAULT 0,
    outbox_created     INTEGER     NOT NULL DEFAULT 0,
    claimed_by         TEXT,
    claimed_at         TIMESTAMPTZ,
    claim_expires_at   TIMESTAMPTZ,
    next_attempt_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error         TEXT,
    completed_at       TIMESTAMPTZ,
    CONSTRAINT fk_nbb_broadcast FOREIGN KEY (broadcast_id)
        REFERENCES public.notification_broadcast (broadcast_id) ON DELETE CASCADE,
    CONSTRAINT uq_nbb_no UNIQUE (broadcast_id, batch_no),
    CONSTRAINT chk_nbb_batch CHECK (batch_no >= 1),
    CONSTRAINT chk_nbb_status CHECK (status IN
        ('pending','claimed','completed','failed','dead','cancelled')),
    CONSTRAINT chk_nbb_claim CHECK (
        status <> 'claimed' OR claim_expires_at IS NOT NULL),
    CONSTRAINT chk_nbb_counts CHECK (
        target_count >= 0 AND materialized_count >= 0 AND skipped_count >= 0
        AND outbox_created >= 0
        AND materialized_count + skipped_count <= target_count),
    CONSTRAINT chk_nbb_attempts CHECK (
        attempt_count >= 0 AND max_attempts >= 1)
);

CREATE INDEX IF NOT EXISTS idx_nbb_claimable
    ON public.notification_broadcast_batch (next_attempt_at, batch_id)
    WHERE status IN ('pending','failed');
CREATE INDEX IF NOT EXISTS idx_nbb_stuck
    ON public.notification_broadcast_batch (claim_expires_at)
    WHERE status = 'claimed';

