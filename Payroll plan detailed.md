# Payroll Module — Revised Implementation Plan v2

> **Progress**: Phase 1 — Step 1 ✅ complete

## 1. Architecture Summary

- **Backend**: Spring Boot, JdbcTemplate, tenant schemas `c_{companyId}`
- **Frontend**: React, sidebar via `appNavigationSchema.js`
- **DB**: PostgreSQL, Flyway migrations iterating `c_%` schemas
- **Auth**: Capability-based via `AuthorizationService`
- **Finance**: Existing `FinancePostingAdapter` pipeline — no duplication
- **HR Link**: `payroll_salary_profile.employee_id → hr_employee.id → user_id`

---

## 2. Database Design

### 2.1 Tables

#### `payroll_settings`
```sql
id SERIAL PK, company_id INT NOT NULL UNIQUE,
default_currency VARCHAR(3) DEFAULT 'EGP',
default_frequency VARCHAR(30) DEFAULT 'MONTHLY'
  CHECK (default_frequency IN ('WEEKLY','BIWEEKLY','MONTHLY','CUSTOM')),
auto_include_attendance BOOLEAN DEFAULT TRUE,
overtime_rate_multiplier DECIMAL(5,2) DEFAULT 1.50,
late_deduction_per_minute DECIMAL(19,4) DEFAULT 0,
salary_expense_account_id UUID, salary_payable_account_id UUID,
cash_bank_account_id UUID, version INT DEFAULT 0,
created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW(),
created_by VARCHAR(100), updated_by VARCHAR(100)
```

#### `payroll_allowance_type`
```sql
id SERIAL PK, company_id INT NOT NULL,
code VARCHAR(50) NOT NULL, name VARCHAR(150) NOT NULL,
is_taxable BOOLEAN DEFAULT FALSE, is_active BOOLEAN DEFAULT TRUE,
created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW(),
created_by VARCHAR(100), updated_by VARCHAR(100),
UNIQUE(company_id, code)
```

#### `payroll_deduction_type`
```sql
id SERIAL PK, company_id INT NOT NULL,
code VARCHAR(50) NOT NULL, name VARCHAR(150) NOT NULL,
is_statutory BOOLEAN DEFAULT FALSE, is_active BOOLEAN DEFAULT TRUE,
created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW(),
created_by VARCHAR(100), updated_by VARCHAR(100),
UNIQUE(company_id, code)
```

#### `payroll_salary_profile`
```sql
id BIGSERIAL PK, company_id INT NOT NULL,
employee_id INT NOT NULL REFERENCES hr_employee(id),
branch_id INT NOT NULL, job_title VARCHAR(150),
salary_type VARCHAR(30) NOT NULL DEFAULT 'MONTHLY'
  CHECK (salary_type IN ('MONTHLY','WEEKLY','DAILY','HOURLY','FLEXIBLE','COMMISSION')),
base_salary DECIMAL(19,4) NOT NULL DEFAULT 0,
currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
payroll_frequency VARCHAR(30) NOT NULL DEFAULT 'MONTHLY'
  CHECK (payroll_frequency IN ('WEEKLY','BIWEEKLY','MONTHLY','CUSTOM')),
salary_expense_account_id UUID,   -- override per profile
salary_payable_account_id UUID,   -- override per profile
is_active BOOLEAN DEFAULT TRUE,
effective_from DATE NOT NULL, effective_to DATE,
version INT NOT NULL DEFAULT 0,
created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW(),
created_by VARCHAR(100), updated_by VARCHAR(100),
UNIQUE(company_id, employee_id, effective_from)
```

> Account resolution: profile account → payroll_settings account → reject if missing.

#### `payroll_salary_component`
```sql
id BIGSERIAL PK, company_id INT NOT NULL,
salary_profile_id BIGINT NOT NULL REFERENCES payroll_salary_profile(id),
component_type VARCHAR(20) NOT NULL CHECK (component_type IN ('ALLOWANCE','DEDUCTION')),
allowance_type_id INT REFERENCES payroll_allowance_type(id),
deduction_type_id INT REFERENCES payroll_deduction_type(id),
calc_method VARCHAR(20) NOT NULL DEFAULT 'FIXED' CHECK (calc_method IN ('FIXED','PERCENTAGE')),
amount DECIMAL(19,4) NOT NULL DEFAULT 0, percentage DECIMAL(7,4),
is_active BOOLEAN DEFAULT TRUE,
created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW(),
created_by VARCHAR(100), updated_by VARCHAR(100)
```

