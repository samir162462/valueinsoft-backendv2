-- Allow a user to clock in and out multiple times on the same attendance day.
-- The immutable action log remains the source of truth; hr_attendance_day is the
-- aggregated reporting/payroll projection for all sessions started that day.

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
        IF to_regclass(format('%I.hr_attendance_day', tenant_schema)) IS NULL THEN
            CONTINUE;
        END IF;

        EXECUTE format(
            'ALTER TABLE %I.hr_attendance_day ADD COLUMN IF NOT EXISTS session_count INT NOT NULL DEFAULT 0',
            tenant_schema
        );

        -- Existing attendance summaries receive their historical number of
        -- sessions. A session belongs to the calendar day on which CLOCK_IN was
        -- recorded, including sessions that finish after midnight.
        EXECUTE format($sql$
            UPDATE %I.hr_attendance_day attendance_day
            SET session_count = session_totals.session_count
            FROM (
                SELECT employee_id, action_time::date AS attendance_date, COUNT(*)::INT AS session_count
                FROM %I.hr_attendance_log
                WHERE action_type = 'CLOCK_IN'
                GROUP BY employee_id, action_time::date
            ) session_totals
            WHERE attendance_day.employee_id = session_totals.employee_id
              AND attendance_day.attendance_date = session_totals.attendance_date
              AND attendance_day.session_count IS DISTINCT FROM session_totals.session_count
        $sql$, tenant_schema, tenant_schema);

        EXECUTE format($sql$
            ALTER TABLE %I.hr_attendance_day
            DROP CONSTRAINT IF EXISTS ck_hr_attendance_day_session_count
        $sql$, tenant_schema);
        EXECUTE format($sql$
            ALTER TABLE %I.hr_attendance_day
            ADD CONSTRAINT ck_hr_attendance_day_session_count CHECK (session_count >= 0)
        $sql$, tenant_schema);
    END LOOP;
END $$;
