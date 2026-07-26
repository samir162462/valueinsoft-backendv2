-- Hot-path, replay, lineage, and retention indexes for all notification tenant tables.

CREATE OR REPLACE FUNCTION public.notification_bootstrap_tenant_indexes(p_schema_name TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_schema_name IS NULL OR p_schema_name !~ '^c_[0-9]+$' THEN
        RAISE EXCEPTION 'Invalid tenant schema name: %', p_schema_name
            USING ERRCODE = '22023';
    END IF;
    IF to_regclass(format('%I.notification_event', p_schema_name)) IS NULL THEN
        RAISE EXCEPTION 'Notification tenant tables do not exist in schema %', p_schema_name
            USING ERRCODE = '42P01';
    END IF;

    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_ne_type_created ON %I.notification_event (type_key, created_at DESC)', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_ne_subject ON %I.notification_event (subject_type, subject_id) WHERE subject_type IS NOT NULL', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_ne_retention ON %I.notification_event (created_at)', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_ne_broadcast ON %I.notification_event (broadcast_id) WHERE broadcast_id IS NOT NULL', p_schema_name);

    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nfj_claimable ON %I.notification_fanout_job (next_attempt_at, job_id) WHERE status IN (''pending'',''failed'')', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nfj_stuck ON %I.notification_fanout_job (claim_expires_at) WHERE status = ''claimed''', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nfj_broadcast ON %I.notification_fanout_job (broadcast_id) WHERE broadcast_id IS NOT NULL', p_schema_name);

    EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS uq_nr_open_group ON %I.notification_recipient (user_id, group_key) WHERE group_key IS NOT NULL AND archived_at IS NULL AND group_closed_at IS NULL', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nr_feed_active ON %I.notification_recipient (user_id, last_event_at DESC, recipient_id DESC) WHERE archived_at IS NULL', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nr_feed_archived ON %I.notification_recipient (user_id, archived_at DESC, recipient_id DESC) WHERE archived_at IS NOT NULL', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nr_feed_active_branch ON %I.notification_recipient (user_id, branch_id, last_event_at DESC, recipient_id DESC) WHERE archived_at IS NULL', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nr_unseen ON %I.notification_recipient (user_id) WHERE state = ''unseen''', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nr_unread ON %I.notification_recipient (user_id) WHERE state IN (''unseen'',''seen'')', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nr_category ON %I.notification_recipient (user_id, category, last_event_at DESC) WHERE archived_at IS NULL', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nr_purge ON %I.notification_recipient (purge_after)', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nr_change ON %I.notification_recipient (user_id, change_sequence DESC)', p_schema_name);

    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nre_recipient ON %I.notification_recipient_event (recipient_id, sequence_no)', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nre_event ON %I.notification_recipient_event (event_id)', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nfc_replay ON %I.notification_feed_change (user_id, change_sequence)', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nfc_retention ON %I.notification_feed_change (occurred_at)', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nra_recipient ON %I.notification_recipient_audit (recipient_id, occurred_at)', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_nra_retention ON %I.notification_recipient_audit (category, occurred_at)', p_schema_name);
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