#### `payroll_adjustment`
```sql
id BIGSERIAL PK, company_id INT NOT NULL, branch_id INT,
employee_id INT NOT NULL REFERENCES hr_employee(id),
payroll_run_id BIGINT,  -- set when APPLIED
adjustment_type VARCHAR(20) NOT NULL CHECK (adjustment_type IN ('ALLOWANCE','DEDUCTION')),
adjustment_code VARCHAR(50) NOT NULL,
description TEXT, amount DECIMAL(19,4) NOT NULL DEFAULT 0,
effective_date DATE NOT NULL,
status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
  CHECK (status IN ('PENDING','APPROVED','REJECTED','APPLIED','CANCELLED')),
approved_by VARCHAR(100), approved_at TIMESTAMP,
created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW(),
created_by VARCHAR(100), updated_by VARCHAR(100)
```

#### `payroll_run`
```sql
id BIGSERIAL PK, company_id INT NOT NULL, branch_id INT,
run_label VARCHAR(200), period_start DATE NOT NULL, period_end DATE NOT NULL,
frequency VARCHAR(30) NOT NULL, currency_code VARCHAR(3) DEFAULT 'EGP',
status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
  CHECK (status IN ('DRAFT','CALCULATED','APPROVED','POSTING_IN_PROGRESS',
    'POSTED','PARTIALLY_PAID','PAID','CANCELLED','FAILED_POSTING','REVERSED')),
total_gross DECIMAL(19,4) DEFAULT 0, total_deductions DECIMAL(19,4) DEFAULT 0,
total_net DECIMAL(19,4) DEFAULT 0, employee_count INT DEFAULT 0,
approved_by VARCHAR(100), approved_at TIMESTAMP,
posting_request_id UUID, posted_journal_id UUID, posted_at TIMESTAMP,
version INT NOT NULL DEFAULT 0,
created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW(),
created_by VARCHAR(100), updated_by VARCHAR(100)
```

#### `payroll_run_line`
```sql
id BIGSERIAL PK, company_id INT NOT NULL,
payroll_run_id BIGINT NOT NULL REFERENCES payroll_run(id),
employee_id INT NOT NULL REFERENCES hr_employee(id),
salary_profile_id BIGINT NOT NULL REFERENCES payroll_salary_profile(id),
base_salary DECIMAL(19,4) DEFAULT 0,
total_allowances DECIMAL(19,4) DEFAULT 0, total_deductions DECIMAL(19,4) DEFAULT 0,
gross_salary DECIMAL(19,4) DEFAULT 0, net_salary DECIMAL(19,4) DEFAULT 0,
paid_amount DECIMAL(19,4) DEFAULT 0, remaining_amount DECIMAL(19,4) DEFAULT 0,
payment_status VARCHAR(20) DEFAULT 'UNPAID'
  CHECK (payment_status IN ('UNPAID','PARTIALLY_PAID','PAID')),
working_days INT DEFAULT 0, absent_days INT DEFAULT 0,
late_minutes INT DEFAULT 0, overtime_minutes INT DEFAULT 0,
salary_type VARCHAR(30), payroll_frequency VARCHAR(30),
calculation_snapshot_json TEXT, notes TEXT,
created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW()
```

#### `payroll_run_line_component`
```sql
id BIGSERIAL PK, company_id INT NOT NULL,
payroll_run_line_id BIGINT NOT NULL REFERENCES payroll_run_line(id),
component_type VARCHAR(20) NOT NULL CHECK (component_type IN ('ALLOWANCE','DEDUCTION')),
type_id INT, type_code VARCHAR(50), type_name VARCHAR(150),
calc_method VARCHAR(20), amount DECIMAL(19,4) NOT NULL DEFAULT 0,
source VARCHAR(30) DEFAULT 'PROFILE'  -- PROFILE, ADJUSTMENT, ATTENDANCE
```

