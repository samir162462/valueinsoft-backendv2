-- ============================================================
-- V61: Attendance System Foundation
-- Adds core HR and Attendance tables to every tenant schema.
-- Includes Employees, Shifts, Logs, and Daily Summaries.
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
        -- 1. HR Employee Table
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.hr_employee (
                id SERIAL PRIMARY KEY,
                company_id INT NOT NULL,
                branch_id INT NOT NULL,
                employee_code VARCHAR(50) NOT NULL,
                pin_hash VARCHAR(100) NOT NULL,
                first_name VARCHAR(100) NOT NULL,
                last_name VARCHAR(100),
                user_id INT,
                is_active BOOLEAN DEFAULT TRUE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_by VARCHAR(100),
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_by VARCHAR(100),
                UNIQUE(company_id, branch_id, employee_code)
            )
        ', schema_rec.schema_name);

        -- 2. HR Shift Table
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.hr_shift (
                id SERIAL PRIMARY KEY,
                company_id INT NOT NULL,
                branch_id INT NOT NULL,
                shift_name VARCHAR(100) NOT NULL,
                start_time TIME NOT NULL,
                end_time TIME NOT NULL,
                grace_minutes INT DEFAULT 0,
                is_active BOOLEAN DEFAULT TRUE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_by VARCHAR(100),
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_by VARCHAR(100),
                UNIQUE(company_id, branch_id, shift_name)
            )
        ', schema_rec.schema_name);

        -- 3. HR Employee Shift Assignment
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.hr_employee_shift (
                id SERIAL PRIMARY KEY,
                company_id INT NOT NULL,
                branch_id INT NOT NULL,
                employee_id INT NOT NULL REFERENCES %I.hr_employee(id) ON DELETE CASCADE,
                shift_id INT NOT NULL REFERENCES %I.hr_shift(id) ON DELETE CASCADE,
                effective_from DATE NOT NULL,
                effective_to DATE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_by VARCHAR(100),
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_by VARCHAR(100)
            )
        ', schema_rec.schema_name, schema_rec.schema_name, schema_rec.schema_name);

        -- 4. HR Attendance Log
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.hr_attendance_log (
                id BIGSERIAL PRIMARY KEY,
                company_id INT NOT NULL,
                branch_id INT NOT NULL,
                employee_id INT NOT NULL REFERENCES %I.hr_employee(id) ON DELETE CASCADE,
                action_type VARCHAR(50) NOT NULL,
                action_time TIMESTAMP NOT NULL,
                source VARCHAR(100),
                device_id VARCHAR(100),
                ip_address VARCHAR(45),
                user_agent TEXT,
                remarks TEXT,
                correction_reason TEXT,
                manager_id VARCHAR(100),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_by VARCHAR(100)
            )
        ', schema_rec.schema_name, schema_rec.schema_name);

        -- 5. HR Attendance Day Summary
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.hr_attendance_day (
                id BIGSERIAL PRIMARY KEY,
                company_id INT NOT NULL,
                branch_id INT NOT NULL,
                employee_id INT NOT NULL REFERENCES %I.hr_employee(id) ON DELETE CASCADE,
                attendance_date DATE NOT NULL,
                clock_in TIMESTAMP,
                clock_out TIMESTAMP,
                working_minutes INT DEFAULT 0,
                break_minutes INT DEFAULT 0,
                late_minutes INT DEFAULT 0,
                overtime_minutes INT DEFAULT 0,
                status VARCHAR(50) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(company_id, branch_id, employee_id, attendance_date)
            )
        ', schema_rec.schema_name, schema_rec.schema_name);

        -- Indexes for performance
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_hr_attendance_log_employee ON %I.hr_attendance_log (employee_id, action_time DESC)', schema_rec.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_hr_attendance_day_date ON %I.hr_attendance_day (attendance_date, branch_id)', schema_rec.schema_name);

    END LOOP;
END $$;
