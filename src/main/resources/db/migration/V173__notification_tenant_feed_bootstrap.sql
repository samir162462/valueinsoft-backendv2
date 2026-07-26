-- Tenant Notification Center foundation.
-- public.notification_bootstrap_tenant(text) is the only application entry point for
-- provisioning these objects. V174 replaces the index helper used at the end.

CREATE OR REPLACE FUNCTION public.notification_bootstrap_tenant_indexes(p_schema_name TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    -- V174 installs the index implementation. Keeping this hook here makes V173 independently
    -- deployable while ensuring new tenants always call one bootstrap entry point.
    RETURN;
END;
$$;

CREATE OR REPLACE FUNCTION public.notification_bootstrap_tenant(p_schema_name TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_schema_name IS NULL OR p_schema_name !~ '^c_[0-9]+$' THEN
        RAISE EXCEPTION 'Invalid tenant schema name: %', p_schema_name
            USING ERRCODE = '22023';
    END IF;
    IF to_regnamespace(p_schema_name) IS NULL THEN
        RAISE EXCEPTION 'Tenant schema does not exist: %', p_schema_name
            USING ERRCODE = '3F000';
    END IF;

    EXECUTE format(
        'CREATE SEQUENCE IF NOT EXISTS %I.notification_feed_change_seq '
        || 'AS BIGINT START WITH 1 INCREMENT BY 1',
        p_schema_name);

    EXECUTE format($ddl$
        CREATE TABLE IF NOT EXISTS %I.notification_event (
            event_id            BIGSERIAL   PRIMARY KEY,
            type_key            TEXT        NOT NULL,
            idempotency_key     TEXT        NOT NULL,
            request_fingerprint BYTEA       NOT NULL,
            branch_id           INTEGER,
            actor_user_id       INTEGER,
            subject_type        TEXT,
            subject_id          BIGINT,
            params              JSONB       NOT NULL DEFAULT '{}'::jsonb,
            priority            TEXT        NOT NULL,
            group_key           TEXT,
            source              TEXT        NOT NULL DEFAULT 'system',
            broadcast_id        BIGINT,
            correlation_id      TEXT,
            retention_days      INTEGER     NOT NULL,
            created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            expires_at          TIMESTAMPTZ,
            CONSTRAINT uq_ne_idempotency UNIQUE (idempotency_key),
            CONSTRAINT chk_ne_priority CHECK (priority IN ('critical','high','normal','low')),
            CONSTRAINT chk_ne_source CHECK (source IN ('system','broadcast')),
            CONSTRAINT chk_ne_params CHECK (jsonb_typeof(params) = 'object'),
            CONSTRAINT chk_ne_subject CHECK ((subject_type IS NULL) = (subject_id IS NULL)),
            CONSTRAINT chk_ne_retention CHECK (retention_days BETWEEN 7 AND 3650),
            CONSTRAINT chk_ne_broadcast_source CHECK (
                source <> 'broadcast' OR broadcast_id IS NOT NULL)
        )
    $ddl$, p_schema_name);

    EXECUTE format($ddl$
        CREATE TABLE IF NOT EXISTS %I.notification_fanout_job (
            job_id             BIGSERIAL   PRIMARY KEY,
            event_id           BIGINT      NOT NULL,
            mode               TEXT,
            status             TEXT        NOT NULL DEFAULT 'pending',
            bounded_audience   INTEGER,
            fanout_cursor      INTEGER,
            recipients_created INTEGER     NOT NULL DEFAULT 0,
            outbox_created     INTEGER     NOT NULL DEFAULT 0,
            batches_processed  INTEGER     NOT NULL DEFAULT 0,
            attempt_count      INTEGER     NOT NULL DEFAULT 0,
            max_attempts       INTEGER     NOT NULL DEFAULT 5,
            broadcast_id       BIGINT,
            broadcast_batch_id BIGINT,
            claimed_by         TEXT,
            claimed_at         TIMESTAMPTZ,
            claim_expires_at   TIMESTAMPTZ,
            next_attempt_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            last_error         TEXT,
            created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            completed_at       TIMESTAMPTZ,
            CONSTRAINT fk_nfj_event FOREIGN KEY (event_id)
                REFERENCES %I.notification_event (event_id) ON DELETE RESTRICT,
            CONSTRAINT uq_nfj_event UNIQUE (event_id),
            CONSTRAINT chk_nfj_mode CHECK (
                mode IS NULL OR mode IN ('SINGLE_BATCH_FANOUT','CURSOR_BATCH_FANOUT')),
            CONSTRAINT chk_nfj_status CHECK (
                status IN ('pending','claimed','completed','failed','dead')),
            CONSTRAINT chk_nfj_claim CHECK (
                status <> 'claimed' OR claim_expires_at IS NOT NULL),
            CONSTRAINT chk_nfj_mode_set CHECK (
                status <> 'completed' OR mode IS NOT NULL),
            CONSTRAINT chk_nfj_counts CHECK (
                recipients_created >= 0 AND outbox_created >= 0
                AND batches_processed >= 0 AND attempt_count >= 0 AND max_attempts >= 1),
            CONSTRAINT chk_nfj_audience CHECK (
                bounded_audience IS NULL OR bounded_audience >= 0)
        )
    $ddl$, p_schema_name, p_schema_name);

    EXECUTE format($ddl$
        CREATE TABLE IF NOT EXISTS %I.notification_recipient (
            recipient_id       BIGSERIAL   PRIMARY KEY,
            recipient_uuid     UUID        NOT NULL DEFAULT gen_random_uuid(),
            user_id            INTEGER     NOT NULL,
            branch_id          INTEGER,
            type_key           TEXT        NOT NULL,
            category           TEXT        NOT NULL,
            group_key          TEXT,
            group_closed_at    TIMESTAMPTZ,
            first_event_id     BIGINT      NOT NULL,
            latest_event_id    BIGINT      NOT NULL,
            aggregate_count    INTEGER     NOT NULL DEFAULT 1,
            rendered_title     TEXT        NOT NULL,
            rendered_body      TEXT        NOT NULL,
            rendered_preview   TEXT        NOT NULL,
            rendered_locale    TEXT        NOT NULL,
            template_version   INTEGER,
            render_status      TEXT        NOT NULL DEFAULT 'ok',
            deep_link_snapshot TEXT        NOT NULL,
            subject_type       TEXT,
            subject_id         BIGINT,
            params             JSONB       NOT NULL DEFAULT '{}'::jsonb,
            priority           TEXT        NOT NULL,
            state              TEXT        NOT NULL DEFAULT 'unseen',
            seen_at            TIMESTAMPTZ,
            read_at            TIMESTAMPTZ,
            clicked_at         TIMESTAMPTZ,
            archived_at        TIMESTAMPTZ,
            change_sequence    BIGINT      NOT NULL,
            created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            last_event_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            purge_after        TIMESTAMPTZ NOT NULL,
            CONSTRAINT fk_nr_first FOREIGN KEY (first_event_id)
                REFERENCES %I.notification_event (event_id) ON DELETE RESTRICT,
            CONSTRAINT fk_nr_latest FOREIGN KEY (latest_event_id)
                REFERENCES %I.notification_event (event_id) ON DELETE RESTRICT,
            CONSTRAINT uq_nr_uuid UNIQUE (recipient_uuid),
            CONSTRAINT chk_nr_state CHECK (state IN ('unseen','seen','read','archived')),
            CONSTRAINT chk_nr_render_status CHECK (
                render_status IN ('ok','template_missing','param_error','fallback_locale')),
            CONSTRAINT chk_nr_aggregate CHECK (aggregate_count >= 1),
            CONSTRAINT chk_nr_priority CHECK (priority IN ('critical','high','normal','low')),
            CONSTRAINT chk_nr_category CHECK (category IN
                ('operational','financial','hr','security','system','marketing')),
            CONSTRAINT chk_nr_archived CHECK (
                (state = 'archived') = (archived_at IS NOT NULL)),
            CONSTRAINT chk_nr_read CHECK (state <> 'read' OR read_at IS NOT NULL),
            CONSTRAINT chk_nr_version CHECK (
                render_status <> 'ok' OR template_version IS NOT NULL),
            CONSTRAINT chk_nr_params CHECK (jsonb_typeof(params) = 'object'),
            CONSTRAINT chk_nr_subject CHECK (
                (subject_type IS NULL) = (subject_id IS NULL)),
            CONSTRAINT chk_nr_archived_closes_group CHECK (
                archived_at IS NULL OR group_key IS NULL OR group_closed_at IS NOT NULL)
        )
    $ddl$, p_schema_name, p_schema_name, p_schema_name);

    EXECUTE format($ddl$
        CREATE TABLE IF NOT EXISTS %I.notification_recipient_event (
            event_id       BIGINT      NOT NULL,
            user_id        INTEGER     NOT NULL,
            recipient_id   BIGINT      NOT NULL,
            sequence_no    INTEGER     NOT NULL,
            contributed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            CONSTRAINT pk_nre PRIMARY KEY (event_id, user_id),
            CONSTRAINT uq_nre_recipient_event UNIQUE (recipient_id, event_id),
            CONSTRAINT uq_nre_recipient_sequence UNIQUE (recipient_id, sequence_no),
            CONSTRAINT fk_nre_recipient FOREIGN KEY (recipient_id)
                REFERENCES %I.notification_recipient (recipient_id) ON DELETE RESTRICT,
            CONSTRAINT fk_nre_event FOREIGN KEY (event_id)
                REFERENCES %I.notification_event (event_id) ON DELETE RESTRICT,
            CONSTRAINT chk_nre_sequence CHECK (sequence_no >= 1)
        )
    $ddl$, p_schema_name, p_schema_name, p_schema_name);

    EXECUTE format($ddl$
        CREATE TABLE IF NOT EXISTS %I.notification_feed_change (
            change_sequence BIGINT      PRIMARY KEY,
            user_id         INTEGER     NOT NULL,
            recipient_id    BIGINT      NOT NULL,
            change_type     TEXT        NOT NULL,
            event_id        BIGINT,
            occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            CONSTRAINT chk_nfc_type CHECK (change_type IN
                ('created','aggregated','group_closed','seen','read','clicked',
                 'archived','invalidated')),
            CONSTRAINT fk_nfc_recipient FOREIGN KEY (recipient_id)
                REFERENCES %I.notification_recipient (recipient_id) ON DELETE CASCADE
        )
    $ddl$, p_schema_name, p_schema_name);

    EXECUTE format($ddl$
        CREATE TABLE IF NOT EXISTS %I.notification_recipient_audit (
            audit_id     BIGSERIAL   PRIMARY KEY,
            recipient_id BIGINT      NOT NULL,
            user_id      INTEGER     NOT NULL,
            category     TEXT        NOT NULL,
            from_state   TEXT,
            to_state     TEXT        NOT NULL,
            channel      TEXT,
            occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            CONSTRAINT chk_nra_category CHECK (category IN
                ('operational','financial','hr','security','system','marketing')),
            CONSTRAINT chk_nra_from_state CHECK (
                from_state IS NULL OR from_state IN ('unseen','seen','read','clicked','archived')),
            CONSTRAINT chk_nra_to_state CHECK (
                to_state IN ('unseen','seen','read','clicked','archived','purged')),
            CONSTRAINT chk_nra_channel CHECK (
                channel IS NULL OR channel IN ('mobile','web','system','admin'))
        )
    $ddl$, p_schema_name);

    PERFORM public.notification_bootstrap_tenant_indexes(p_schema_name);
END;
$$;

DO $$
DECLARE
    tenant_schema TEXT;
BEGIN
    FOR tenant_schema IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name ~ '^c_[0-9]+$'
        ORDER BY schema_name
    LOOP
        PERFORM public.notification_bootstrap_tenant(tenant_schema);
    END LOOP;
END;
$$;