#### `payroll_payment`
```sql
id BIGSERIAL PK, company_id INT NOT NULL,
payroll_run_id BIGINT NOT NULL REFERENCES payroll_run(id),
payment_date DATE NOT NULL, payment_method VARCHAR(50),
total_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
currency_code VARCHAR(3) DEFAULT 'EGP', reference_number VARCHAR(100),
status VARCHAR(20) DEFAULT 'COMPLETED'
  CHECK (status IN ('COMPLETED','CANCELLED')),
posting_request_id UUID, journal_id UUID, posted_at TIMESTAMP,
notes TEXT, version INT DEFAULT 0,
created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW(),
created_by VARCHAR(100), updated_by VARCHAR(100)
```

#### `payroll_payment_line`
```sql
id BIGSERIAL PK, company_id INT NOT NULL,
payroll_payment_id BIGINT NOT NULL REFERENCES payroll_payment(id),
payroll_run_line_id BIGINT NOT NULL REFERENCES payroll_run_line(id),
employee_id INT NOT NULL REFERENCES hr_employee(id),
net_salary DECIMAL(19,4) NOT NULL, paid_amount DECIMAL(19,4) NOT NULL,
remaining_amount DECIMAL(19,4) NOT NULL,
payment_method VARCHAR(50),  -- per-employee override
payment_status VARCHAR(20) NOT NULL
  CHECK (payment_status IN ('UNPAID','PARTIALLY_PAID','PAID')),
created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW()
```

#### `payroll_audit_log`
```sql
id BIGSERIAL PK, company_id INT NOT NULL, branch_id INT,
entity_type VARCHAR(50) NOT NULL, entity_id VARCHAR(100) NOT NULL,
action VARCHAR(100) NOT NULL,
old_value_json TEXT, new_value_json TEXT,
performed_by VARCHAR(100), performed_at TIMESTAMP DEFAULT NOW(),
remarks TEXT
```

### 2.2 Indexes

```sql
-- salary_profile
idx_psp_company_employee_active (company_id, employee_id, is_active)
idx_psp_company_branch_active   (company_id, branch_id, is_active)
-- payroll_run
idx_pr_company_branch_period    (company_id, branch_id, period_start, period_end, status)
-- payroll_run_line
idx_prl_company_run             (company_id, payroll_run_id)
idx_prl_company_employee        (company_id, employee_id)
-- payroll_payment
idx_pp_company_run              (company_id, payroll_run_id)
-- payroll_payment_line
idx_ppl_company_employee        (company_id, employee_id)
-- payroll_adjustment
idx_pa_company_employee_status  (company_id, employee_id, status, effective_date)
-- payroll_audit_log
idx_pal_company_entity          (company_id, entity_type, entity_id)
```

---

## 3. Backend Files

### 3.1 New Model POJOs (`Model/Payroll/`)

`PayrollSettings`, `PayrollAllowanceType`, `PayrollDeductionType`, `PayrollSalaryProfile`, `PayrollSalaryComponent`, `PayrollAdjustment`, `PayrollRun`, `PayrollRunLine`, `PayrollRunLineComponent`, `PayrollPayment`, `PayrollPaymentLine`, `PayrollAuditLog`, `CurrentSalaryView`

All: Lombok `@Data @NoArgsConstructor @AllArgsConstructor`

### 3.2 TenantSqlIdentifiers — 12 new methods

`payrollSettingsTable`, `payrollAllowanceTypeTable`, `payrollDeductionTypeTable`, `payrollSalaryProfileTable`, `payrollSalaryComponentTable`, `payrollAdjustmentTable`, `payrollRunTable`, `payrollRunLineTable`, `payrollRunLineComponentTable`, `payrollPaymentTable`, `payrollPaymentLineTable`, `payrollAuditLogTable`

### 3.3 Repository: `DbPayroll.java`

