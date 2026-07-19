-- Attendance identity hardening and workspace bootstrap.
-- public.users.id is the authoritative person identity. hr_employee remains the
-- tenant-local HR/payroll profile and internal FK target for backward compatibility.

INSERT INTO public.platform_capabilities (
    capability_key, module_id, resource, action, scope_type, status, description
) VALUES
    ('attendance.self.use', 'attendance', 'self', 'use', 'branch', 'active', 'Clock in and out for the authenticated user.'),
    ('hr.employee.read.company', 'attendance', 'employee', 'read_company', 'company', 'active', 'View synchronized employees across the company.'),
    ('attendance.report.company', 'attendance', 'report', 'view_company', 'company', 'active', 'View attendance reporting across the company.')
ON CONFLICT (capability_key) DO UPDATE SET
    module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT role_id, 'attendance.self.use', 'branch', 'allow', 'v1'
FROM public.role_definitions
WHERE status = 'active'
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET
    grant_mode = 'allow',
    grant_version = 'v1';

INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
VALUES
    ('Owner', 'hr.employee.read.company', 'company', 'allow', 'v1'),
    ('Owner', 'attendance.report.company', 'company', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET
    grant_mode = 'allow',
    grant_version = 'v1';

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

        IF to_regclass(format('%I.hr_employee', tenant_schema)) IS NULL THEN
            CONTINUE;
        END IF;

        EXECUTE format('ALTER TABLE %I.hr_employee_shift ADD COLUMN IF NOT EXISTS user_id INT', tenant_schema);
        EXECUTE format('ALTER TABLE %I.hr_attendance_log ADD COLUMN IF NOT EXISTS user_id INT', tenant_schema);
        EXECUTE format('ALTER TABLE %I.hr_attendance_day ADD COLUMN IF NOT EXISTS user_id INT', tenant_schema);

        -- Link legacy HR profiles to users when their stored code is either the user id or username.
        EXECUTE format($sql$
            UPDATE %I.hr_employee e
            SET user_id = u.id,
                branch_id = u."branchId",
                employee_code = u.id::TEXT,
                first_name = COALESCE(NULLIF(u."firstName", ''), u."userName"),
                last_name = u."lastName",
                is_active = TRUE,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = 'flyway-v155'
            FROM public.users u
            JOIN public."Branch" b ON b."branchId" = u."branchId" AND b."companyId" = %s
            WHERE e.user_id IS NULL
              AND e.company_id = %s
              AND (e.employee_code = u.id::TEXT OR lower(e.employee_code) = lower(u."userName"))
        $sql$, tenant_schema, tenant_id, tenant_id);

        -- Resolve any historical duplicate user links without deleting referenced HR/payroll rows.
        EXECUTE format($sql$
            WITH ranked AS (
                SELECT id,
                       row_number() OVER (
                           PARTITION BY company_id, user_id
                           ORDER BY is_active DESC, updated_at DESC NULLS LAST, id DESC
                       ) AS row_rank
                FROM %I.hr_employee
                WHERE user_id IS NOT NULL
            )
            UPDATE %I.hr_employee e
            SET user_id = NULL,
                is_active = FALSE,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = 'flyway-v155-duplicate'
            FROM ranked r
            WHERE e.id = r.id AND r.row_rank > 1
        $sql$, tenant_schema, tenant_schema);

        EXECUTE format($sql$
            UPDATE %I.hr_employee e
            SET branch_id = u."branchId",
                employee_code = u.id::TEXT,
                first_name = COALESCE(NULLIF(u."firstName", ''), u."userName"),
                last_name = u."lastName",
                is_active = TRUE,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = 'flyway-v155-sync'
            FROM public.users u
            JOIN public."Branch" b ON b."branchId" = u."branchId" AND b."companyId" = %s
            WHERE e.user_id = u.id AND e.company_id = %s
        $sql$, tenant_schema, tenant_id, tenant_id);

        -- Active HR profiles must always have a valid company user identity.
        EXECUTE format($sql$
            UPDATE %I.hr_employee e
            SET is_active = FALSE,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = 'flyway-v155-orphan'
            WHERE e.user_id IS NULL
               OR NOT EXISTS (
                    SELECT 1
                    FROM public.users u
                    JOIN public."Branch" b ON b."branchId" = u."branchId"
                    WHERE u.id = e.user_id AND b."companyId" = %s
               )
        $sql$, tenant_schema, tenant_id);

        EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS ux_hr_employee_company_user ON %I.hr_employee (company_id, user_id) WHERE user_id IS NOT NULL', tenant_schema);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_hr_employee_branch_active ON %I.hr_employee (branch_id, is_active, user_id)', tenant_schema);

        constraint_name := 'fk_hr_employee_public_user';
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = constraint_name AND conrelid = to_regclass(format('%I.hr_employee', tenant_schema))) THEN
            EXECUTE format('ALTER TABLE %I.hr_employee ADD CONSTRAINT %I FOREIGN KEY (user_id) REFERENCES public.users(id) NOT VALID', tenant_schema, constraint_name);
        END IF;

        constraint_name := 'ck_hr_employee_active_user';
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = constraint_name AND conrelid = to_regclass(format('%I.hr_employee', tenant_schema))) THEN
            EXECUTE format('ALTER TABLE %I.hr_employee ADD CONSTRAINT %I CHECK (NOT is_active OR user_id IS NOT NULL) NOT VALID', tenant_schema, constraint_name);
        END IF;

        EXECUTE format('UPDATE %I.hr_employee_shift es SET user_id = e.user_id FROM %I.hr_employee e WHERE es.employee_id = e.id AND es.user_id IS NULL', tenant_schema, tenant_schema);
        EXECUTE format('UPDATE %I.hr_attendance_log al SET user_id = e.user_id FROM %I.hr_employee e WHERE al.employee_id = e.id AND al.user_id IS NULL', tenant_schema, tenant_schema);
        EXECUTE format('UPDATE %I.hr_attendance_day ad SET user_id = e.user_id FROM %I.hr_employee e WHERE ad.employee_id = e.id AND ad.user_id IS NULL', tenant_schema, tenant_schema);

        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_hr_employee_shift_user_date ON %I.hr_employee_shift (user_id, effective_from DESC, effective_to)', tenant_schema);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_hr_attendance_log_user_time ON %I.hr_attendance_log (user_id, action_time DESC)', tenant_schema);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_hr_attendance_day_user_date ON %I.hr_attendance_day (user_id, attendance_date DESC)', tenant_schema);

        -- Preserve only one open-ended current assignment per user and branch.
        EXECUTE format($sql$
            WITH ranked AS (
                SELECT id,
                       row_number() OVER (
                           PARTITION BY company_id, branch_id, user_id
                           ORDER BY effective_from DESC, created_at DESC NULLS LAST, id DESC
                       ) AS row_rank
                FROM %I.hr_employee_shift
                WHERE user_id IS NOT NULL AND effective_to IS NULL
            )
            UPDATE %I.hr_employee_shift es
            SET effective_to = GREATEST(es.effective_from, CURRENT_DATE - 1),
                updated_at = CURRENT_TIMESTAMP,
                updated_by = 'flyway-v155-overlap'
            FROM ranked r
            WHERE es.id = r.id AND r.row_rank > 1
        $sql$, tenant_schema, tenant_schema);
        EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS ux_hr_employee_shift_open_user ON %I.hr_employee_shift (company_id, branch_id, user_id) WHERE user_id IS NOT NULL AND effective_to IS NULL', tenant_schema);

        -- Every configured shift is active. Missing branch schedules get the requested noon-to-midnight standard.
        EXECUTE format('UPDATE %I.hr_shift SET is_active = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = ''flyway-v155'' WHERE is_active IS DISTINCT FROM TRUE', tenant_schema);
        EXECUTE format($sql$
            INSERT INTO %I.hr_shift (
                company_id, branch_id, shift_name, start_time, end_time,
                grace_minutes, is_active, created_by, updated_by
            )
            SELECT %s, b."branchId", 'Standard Shift', TIME '12:00:00', TIME '00:00:00',
                   15, TRUE, 'system', 'system'
            FROM public."Branch" b
            WHERE b."companyId" = %s
              AND NOT EXISTS (
                  SELECT 1 FROM %I.hr_shift s WHERE s.branch_id = b."branchId"
              )
        $sql$, tenant_schema, tenant_id, tenant_id, tenant_schema);

        -- When exactly one shift exists in a branch, assign every active user-backed profile automatically.
        EXECUTE format($sql$
            INSERT INTO %I.hr_employee_shift (
                company_id, branch_id, employee_id, user_id, shift_id,
                effective_from, effective_to, created_by, updated_by
            )
            SELECT e.company_id, e.branch_id, e.id, e.user_id, only_shift.shift_id,
                   CURRENT_DATE, NULL, 'system-auto-assignment', 'system-auto-assignment'
            FROM %I.hr_employee e
            JOIN (
                SELECT branch_id, MIN(id) AS shift_id
                FROM %I.hr_shift
                WHERE is_active = TRUE
                GROUP BY branch_id
                HAVING COUNT(*) = 1
            ) only_shift ON only_shift.branch_id = e.branch_id
            WHERE e.is_active = TRUE
              AND e.user_id IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM %I.hr_employee_shift current_assignment
                  WHERE current_assignment.company_id = e.company_id
                    AND current_assignment.branch_id = e.branch_id
                    AND current_assignment.user_id = e.user_id
                    AND current_assignment.effective_to IS NULL
              )
        $sql$, tenant_schema, tenant_schema, tenant_schema, tenant_schema);
    END LOOP;
END $$;
