-- Approved annual leave is a first-class HR source used by attendance reporting.
-- The authenticated public user id remains the authoritative employee identity.
DO $$
DECLARE
    tenant_schema TEXT;
    tenant_id INTEGER;
BEGIN
    FOR tenant_schema IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name ~ '^c_[0-9]+$'
        ORDER BY schema_name
    LOOP
        tenant_id := substring(tenant_schema FROM 3)::INTEGER;

        EXECUTE format($sql$
            CREATE TABLE IF NOT EXISTS %I.hr_leave_request (
                id BIGSERIAL PRIMARY KEY,
                company_id INT NOT NULL,
                branch_id INT NOT NULL,
                user_id INT NOT NULL,
                leave_type VARCHAR(30) NOT NULL DEFAULT 'ANNUAL',
                start_date DATE NOT NULL,
                end_date DATE NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                notes TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_by VARCHAR(100),
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_by VARCHAR(100),
                CONSTRAINT ck_hr_leave_dates CHECK (end_date >= start_date),
                CONSTRAINT ck_hr_leave_type CHECK (leave_type IN ('ANNUAL')),
                CONSTRAINT ck_hr_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
                CONSTRAINT fk_hr_leave_public_user FOREIGN KEY (user_id) REFERENCES public.users(id)
            )
        $sql$, tenant_schema);

        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_hr_leave_calendar ON %I.hr_leave_request (company_id, branch_id, status, start_date, end_date, user_id)',
            tenant_schema
        );
        EXECUTE format(
            'CREATE UNIQUE INDEX IF NOT EXISTS ux_hr_leave_approved_period ON %I.hr_leave_request (company_id, branch_id, user_id, leave_type, start_date, end_date) WHERE status = ''APPROVED''',
            tenant_schema
        );
    END LOOP;
END $$;