Single repository. RowMappers + CRUD for all 12 tables. Aggregate query for `CurrentSalaryView` joining `hr_employee LEFT JOIN payroll_salary_profile`.

### 3.4 Services

| Service | Key Methods |
|---------|------------|
| **PayrollSettingsService** | `get`, `update` |
| **PayrollSalaryProfileService** | `create`, `update`, `deactivate`, `listByCompany`, `getByEmployee` |
| **PayrollValidationService** | `validateNoOverlap`, `validatePeriod`, `validateAccounts`, `validateRunEditable`, `validatePaymentAmount` |
| **PayrollStateMachineService** | `assertTransition(current, target)` — enforces state graph |
| **CurrentSalaryService** | `listAll` (with CONFIGURED/MISSING filter) |
| **PayrollAdjustmentService** | `create`, `approve`, `reject`, `cancel` |
| **PayrollAttendanceIntegrationService** | `getAttendanceForPeriod(companyId, employeeId, start, end)` — queries `DbHR` |
| **PayrollCalculationService** | `calculateRunLines` — snapshots salary, components, attendance, approved adjustments |
| **PayrollRunService** | `generate`, `recalculate`, `approve`, `postToFinance`, `cancel`, `reverse` |
| **PayrollPostingService** | `enqueueAccrual`, `enqueuePayment` — uses `FinanceOperationalPostingService` pattern |
| **PayrollPaymentService** | `payAll`, `payEmployee`, `payPartial` — creates payment + payment_lines, updates run_line paid/remaining |
| **PayrollAuditService** | `record(companyId, entityType, entityId, action, oldJson, newJson, actor, remarks)` |

### 3.5 Finance Adapter: `FinancePayrollPostingAdapter.java`

```
implements FinancePostingAdapter
supports("payroll") → true

sourceType "salary_accrual":
  Dr salary_expense_account (profile override or settings default)
  Cr salary_payable_account

sourceType "salary_payment":
  Dr salary_payable_account
  Cr cash_bank_account
```

### 3.6 Controller: `PayrollController.java`

```
@RequestMapping("/api/payroll")

GET  /{companyId}/settings
PUT  /{companyId}/settings
GET  /{companyId}/current-salaries?filter=ALL|CONFIGURED|MISSING
GET  /{companyId}/salary-profiles
POST /{companyId}/salary-profiles
PUT  /{companyId}/salary-profiles/{id}
POST /{companyId}/salary-profiles/{id}/deactivate
GET  /{companyId}/allowance-types
POST /{companyId}/allowance-types
GET  /{companyId}/deduction-types
POST /{companyId}/deduction-types
GET  /{companyId}/adjustments
POST /{companyId}/adjustments
POST /{companyId}/adjustments/{id}/approve
POST /{companyId}/adjustments/{id}/reject
POST /{companyId}/runs/generate
GET  /{companyId}/runs
GET  /{companyId}/runs/{id}
POST /{companyId}/runs/{id}/recalculate
POST /{companyId}/runs/{id}/approve
POST /{companyId}/runs/{id}/post-to-finance
POST /{companyId}/runs/{id}/cancel
POST /{companyId}/runs/{id}/reverse
POST /{companyId}/runs/{id}/payments
POST /{companyId}/runs/{id}/payments/pay-all
POST /{companyId}/runs/{id}/payments/pay-employee
GET  /{companyId}/runs/{id}/payments
GET  /{companyId}/audit-log?entityType=&entityId=
```

---

## 4. Finance Posting Design

### Idempotency

| Guard | Column | Check |
|-------|--------|-------|
| Accrual | `payroll_run.posted_journal_id` | If NOT NULL → reject with `PAYROLL_ALREADY_POSTED` |
| Payment | `payroll_payment.journal_id` | If NOT NULL → reject with `PAYMENT_ALREADY_POSTED` |
| Source dedup | `FinancePostingRequestService` | Uses unique `sourceId`: `payroll-accrual-{companyId}-{runId}` or `payroll-payment-{companyId}-{paymentId}` |

### Posting Flow (Accrual)

