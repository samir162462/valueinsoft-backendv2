-- ============================================================
-- V41: Shift Event Audit Log Table
-- One table per tenant schema to record every shift lifecycle
-- event with actor, timestamp, and metadata.
-- ============================================================

DO $$
DECLARE
    schema_rec RECORD;
BEGIN
    FOR schema_rec IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'c_%'
    LOOP
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.shift_event (
                event_id        BIGSERIAL PRIMARY KEY,
                shift_id        INTEGER NOT NULL,
                branch_id       INTEGER NOT NULL,
                event_type      VARCHAR(60)  NOT NULL,
                event_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                actor_user_id   VARCHAR(120),
                actor_role      VARCHAR(40),
                reference_type  VARCHAR(60),
                reference_id    VARCHAR(120),
                metadata        JSONB,
                reason          VARCHAR(500),

                CONSTRAINT shift_event_shift_fk
                    FOREIGN KEY (shift_id)
                    REFERENCES %I."PosShiftPeriod" ("PosSOID")
                    ON DELETE CASCADE
            )
        ', schema_rec.schema_name, schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_shift_event_shift_time
                ON %I.shift_event (shift_id, event_time)
        ', schema_rec.schema_name);
    END LOOP;
END $$;
