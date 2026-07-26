-- Cross-partition logical deduplication plus the range-partitioned push queue.

CREATE TABLE IF NOT EXISTS public.notification_delivery_dedup (
    delivery_key    BYTEA       PRIMARY KEY,
    company_id      INTEGER     NOT NULL,
    event_id        BIGINT      NOT NULL,
    user_id         INTEGER     NOT NULL,
    device_id       BIGINT      NOT NULL,
    channel         TEXT        NOT NULL,
    payload_version INTEGER     NOT NULL,
    outbox_uuid     UUID,
    reserved_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ndd_channel CHECK (channel IN ('push','in_app','email','whatsapp')),
    CONSTRAINT chk_ndd_key_len CHECK (octet_length(delivery_key) = 32),
    CONSTRAINT chk_ndd_payload_version CHECK (payload_version >= 1),
    CONSTRAINT chk_ndd_expiry CHECK (expires_at > reserved_at)
);

CREATE INDEX IF NOT EXISTS idx_ndd_expiry
    ON public.notification_delivery_dedup (expires_at);
CREATE INDEX IF NOT EXISTS idx_ndd_company
    ON public.notification_delivery_dedup (company_id, reserved_at DESC);
CREATE INDEX IF NOT EXISTS idx_ndd_outbox
    ON public.notification_delivery_dedup (outbox_uuid)
    WHERE outbox_uuid IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ndd_abandoned
    ON public.notification_delivery_dedup (reserved_at)
    WHERE outbox_uuid IS NULL;

CREATE TABLE IF NOT EXISTS public.notification_push_outbox (
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    outbox_id               BIGSERIAL   NOT NULL,
    outbox_uuid             UUID        NOT NULL DEFAULT gen_random_uuid(),
    delivery_key            BYTEA       NOT NULL,
    company_id              INTEGER     NOT NULL,
    event_id                BIGINT      NOT NULL,
    recipient_id            BIGINT      NOT NULL,
    recipient_uuid          UUID        NOT NULL,
    user_id                 INTEGER     NOT NULL,
    device_id               BIGINT      NOT NULL,
    device_binding_version  BIGINT      NOT NULL,
    provider                TEXT        NOT NULL,
    priority                TEXT        NOT NULL,
    payload                 JSONB       NOT NULL,
    payload_version         INTEGER     NOT NULL DEFAULT 1,
    payload_bytes           INTEGER     NOT NULL,
    collapse_key            TEXT,
    ttl_seconds             INTEGER     NOT NULL DEFAULT 86400,
    broadcast_id            BIGINT,
    broadcast_target_id     BIGINT,
    replay_of_outbox_uuid   UUID,
    replay_seq              INTEGER,
    status                  TEXT        NOT NULL DEFAULT 'pending',
    attempt_count           INTEGER     NOT NULL DEFAULT 0,
    max_attempts            INTEGER     NOT NULL DEFAULT 6,
    next_attempt_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    claimed_by              TEXT,
    claimed_at              TIMESTAMPTZ,
    claim_expires_at        TIMESTAMPTZ,
    sent_at                 TIMESTAMPTZ,
    cancelled_reason        TEXT,
    last_error_code         TEXT,
    last_error              TEXT,
    provider_message_id     TEXT,

    PRIMARY KEY (created_at, outbox_id),
    CONSTRAINT uq_npo_uuid UNIQUE (created_at, outbox_uuid),
    CONSTRAINT chk_npo_delivery_key CHECK (octet_length(delivery_key) = 32),
    CONSTRAINT chk_npo_provider CHECK (provider IN ('fcm','apns','webpush')),
    CONSTRAINT chk_npo_priority CHECK (priority IN ('critical','high','normal','low')),
    CONSTRAINT chk_npo_status CHECK (status IN
        ('pending','claimed','sent','failed','dead','cancelled')),
    CONSTRAINT chk_npo_attempts CHECK (
        attempt_count >= 0 AND max_attempts >= 1 AND attempt_count <= max_attempts + 1),
    CONSTRAINT chk_npo_claim CHECK (status <> 'claimed' OR claim_expires_at IS NOT NULL),
    CONSTRAINT chk_npo_sent CHECK (status <> 'sent' OR sent_at IS NOT NULL),
    CONSTRAINT chk_npo_cancelled CHECK (
        status <> 'cancelled' OR cancelled_reason IS NOT NULL),
    CONSTRAINT chk_npo_cancel_reason CHECK (
        cancelled_reason IS NULL OR cancelled_reason IN
        ('QUIET_HOURS','DND','PREFERENCE_MUTED','MIN_PRIORITY','DEVICE_REVOKED',
         'DEVICE_BINDING_CHANGED','BROADCAST_CANCELLED','TEMPLATE_MISSING',
         'PAYLOAD_TOO_LARGE','STALE_ON_RESUME','PROVIDER_DISABLED','CHANNEL_DISABLED')),
    CONSTRAINT chk_npo_binding CHECK (device_binding_version >= 1),
    CONSTRAINT chk_npo_payload_obj CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT chk_npo_payload_coarse CHECK (pg_column_size(payload) <= 8192),
    CONSTRAINT chk_npo_payload_bytes CHECK (payload_bytes BETWEEN 1 AND 3800),
    CONSTRAINT chk_npo_payload_version CHECK (payload_version >= 1),
    CONSTRAINT chk_npo_ttl CHECK (ttl_seconds > 0),
    CONSTRAINT chk_npo_replay CHECK (
        (replay_of_outbox_uuid IS NULL) = (replay_seq IS NULL)),
    CONSTRAINT chk_npo_replay_seq CHECK (replay_seq IS NULL OR replay_seq >= 1),
    CONSTRAINT chk_npo_broadcast CHECK (
        broadcast_target_id IS NULL OR broadcast_id IS NOT NULL)
) PARTITION BY RANGE (created_at);