```
PayrollRunService.postToFinance(runId)
  → PayrollStateMachineService.assertTransition(APPROVED → POSTING_IN_PROGRESS)
  → update status to POSTING_IN_PROGRESS
  → PayrollPostingService.enqueueAccrual(run)
    → resolve accounts per employee (profile override → settings default)
    → aggregate total Dr/Cr amounts
    → FinancePostingRequestService.createPostingRequestFromSystem(
        sourceModule="payroll", sourceType="salary_accrual",
        sourceId="payroll-accrual-{companyId}-{runId}")
  → FinancePayrollPostingAdapter.post(request) creates journal
  → update payroll_run: posted_journal_id, posted_at, status=POSTED
  → on failure: status=FAILED_POSTING
```

### Posting Flow (Payment)

```
PayrollPaymentService.payAll(runId)
  → create payroll_payment + payroll_payment_lines
  → update each payroll_run_line: paid_amount, remaining_amount, payment_status
  → update payroll_run status (PAID or PARTIALLY_PAID)
  → PayrollPostingService.enqueuePayment(payment)
    → sourceId="payroll-payment-{companyId}-{paymentId}"
  → FinancePayrollPostingAdapter handles sourceType "salary_payment"
  → update payroll_payment: journal_id, posted_at
```

---

## 5. Payroll Calculation Design

### Generation Flow

1. Query active `payroll_salary_profile` WHERE `effective_from <= period_end AND (effective_to IS NULL OR effective_to >= period_start) AND is_active`
2. For each employee:
   - Snapshot base salary, salary_type, frequency, currency
   - Load active `payroll_salary_component` rows
   - Load `APPROVED` adjustments WHERE `effective_date BETWEEN period_start AND period_end`
   - Load attendance from `hr_attendance_day` (if `auto_include_attendance`)
   - Calculate: `gross = base + allowances + approved_allowance_adjustments`
   - Calculate: `deductions = components + approved_deduction_adjustments + attendance_deductions`
   - Calculate: `net = gross - deductions`
   - Store `calculation_snapshot_json` with all inputs used
   - Mark included adjustments as `APPLIED` with `payroll_run_id`
3. Sum totals into `payroll_run`

### Salary Profile Change Rules

- To change salary: set `effective_to` on old profile, create new profile with new `effective_from`
- Service rejects overlapping active profiles for same employee
- Old payroll runs keep snapshot values — never recalculated from updated profiles

### Overlap Validation (PayrollValidationService)

```
For same company_id + employee_id + is_active=true:
Reject if any existing profile has:
  existing.effective_from < new.effective_to (or new has no end)
  AND (existing.effective_to IS NULL OR existing.effective_to > new.effective_from)
```

---

## 6. Frontend Design

### 6.1 Domain Structure

```
src/domains/payroll/
├── api/payrollApi.js
├── components/
│   ├── PayrollWorkspace.js        -- tab container
│   ├── PayrollDashboardCards.js    -- summary cards
│   ├── CurrentSalariesTab.js      -- table with CONFIGURED/MISSING badges
│   ├── SalaryProfileForm.js       -- create/edit modal
│   ├── PayrollRunsTab.js          -- runs list
│   ├── PayrollRunWizard.js        -- 5-step wizard (branch→freq→period→preview→generate)
│   ├── PayrollRunDetail.js        -- summary + employee breakdown + components + adjustments
│   ├── PayrollPaymentsTab.js      -- payment records with employee lines
│   ├── PayrollAdjustmentsTab.js   -- manual adjustments
│   ├── PayrollSettingsTab.js      -- settings + account mapping
│   └── PayrollAuditTab.js         -- audit log viewer
└── styles/payrollWorkspace.css
```

### 6.2 Dashboard Cards

| Card | Data |
|------|------|
| Employees with salary | Count of CONFIGURED |
| Missing salary setup | Count of MISSING |
| Current month total | Sum of net from latest run |
| Pending runs | Count status DRAFT/CALCULATED |
| Unpaid salaries | Sum remaining_amount > 0 |
| Posted amount | Sum from POSTED runs |

### 6.3 Current Salaries Table

