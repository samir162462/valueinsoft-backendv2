-- ============================================================
-- V71: Payroll Module Foundation
-- Creates all payroll tables in every tenant schema (c_*).
-- Tables: payroll_settings, payroll_allowance_type,
--         payroll_deduction_type, payroll_salary_profile,
--         payroll_salary_component, payroll_adjustment,
--         payroll_run, payroll_run_line,
--         payroll_run_line_component, payroll_payment,
--         payroll_payment_line, payroll_audit_log
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

        -- --------------------------------------------------------
        -- 1. payroll_settings (one row per company)
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_settings (
                id                           SERIAL PRIMARY KEY,
                company_id                   INT NOT NULL UNIQUE,
                default_currency             VARCHAR(3)     NOT NULL DEFAULT ''EGP'',
                default_frequency            VARCHAR(30)    NOT NULL DEFAULT ''MONTHLY''
                    CHECK (default_frequency IN (''WEEKLY'',''BIWEEKLY'',''MONTHLY'',''CUSTOM'')),
                auto_include_attendance      BOOLEAN        NOT NULL DEFAULT TRUE,
                overtime_rate_multiplier     DECIMAL(5,2)   NOT NULL DEFAULT 1.50,
                late_deduction_per_minute    DECIMAL(19,4)  NOT NULL DEFAULT 0,
                salary_expense_account_id    UUID,
                salary_payable_account_id    UUID,
                cash_bank_account_id         UUID,
                version                      INT            NOT NULL DEFAULT 0,
                created_at                   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at                   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_by                   VARCHAR(100),
                updated_by                   VARCHAR(100)
            )
        ', schema_rec.schema_name);

        -- --------------------------------------------------------
        -- 2. payroll_allowance_type
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_allowance_type (
                id           SERIAL PRIMARY KEY,
                company_id   INT           NOT NULL,
                code         VARCHAR(50)   NOT NULL,
                name         VARCHAR(150)  NOT NULL,
                is_taxable   BOOLEAN       NOT NULL DEFAULT FALSE,
                is_active    BOOLEAN       NOT NULL DEFAULT TRUE,
                created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_by   VARCHAR(100),
                updated_by   VARCHAR(100),
                UNIQUE (company_id, code)
            )
        ', schema_rec.schema_name);

        -- --------------------------------------------------------
        -- 3. payroll_deduction_type
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_deduction_type (
                id            SERIAL PRIMARY KEY,
                company_id    INT           NOT NULL,
                code          VARCHAR(50)   NOT NULL,
                name          VARCHAR(150)  NOT NULL,
                is_statutory  BOOLEAN       NOT NULL DEFAULT FALSE,
                is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
                created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_by    VARCHAR(100),
                updated_by    VARCHAR(100),
                UNIQUE (company_id, code)
            )
        ', schema_rec.schema_name);

        -- --------------------------------------------------------
        -- 4. payroll_salary_profile
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_salary_profile (
                id                          BIGSERIAL PRIMARY KEY,
                company_id                  INT           NOT NULL,
                employee_id                 INT           NOT NULL
                    REFERENCES %I.hr_employee(id) ON DELETE RESTRICT,
                branch_id                   INT           NOT NULL,
                job_title                   VARCHAR(150),
                salary_type                 VARCHAR(30)   NOT NULL DEFAULT ''MONTHLY''
                    CHECK (salary_type IN (
                        ''MONTHLY'',''WEEKLY'',''DAILY'',''HOURLY'',''FLEXIBLE'',''COMMISSION'')),
                base_salary                 DECIMAL(19,4) NOT NULL DEFAULT 0,
                currency_code               VARCHAR(3)    NOT NULL DEFAULT ''EGP'',
                payroll_frequency           VARCHAR(30)   NOT NULL DEFAULT ''MONTHLY''
                    CHECK (payroll_frequency IN (''WEEKLY'',''BIWEEKLY'',''MONTHLY'',''CUSTOM'')),
                salary_expense_account_id   UUID,
                salary_payable_account_id   UUID,
                is_active                   BOOLEAN       NOT NULL DEFAULT TRUE,
                effective_from              DATE          NOT NULL,
                effective_to                DATE,
                version                     INT           NOT NULL DEFAULT 0,
                created_at                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_by                  VARCHAR(100),
                updated_by                  VARCHAR(100),
                UNIQUE (company_id, employee_id, effective_from)
            )
        ', schema_rec.schema_name, schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_psp_company_employee_active
            ON %I.payroll_salary_profile (company_id, employee_id, is_active)
        ', schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_psp_company_branch_active
            ON %I.payroll_salary_profile (company_id, branch_id, is_active)
        ', schema_rec.schema_name);

        -- --------------------------------------------------------
        -- 5. payroll_salary_component
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_salary_component (
                id                  BIGSERIAL PRIMARY KEY,
                company_id          INT           NOT NULL,
                salary_profile_id   BIGINT        NOT NULL
                    REFERENCES %I.payroll_salary_profile(id) ON DELETE CASCADE,
                component_type      VARCHAR(20)   NOT NULL
                    CHECK (component_type IN (''ALLOWANCE'',''DEDUCTION'')),
                allowance_type_id   INT
                    REFERENCES %I.payroll_allowance_type(id) ON DELETE SET NULL,
                deduction_type_id   INT
                    REFERENCES %I.payroll_deduction_type(id) ON DELETE SET NULL,
                calc_method         VARCHAR(20)   NOT NULL DEFAULT ''FIXED''
                    CHECK (calc_method IN (''FIXED'',''PERCENTAGE'')),
                amount              DECIMAL(19,4) NOT NULL DEFAULT 0,
                percentage          DECIMAL(7,4),
                is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
                created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_by          VARCHAR(100),
                updated_by          VARCHAR(100)
            )
        ', schema_rec.schema_name,
           schema_rec.schema_name,
           schema_rec.schema_name,
           schema_rec.schema_name);

        -- --------------------------------------------------------
        -- 6. payroll_adjustment
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_adjustment (
                id               BIGSERIAL PRIMARY KEY,
                company_id       INT           NOT NULL,
                branch_id        INT,
                employee_id      INT           NOT NULL
                    REFERENCES %I.hr_employee(id) ON DELETE RESTRICT,
                payroll_run_id   BIGINT,
                adjustment_type  VARCHAR(20)   NOT NULL
                    CHECK (adjustment_type IN (''ALLOWANCE'',''DEDUCTION'')),
                adjustment_code  VARCHAR(50)   NOT NULL,
                description      TEXT,
                amount           DECIMAL(19,4) NOT NULL DEFAULT 0,
                effective_date   DATE          NOT NULL,
                status           VARCHAR(20)   NOT NULL DEFAULT ''PENDING''
                    CHECK (status IN (
                        ''PENDING'',''APPROVED'',''REJECTED'',''APPLIED'',''CANCELLED'')),
                approved_by      VARCHAR(100),
                approved_at      TIMESTAMP,
                created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_by       VARCHAR(100),
                updated_by       VARCHAR(100)
            )
        ', schema_rec.schema_name, schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_pa_company_employee_status
            ON %I.payroll_adjustment (company_id, employee_id, status, effective_date)
        ', schema_rec.schema_name);

        -- --------------------------------------------------------
        -- 7. payroll_run
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_run (
                id                  BIGSERIAL PRIMARY KEY,
                company_id          INT           NOT NULL,
                branch_id           INT,
                run_label           VARCHAR(200),
                period_start        DATE          NOT NULL,
                period_end          DATE          NOT NULL,
                frequency           VARCHAR(30)   NOT NULL,
                currency_code       VARCHAR(3)    NOT NULL DEFAULT ''EGP'',
                status              VARCHAR(30)   NOT NULL DEFAULT ''DRAFT''
                    CHECK (status IN (
                        ''DRAFT'',''CALCULATED'',''APPROVED'',
                        ''POSTING_IN_PROGRESS'',''POSTED'',
                        ''PARTIALLY_PAID'',''PAID'',
                        ''CANCELLED'',''FAILED_POSTING'',''REVERSED'')),
                total_gross         DECIMAL(19,4) NOT NULL DEFAULT 0,
                total_deductions    DECIMAL(19,4) NOT NULL DEFAULT 0,
                total_net           DECIMAL(19,4) NOT NULL DEFAULT 0,
                employee_count      INT           NOT NULL DEFAULT 0,
                approved_by         VARCHAR(100),
                approved_at         TIMESTAMP,
                posting_request_id  UUID,
                posted_journal_id   UUID,
                posted_at           TIMESTAMP,
                version             INT           NOT NULL DEFAULT 0,
                created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_by          VARCHAR(100),
                updated_by          VARCHAR(100)
            )
        ', schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_pr_company_branch_period
            ON %I.payroll_run (company_id, branch_id, period_start, period_end, status)
        ', schema_rec.schema_name);

        -- --------------------------------------------------------
        -- 8. payroll_run_line
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_run_line (
                id                          BIGSERIAL PRIMARY KEY,
                company_id                  INT           NOT NULL,
                payroll_run_id              BIGINT        NOT NULL
                    REFERENCES %I.payroll_run(id) ON DELETE CASCADE,
                employee_id                 INT           NOT NULL
                    REFERENCES %I.hr_employee(id) ON DELETE RESTRICT,
                salary_profile_id           BIGINT        NOT NULL
                    REFERENCES %I.payroll_salary_profile(id) ON DELETE RESTRICT,
                base_salary                 DECIMAL(19,4) NOT NULL DEFAULT 0,
                total_allowances            DECIMAL(19,4) NOT NULL DEFAULT 0,
                total_deductions            DECIMAL(19,4) NOT NULL DEFAULT 0,
                gross_salary                DECIMAL(19,4) NOT NULL DEFAULT 0,
                net_salary                  DECIMAL(19,4) NOT NULL DEFAULT 0,
                paid_amount                 DECIMAL(19,4) NOT NULL DEFAULT 0,
                remaining_amount            DECIMAL(19,4) NOT NULL DEFAULT 0,
                payment_status              VARCHAR(20)   NOT NULL DEFAULT ''UNPAID''
                    CHECK (payment_status IN (''UNPAID'',''PARTIALLY_PAID'',''PAID'')),
                working_days                INT           NOT NULL DEFAULT 0,
                absent_days                 INT           NOT NULL DEFAULT 0,
                late_minutes                INT           NOT NULL DEFAULT 0,
                overtime_minutes            INT           NOT NULL DEFAULT 0,
                salary_type                 VARCHAR(30),
                payroll_frequency           VARCHAR(30),
                currency_code               VARCHAR(3),
                calculation_snapshot_json   TEXT,
                notes                       TEXT,
                created_at                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        ', schema_rec.schema_name,
           schema_rec.schema_name,
           schema_rec.schema_name,
           schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_prl_company_run
            ON %I.payroll_run_line (company_id, payroll_run_id)
        ', schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_prl_company_employee
            ON %I.payroll_run_line (company_id, employee_id)
        ', schema_rec.schema_name);

        -- --------------------------------------------------------
        -- 9. payroll_run_line_component
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_run_line_component (
                id                    BIGSERIAL PRIMARY KEY,
                company_id            INT           NOT NULL,
                payroll_run_line_id   BIGINT        NOT NULL
                    REFERENCES %I.payroll_run_line(id) ON DELETE CASCADE,
                component_type        VARCHAR(20)   NOT NULL
                    CHECK (component_type IN (''ALLOWANCE'',''DEDUCTION'')),
                type_id               INT,
                type_code             VARCHAR(50),
                type_name             VARCHAR(150),
                calc_method           VARCHAR(20),
                amount                DECIMAL(19,4) NOT NULL DEFAULT 0,
                source                VARCHAR(30)   NOT NULL DEFAULT ''PROFILE''
                    CHECK (source IN (''PROFILE'',''ADJUSTMENT'',''ATTENDANCE''))
            )
        ', schema_rec.schema_name, schema_rec.schema_name);

        -- --------------------------------------------------------
        -- 10. payroll_payment
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_payment (
                id                  BIGSERIAL PRIMARY KEY,
                company_id          INT           NOT NULL,
                payroll_run_id      BIGINT        NOT NULL
                    REFERENCES %I.payroll_run(id) ON DELETE RESTRICT,
                payment_date        DATE          NOT NULL,
                payment_method      VARCHAR(50),
                total_amount        DECIMAL(19,4) NOT NULL DEFAULT 0,
                currency_code       VARCHAR(3)    NOT NULL DEFAULT ''EGP'',
                reference_number    VARCHAR(100),
                status              VARCHAR(20)   NOT NULL DEFAULT ''COMPLETED''
                    CHECK (status IN (''COMPLETED'',''CANCELLED'')),
                posting_request_id  UUID,
                journal_id          UUID,
                posted_at           TIMESTAMP,
                notes               TEXT,
                version             INT           NOT NULL DEFAULT 0,
                created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_by          VARCHAR(100),
                updated_by          VARCHAR(100)
            )
        ', schema_rec.schema_name, schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_pp_company_run
            ON %I.payroll_payment (company_id, payroll_run_id)
        ', schema_rec.schema_name);

        -- --------------------------------------------------------
        -- 11. payroll_payment_line
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_payment_line (
                id                    BIGSERIAL PRIMARY KEY,
                company_id            INT           NOT NULL,
                payroll_payment_id    BIGINT        NOT NULL
                    REFERENCES %I.payroll_payment(id) ON DELETE CASCADE,
                payroll_run_line_id   BIGINT        NOT NULL
                    REFERENCES %I.payroll_run_line(id) ON DELETE RESTRICT,
                employee_id           INT           NOT NULL
                    REFERENCES %I.hr_employee(id) ON DELETE RESTRICT,
                net_salary            DECIMAL(19,4) NOT NULL DEFAULT 0,
                paid_amount           DECIMAL(19,4) NOT NULL DEFAULT 0,
                remaining_amount      DECIMAL(19,4) NOT NULL DEFAULT 0,
                payment_method        VARCHAR(50),
                payment_status        VARCHAR(20)   NOT NULL DEFAULT ''UNPAID''
                    CHECK (payment_status IN (''UNPAID'',''PARTIALLY_PAID'',''PAID'')),
                created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        ', schema_rec.schema_name,
           schema_rec.schema_name,
           schema_rec.schema_name,
           schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_ppl_company_employee
            ON %I.payroll_payment_line (company_id, employee_id)
        ', schema_rec.schema_name);

        -- --------------------------------------------------------
        -- 12. payroll_audit_log
        -- --------------------------------------------------------
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.payroll_audit_log (
                id             BIGSERIAL PRIMARY KEY,
                company_id     INT           NOT NULL,
                branch_id      INT,
                entity_type    VARCHAR(50)   NOT NULL,
                entity_id      VARCHAR(100)  NOT NULL,
                action         VARCHAR(100)  NOT NULL,
                old_value_json TEXT,
                new_value_json TEXT,
                performed_by   VARCHAR(100),
                performed_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                remarks        TEXT
            )
        ', schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_pal_company_entity
            ON %I.payroll_audit_log (company_id, entity_type, entity_id)
        ', schema_rec.schema_name);

    END LOOP;
END $$;
