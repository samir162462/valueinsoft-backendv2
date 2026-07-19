-- Payroll identity, deterministic attendance snapshots, and finance-safe totals.
-- public.users.id is the authoritative person identity; hr_employee.id remains
-- the tenant-local operational FK for backward compatibility.

DO $$
DECLARE
    tenant_schema TEXT;
    tenant_id INTEGER;
    constraint_name TEXT;
BEGIN
    FOR tenant_schema IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name ~ '^c_[0-9]+$'
        ORDER BY schema_name
    LOOP
        tenant_id := substring(tenant_schema FROM 3)::INTEGER;

        IF to_regclass(format('%I.payroll_salary_profile', tenant_schema)) IS NULL
           OR to_regclass(format('%I.hr_employee', tenant_schema)) IS NULL THEN
            CONTINUE;
        END IF;

        EXECUTE format('ALTER TABLE %I.payroll_settings ADD COLUMN IF NOT EXISTS timezone_id VARCHAR(80) NOT NULL DEFAULT ''Africa/Cairo''', tenant_schema);
        EXECUTE format('ALTER TABLE %I.payroll_settings ADD COLUMN IF NOT EXISTS week_start_day VARCHAR(12) NOT NULL DEFAULT ''SUNDAY''', tenant_schema);
        EXECUTE format('ALTER TABLE %I.payroll_settings ADD COLUMN IF NOT EXISTS work_week_days VARCHAR(100) NOT NULL DEFAULT ''SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY''', tenant_schema);
        EXECUTE format('ALTER TABLE %I.payroll_settings ADD COLUMN IF NOT EXISTS monthly_cutoff_day SMALLINT NOT NULL DEFAULT 25', tenant_schema);
        EXECUTE format('ALTER TABLE %I.payroll_settings ADD COLUMN IF NOT EXISTS deduction_payable_account_id UUID', tenant_schema);

        constraint_name := 'ck_payroll_settings_monthly_cutoff';
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = constraint_name
              AND conrelid = to_regclass(format('%I.payroll_settings', tenant_schema))
        ) THEN
            EXECUTE format('ALTER TABLE %I.payroll_settings ADD CONSTRAINT %I CHECK (monthly_cutoff_day BETWEEN 1 AND 28)', tenant_schema, constraint_name);
        END IF;

        EXECUTE format('ALTER TABLE %I.payroll_salary_profile ADD COLUMN IF NOT EXISTS user_id INT', tenant_schema);
        EXECUTE format('ALTER TABLE %I.payroll_adjustment ADD COLUMN IF NOT EXISTS user_id INT', tenant_schema);
        EXECUTE format('ALTER TABLE %I.payroll_run_line ADD COLUMN IF NOT EXISTS user_id INT', tenant_schema);
        EXECUTE format('ALTER TABLE %I.payroll_run_line ADD COLUMN IF NOT EXISTS wage_reduction_total DECIMAL(19,4) NOT NULL DEFAULT 0', tenant_schema);
        EXECUTE format('ALTER TABLE %I.payroll_run_line ADD COLUMN IF NOT EXISTS withholding_total DECIMAL(19,4) NOT NULL DEFAULT 0', tenant_schema);
        EXECUTE format('ALTER TABLE %I.payroll_payment_line ADD COLUMN IF NOT EXISTS user_id INT', tenant_schema);

        EXECUTE format('UPDATE %I.payroll_salary_profile p SET user_id = e.user_id FROM %I.hr_employee e WHERE p.employee_id = e.id AND p.user_id IS NULL', tenant_schema, tenant_schema);
        EXECUTE format('UPDATE %I.payroll_adjustment a SET user_id = e.user_id FROM %I.hr_employee e WHERE a.employee_id = e.id AND a.user_id IS NULL', tenant_schema, tenant_schema);
        EXECUTE format('UPDATE %I.payroll_run_line l SET user_id = e.user_id FROM %I.hr_employee e WHERE l.employee_id = e.id AND l.user_id IS NULL', tenant_schema, tenant_schema);
        -- Legacy deductions did not distinguish wage reductions from third-party
        -- withholdings. Treat them as wage reductions so historical unposted runs
        -- remain balanced without inventing a liability account.
        EXECUTE format('UPDATE %I.payroll_run_line SET wage_reduction_total = total_deductions WHERE total_deductions > 0 AND wage_reduction_total = 0 AND withholding_total = 0', tenant_schema);
        EXECUTE format('UPDATE %I.payroll_payment_line l SET user_id = e.user_id FROM %I.hr_employee e WHERE l.employee_id = e.id AND l.user_id IS NULL', tenant_schema, tenant_schema);

        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_payroll_profile_user_effective ON %I.payroll_salary_profile (company_id, user_id, effective_from DESC, effective_to) WHERE user_id IS NOT NULL', tenant_schema);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_payroll_adjustment_user_status ON %I.payroll_adjustment (company_id, user_id, status, effective_date) WHERE user_id IS NOT NULL', tenant_schema);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_payroll_run_line_user ON %I.payroll_run_line (company_id, user_id, payroll_run_id) WHERE user_id IS NOT NULL', tenant_schema);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_payroll_payment_line_user ON %I.payroll_payment_line (company_id, user_id, payroll_payment_id) WHERE user_id IS NOT NULL', tenant_schema);
        EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS ux_payroll_profile_user_effective ON %I.payroll_salary_profile (company_id, user_id, effective_from) WHERE user_id IS NOT NULL', tenant_schema);
        EXECUTE format($sql$
            CREATE UNIQUE INDEX IF NOT EXISTS ux_payroll_run_scope_period
            ON %I.payroll_run (company_id, COALESCE(branch_id, 0), frequency, currency_code, period_start, period_end)
            WHERE status NOT IN ('CANCELLED', 'REVERSED')
        $sql$, tenant_schema);

        FOREACH constraint_name IN ARRAY ARRAY[
            'fk_payroll_profile_public_user',
            'fk_payroll_adjustment_public_user',
            'fk_payroll_run_line_public_user',
            'fk_payroll_payment_line_public_user'
        ]
        LOOP
            IF constraint_name = 'fk_payroll_profile_public_user'
               AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = constraint_name AND conrelid = to_regclass(format('%I.payroll_salary_profile', tenant_schema))) THEN
                EXECUTE format('ALTER TABLE %I.payroll_salary_profile ADD CONSTRAINT %I FOREIGN KEY (user_id) REFERENCES public.users(id) NOT VALID', tenant_schema, constraint_name);
            ELSIF constraint_name = 'fk_payroll_adjustment_public_user'
               AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = constraint_name AND conrelid = to_regclass(format('%I.payroll_adjustment', tenant_schema))) THEN
                EXECUTE format('ALTER TABLE %I.payroll_adjustment ADD CONSTRAINT %I FOREIGN KEY (user_id) REFERENCES public.users(id) NOT VALID', tenant_schema, constraint_name);
            ELSIF constraint_name = 'fk_payroll_run_line_public_user'
               AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = constraint_name AND conrelid = to_regclass(format('%I.payroll_run_line', tenant_schema))) THEN
                EXECUTE format('ALTER TABLE %I.payroll_run_line ADD CONSTRAINT %I FOREIGN KEY (user_id) REFERENCES public.users(id) NOT VALID', tenant_schema, constraint_name);
            ELSIF constraint_name = 'fk_payroll_payment_line_public_user'
               AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = constraint_name AND conrelid = to_regclass(format('%I.payroll_payment_line', tenant_schema))) THEN
                EXECUTE format('ALTER TABLE %I.payroll_payment_line ADD CONSTRAINT %I FOREIGN KEY (user_id) REFERENCES public.users(id) NOT VALID', tenant_schema, constraint_name);
            END IF;
        END LOOP;

        constraint_name := 'ck_payroll_active_profile_user';
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = constraint_name AND conrelid = to_regclass(format('%I.payroll_salary_profile', tenant_schema))) THEN
            EXECUTE format('ALTER TABLE %I.payroll_salary_profile ADD CONSTRAINT %I CHECK (NOT is_active OR user_id IS NOT NULL) NOT VALID', tenant_schema, constraint_name);
        END IF;

        EXECUTE format($sql$
            CREATE TABLE IF NOT EXISTS %I.payroll_run_attendance_day (
                id BIGSERIAL PRIMARY KEY,
                company_id INT NOT NULL,
                payroll_run_id BIGINT NOT NULL REFERENCES %I.payroll_run(id) ON DELETE CASCADE,
                payroll_run_line_id BIGINT NOT NULL REFERENCES %I.payroll_run_line(id) ON DELETE CASCADE,
                employee_id INT NOT NULL REFERENCES %I.hr_employee(id) ON DELETE RESTRICT,
                user_id INT NOT NULL REFERENCES public.users(id),
                branch_id INT NOT NULL,
                attendance_date DATE NOT NULL,
                shift_id INT,
                scheduled_minutes INT NOT NULL DEFAULT 0,
                worked_minutes INT NOT NULL DEFAULT 0,
                break_minutes INT NOT NULL DEFAULT 0,
                late_minutes INT NOT NULL DEFAULT 0,
                overtime_minutes INT NOT NULL DEFAULT 0,
                payable_minutes INT NOT NULL DEFAULT 0,
                day_status VARCHAR(30) NOT NULL,
                is_paid_leave BOOLEAN NOT NULL DEFAULT FALSE,
                source_attendance_day_id BIGINT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (company_id, payroll_run_id, user_id, attendance_date),
                CHECK (scheduled_minutes >= 0 AND worked_minutes >= 0 AND break_minutes >= 0),
                CHECK (late_minutes >= 0 AND overtime_minutes >= 0 AND payable_minutes >= 0)
            )
        $sql$, tenant_schema, tenant_schema, tenant_schema, tenant_schema);

        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_payroll_attendance_run_line ON %I.payroll_run_attendance_day (company_id, payroll_run_line_id, attendance_date)', tenant_schema);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_payroll_attendance_user_date ON %I.payroll_run_attendance_day (company_id, user_id, attendance_date)', tenant_schema);
    END LOOP;
END $$;