Columns: Employee Name, Branch, Job Title, Salary Type, Base Salary, Frequency, Allowances, Deductions, Expected Net, Status (CONFIGURED/MISSING badge), Actions.

Filter dropdown: ALL / CONFIGURED / MISSING.

### 6.4 Payroll Run Wizard Steps

1. Select branch (or all)
2. Select frequency
3. Select period (start/end dates)
4. Preview employees (with salary totals)
5. Confirm → Generate

### 6.5 UX Rules

- Buttons disabled based on status + capability
- Confirmation dialog before posting to finance
- Warning banner: "Posted payroll cannot be edited"
- Payment modal: pay all, pay single employee, partial amount

### 6.6 Sidebar (`appNavigationSchema.js`)

New group `hr_payroll` with icon `FaMoneyBillWave`:
Items: Current Salaries, Salary Profiles, Adjustments, Payroll Runs, Payments, Payroll Settings, Attendance, Employees, Shifts, Shift Assignments.

### 6.7 View Access (`appShellAccess.js`)

```
PayrollCurrentSalaries: { moduleId:'payroll', requiredCapabilities:['payroll.profile.read'] }
PayrollSalaryProfiles:  { moduleId:'payroll', requiredCapabilities:['payroll.profile.read'] }
PayrollAdjustments:     { moduleId:'payroll', requiredCapabilities:['payroll.adjustment.read'] }
PayrollRuns:            { moduleId:'payroll', requiredCapabilities:['payroll.run.read'] }
PayrollPayments:        { moduleId:'payroll', requiredCapabilities:['payroll.payment.read'] }
PayrollSettings:        { moduleId:'payroll', requiredCapabilities:['payroll.settings.read'] }
PayrollAudit:           { moduleId:'payroll', requiredCapabilities:['payroll.audit.read'] }
```

---

## 7. Permissions

```
payroll.profile.read, payroll.profile.create, payroll.profile.edit, payroll.profile.deactivate
payroll.adjustment.read, payroll.adjustment.create, payroll.adjustment.approve
payroll.run.read, payroll.run.create, payroll.run.recalculate
payroll.run.approve, payroll.run.post, payroll.run.reverse
payroll.payment.read, payroll.payment.create
payroll.settings.read, payroll.settings.edit
payroll.audit.read
```

Flyway migration seeds these into capability definitions and grants Owner + Admin roles.

---

## 8. State Machine

```
DRAFT → CALCULATED, CANCELLED
CALCULATED → APPROVED, CANCELLED
APPROVED → POSTING_IN_PROGRESS
POSTING_IN_PROGRESS → POSTED, FAILED_POSTING
FAILED_POSTING → POSTING_IN_PROGRESS (retry)
POSTED → PARTIALLY_PAID, PAID, REVERSED
PARTIALLY_PAID → PAID
PAID → (locked)
CANCELLED → (locked)
REVERSED → (locked)
```

No editing after POSTED. Corrections via reversal or next-period adjustments.

---

## 9. Validation Rules

| Rule | Service |
|------|---------|
| No overlapping active salary profiles per employee | PayrollValidationService |
| Period start ≤ period end | PayrollValidationService |
| Employee has active salary profile for period | PayrollValidationService |
| Finance accounts exist (profile override or settings) | PayrollValidationService |
| Run is in editable status (DRAFT/CALCULATED) | PayrollStateMachineService |
| Payment amount ≤ remaining per employee | PayrollValidationService |
| Adjustments approved before inclusion | PayrollCalculationService |
| No duplicate posting (journal_id check) | PayrollPostingService |
| Status transitions follow state machine | PayrollStateMachineService |
| Optimistic lock via version column | DbPayroll (WHERE version = ?) |

---

## 10. Testing Plan

| Area | Tests |
|------|-------|
| Salary profile CRUD | Create, update, deactivate, overlap rejection |
| Calculation | Correct gross/net with components, adjustments, attendance |
| State machine | Valid transitions succeed, invalid throw |
| Posting idempotency | Double-post returns same journal, no duplicate |
| Payment | Full, partial, per-employee; remaining recalculation |
| Account resolution | Profile override vs settings default |
| Permissions | Each endpoint rejects unauthorized |
| Snapshots | Old runs unchanged after profile edit |
| Adjustments | Lifecycle: PENDING → APPROVED → APPLIED |
| Audit | All actions produce audit entries |