CREATE INDEX IF NOT EXISTS idx_npo_claimable
    ON public.notification_push_outbox (next_attempt_at, outbox_id)
    WHERE status IN ('pending','failed');
CREATE INDEX IF NOT EXISTS idx_npo_stuck
    ON public.notification_push_outbox (claim_expires_at)
    WHERE status = 'claimed';
CREATE INDEX IF NOT EXISTS idx_npo_company
    ON public.notification_push_outbox (company_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_npo_recipient
    ON public.notification_push_outbox (recipient_id);
CREATE INDEX IF NOT EXISTS idx_npo_device
    ON public.notification_push_outbox (device_id)
    WHERE status IN ('pending','failed','claimed');
CREATE INDEX IF NOT EXISTS idx_npo_broadcast
    ON public.notification_push_outbox (broadcast_id, status)
    WHERE broadcast_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_npo_dead
    ON public.notification_push_outbox (company_id, created_at DESC)
    WHERE status = 'dead';
CREATE INDEX IF NOT EXISTS idx_npo_dedup
    ON public.notification_push_outbox (delivery_key);

DO $$
DECLARE
    month_start DATE := date_trunc('month', CURRENT_DATE)::DATE;
    part_start DATE;
    part_end DATE;
    part_name TEXT;
    month_offset INTEGER;
BEGIN
    FOR month_offset IN 0..3 LOOP
        part_start := (month_start + make_interval(months => month_offset))::DATE;
        part_end := (month_start + make_interval(months => month_offset + 1))::DATE;
        part_name := 'notification_push_outbox_' || to_char(part_start, 'YYYY_MM');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS public.%I PARTITION OF public.notification_push_outbox '
            || 'FOR VALUES FROM (%L) TO (%L)',
            part_name, part_start::TIMESTAMPTZ, part_end::TIMESTAMPTZ);
    END LOOP;
END;
$$;

CREATE TABLE IF NOT EXISTS public.notification_push_outbox_default
    PARTITION OF public.notification_push_outbox DEFAULT;

