-- Retain legacy assignment rows for audit, but make sure rows without an
-- authoritative company user cannot remain operationally active.
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
        IF to_regclass(format('%I.hr_employee_shift', tenant_schema)) IS NULL
           OR to_regclass(format('%I.hr_employee', tenant_schema)) IS NULL THEN
            CONTINUE;
        END IF;

        EXECUTE format($sql$
            UPDATE %I.hr_employee_shift es
            SET effective_to = GREATEST(es.effective_from, CURRENT_DATE - 1),
                updated_at = CURRENT_TIMESTAMP,
                updated_by = 'flyway-v156-orphan-close'
            WHERE es.effective_to IS NULL
              AND (
                  es.user_id IS NULL
                  OR NOT EXISTS (
                      SELECT 1
                      FROM %I.hr_employee e
                      WHERE e.id = es.employee_id
                        AND e.user_id = es.user_id
                        AND e.is_active = TRUE
                  )
              )
        $sql$, tenant_schema, tenant_schema);
    END LOOP;
END $$;