---

## 11. Risks & Mitigation

| Risk | Mitigation |
|------|-----------|
| Employee duplication | FK to hr_employee only; no new employee table |
| Finance logic duplication | FinancePostingAdapter pipeline; never create journals directly |
| Double posting | Check posted_journal_id + unique sourceId in posting request |
| Stale salary data in old runs | calculation_snapshot_json preserves point-in-time values |
| Overlapping profiles | Service-level date range validation before save |
| Concurrent edits | Optimistic locking via version column |
| Partial payment accounting errors | Payment lines track per-employee paid/remaining |
| Missing accounts | PayrollValidationService checks before posting |
| Status skipping | PayrollStateMachineService enforces transitions |

---

## 12. Implementation Phases

### MVP Phase 1 — Core Payroll

| Step | File/Task |
|------|-----------|
| ✅ 1 | `V71__payroll_module_foundation.sql` — all tables, constraints, indexes — **DONE** `src/main/resources/db/migration/V71__payroll_module_foundation.sql` |
| 2 | `V72__payroll_capabilities.sql` — register permissions, grant Owner/Admin |
| 3 | `TenantSqlIdentifiers.java` — add 12 table methods |
| 4 | `Model/Payroll/*.java` — 13 POJOs |
| 5 | `DbPayroll.java` — repository with all mappers and CRUD |
| 6 | `PayrollStateMachineService.java` |
| 7 | `PayrollValidationService.java` |
| 8 | `PayrollAuditService.java` |
| 9 | `PayrollSettingsService.java` |
| 10 | `PayrollSalaryProfileService.java` (with overlap validation) |
| 11 | `CurrentSalaryService.java` (CONFIGURED/MISSING filter) |
| 12 | `PayrollAttendanceIntegrationService.java` |
| 13 | `PayrollCalculationService.java` (with snapshots) |
| 14 | `PayrollRunService.java` (generate, recalculate, approve, cancel) |
| 15 | Add `"payroll"` to `FinancePostingRequestService.SOURCE_MODULES` |
| 16 | `FinancePayrollPostingAdapter.java` |
| 17 | `PayrollPostingService.java` (with idempotency guards) |
| 18 | `PayrollPaymentService.java` (pay-all only in MVP1) |
| 19 | `PayrollController.java` — all endpoints |
| 20 | `domains/payroll/api/payrollApi.js` |
| 21 | `PayrollWorkspace.js` + `PayrollDashboardCards.js` |
| 22 | `CurrentSalariesTab.js` |
| 23 | `SalaryProfileForm.js` |
| 24 | `PayrollRunsTab.js` + `PayrollRunWizard.js` |
| 25 | `PayrollRunDetail.js` |
| 26 | `PayrollSettingsTab.js` |
| 27 | `payrollWorkspace.css` |
| 28 | Update `appNavigationSchema.js` — HR & Payroll group |
| 29 | Update `appShellAccess.js` — view access rules + view order |
| 30 | Wire payroll viewIds in main app shell renderer |

### MVP Phase 2 — Advanced Payments & Adjustments

| Step | Task |
|------|------|
| 31 | Employee-level payments (pay single, partial) |
| 32 | `PayrollAdjustmentsTab.js` frontend |
| 33 | Adjustment approval workflow |
| 34 | Attendance deductions (late penalty, absent deduction) |
| 35 | Overtime calculation with rate multiplier |
| 36 | `PayrollAuditTab.js` frontend |
| 37 | Different payment methods per employee (cash/bank) |

### MVP Phase 3 — Future Extensions

| Step | Task |
|------|------|
| 38 | Payslip PDF generation |
| 39 | Salary advance / employee loans |
| 40 | Leave integration |
| 41 | Bank transfer file export |
| 42 | Reversal journal entries |
| 43 | Payroll reports (monthly summary, department breakdown) |
