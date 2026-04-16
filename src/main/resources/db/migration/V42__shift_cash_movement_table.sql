-- ============================================================
-- V42: Shift Cash Movement Ledger Table
-- One table per tenant schema to record every cash drawer
-- event: opening float, cash sales, refunds, paid-in/out,
-- safe drops, and close counts.
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
            CREATE TABLE IF NOT EXISTS %I.shift_cash_movement (
                movement_id     BIGSERIAL    PRIMARY KEY,
                shift_id        INTEGER      NOT NULL,
                branch_id       INTEGER      NOT NULL,
                movement_type   VARCHAR(30)  NOT NULL,
                amount          NUMERIC(14,2) NOT NULL,
                actor_user_id   VARCHAR(120),
                reference_type  VARCHAR(60),
                reference_id    VARCHAR(120),
                note            VARCHAR(500),
                created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

                CONSTRAINT shift_cash_movement_shift_fk
                    FOREIGN KEY (shift_id)
                    REFERENCES %I."PosShiftPeriod" ("PosSOID")
                    ON DELETE CASCADE,

                CONSTRAINT shift_cash_movement_type_ck
                    CHECK (movement_type IN (
                        ''OPENING_FLOAT'',
                        ''CASH_SALE'',
                        ''CASH_REFUND'',
                        ''PAID_IN'',
                        ''PAID_OUT'',
                        ''SAFE_DROP'',
                        ''CASH_ADJUSTMENT'',
                        ''CLOSE_COUNT''
                    ))
            )
        ', schema_rec.schema_name, schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_shift_cash_movement_shift
                ON %I.shift_cash_movement (shift_id, created_at)
        ', schema_rec.schema_name);
    END LOOP;
END $$;
