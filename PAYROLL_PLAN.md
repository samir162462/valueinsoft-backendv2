# Payroll & Salaries Module - Implementation Plan

> Stack: Spring Boot, JdbcTemplate, PostgreSQL, tenant schemas `c_{companyId}`, React frontend  
> Last updated: 2026-05-01

---

## Progress Legend

| Status | Meaning |
|--------|---------|
| Done | Implemented and compile/build verified |
| In progress | Partially implemented; needs hardening or full workflow validation |
| Not started | Not implemented |

---

## Phase 1 - MVP Core

### Backend Foundation

| # | Task | File | Status |
|---|------|------|--------|
| 1 | Flyway: all 12 payroll tables, CHECK constraints, FKs, indexes | `src/main/resources/db/migration/V71__payroll_module_foundation.sql` | Done |
| 2 | Flyway: payroll capability definitions + role grants | `src/main/resources/db/migration/V72__payroll_capabilities.sql` | Done |
| 3 | Add 12 payroll table accessor methods | `TenantSqlIdentifiers.java` | Done |

### Model POJOs

| # | Task | File | Status |
|---|------|------|--------|
| 4a | PayrollSettings POJO | `Model/Payroll/PayrollSettings.java` | Done |
| 4b | PayrollAllowanceType POJO | `Model/Payroll/PayrollAllowanceType.java` | Done |
| 4c | PayrollDeductionType POJO | `Model/Payroll/PayrollDeductionType.java` | Done |
| 4d | PayrollSalaryProfile POJO | `Model/Payroll/PayrollSalaryProfile.java` | Done |
| 4e | PayrollSalaryComponent POJO | `Model/Payroll/PayrollSalaryComponent.java` | Done |
| 4f | PayrollAdjustment POJO | `Model/Payroll/PayrollAdjustment.java` | Done |
| 4g | PayrollRun POJO | `Model/Payroll/PayrollRun.java` | Done |
| 4h | PayrollRunLine POJO | `Model/Payroll/PayrollRunLine.java` | Done |
| 4i | PayrollRunLineComponent POJO | `Model/Payroll/PayrollRunLineComponent.java` | Done |
| 4j | PayrollPayment POJO | `Model/Payroll/PayrollPayment.java` | Done |
| 4k | PayrollPaymentLine POJO | `Model/Payroll/PayrollPaymentLine.java` | Done |
| 4l | PayrollAuditLog POJO | `Model/Payroll/PayrollAuditLog.java` | Done |
| 4m | CurrentSalaryView read model | `Model/Payroll/CurrentSalaryView.java` | Done |

### Repository

| # | Task | File | Status |
|---|------|------|--------|
| 5 | RowMappers + CRUD for payroll tables + CurrentSalaryView query | `DatabaseRequests/DbPayroll.java` | Done |

### Services

| # | Task | File | Status |
|---|------|------|--------|
| 6 | State machine - enforce payroll status transitions | `PayrollStateMachineService.java` | Done |
| 7 | Validation - overlap, accounts, period, payment amount | `PayrollValidationService.java` | Done |
| 8 | Audit - record payroll events | `PayrollAuditService.java` | Done |
| 9 | Settings - get/update | `PayrollSettingsService.java` | Done |
| 10 | Salary profile - CRUD, deactivate, overlap guard | `PayrollSalaryProfileService.java` | Done |
| 11 | Current salary - CONFIGURED/MISSING filter | `CurrentSalaryService.java` | Done |
| 12 | Attendance integration - pull `hr_attendance_day` by period | `PayrollAttendanceIntegrationService.java` | Done |
| 13 | Calculation - gross/net, snapshots, adjustments, attendance | `PayrollCalculationService.java` | Done |
| 14 | Run - generate, recalculate, approve, cancel, reverse, enqueue posting | `PayrollRunService.java` | Done |
| 15 | Add `payroll` to finance posting source modules | `FinancePostingRequestService.java` | Done |
| 16 | Finance posting adapter for salary accrual/payment | `FinancePayrollPostingAdapter.java` | Done |
| 17 | Posting - enqueue accrual/payment with idempotency guards | `PayrollPostingService.java` | Done |
| 18 | Payment - pay-all, pay single, partial payment | `PayrollPaymentService.java` | Done |

### Controller

| # | Task | File | Status |
|---|------|------|--------|
| 19 | Payroll REST endpoints | `Controller/PayrollController.java` | Done |

### Frontend

| # | Task | File | Status |
|---|------|------|--------|
| 20 | API client | `src/domains/payroll/api/payrollApi.js` | Done |
| 21 | Workspace container + dashboard cards | `PayrollWorkspace.js`, `PayrollDashboardCards.js` | Done |
| 22 | Current Salaries table with CONFIGURED/MISSING badges/filter | `CurrentSalariesTab.js` | Done |
| 23 | Salary profile create/edit modal | `SalaryProfileForm.js` | Done |
| 24 | Payroll Runs list + 5-step wizard | `PayrollRunsTab.js`, `PayrollRunWizard.js` | Done |
| 25 | Payroll Run detail with employee lines and payslip action | `PayrollRunDetail.js` | Done |
| 26 | Payments tab | `PayrollPaymentsTab.js` | Done |
| 27 | Settings tab | `PayrollSettingsTab.js` | Done |
| 28 | Payroll workspace CSS | `styles/payrollWorkspace.css` | Done |

### Navigation Wiring

| # | Task | File | Status |
|---|------|------|--------|
| 29 | Add HR & Payroll sidebar group | `appNavigationSchema.js`, `Aside.js` | Done |
| 30 | Add payroll view access rules + renderer wiring | `appShellAccess.js`, `Main.js` | Done |

---

## Phase 2 - Advanced Payments & Adjustments

| # | Task | Status |
|---|------|--------|
| 31 | Employee-level payments: pay single and partial | Done |
| 32 | Adjustments tab frontend | Done |
| 33 | Adjustment approval/reject/cancel workflow | Done |
| 34 | Attendance deductions: late penalty and absent deduction | Done |
| 35 | Overtime calculation with rate multiplier | Done |
| 36 | Audit log tab frontend | Done |
| 37 | Different payment method per employee | Done |

---

## Phase 3 - Future Extensions

| # | Task | Status |
|---|------|--------|
| 38 | Payslip PDF generation | Done |
| 39 | Salary advance / employee loans | Not started |
| 40 | Leave integration | Not started |
| 41 | Bank transfer file export | Not started |
| 42 | Reversal journal entries | Not started |
| 43 | Payroll reports: monthly summary and department breakdown | Not started |

---

## Implemented Backend Endpoints

Base path: `/api/payroll`

| Area | Endpoints |
|------|-----------|
| Settings | `GET /{companyId}/settings`, `PUT /{companyId}/settings` |
| Current salaries | `GET /{companyId}/current-salaries?filter=ALL|CONFIGURED|MISSING` |
| Salary profiles | `GET /{companyId}/salary-profiles`, `POST /{companyId}/salary-profiles`, `PUT /{companyId}/salary-profiles/{id}`, `POST /{companyId}/salary-profiles/{id}/deactivate` |
| Types | `GET/POST /{companyId}/allowance-types`, `GET/POST /{companyId}/deduction-types` |
| Adjustments | `GET/POST /{companyId}/adjustments`, `POST /{companyId}/adjustments/{id}/approve`, `POST /{companyId}/adjustments/{id}/reject`, `POST /{companyId}/adjustments/{id}/cancel` |
| Runs | `POST /{companyId}/runs/generate`, `GET /{companyId}/runs`, `GET /{companyId}/runs/{id}`, `GET /{companyId}/runs/{id}/lines` |
| Run workflow | `POST /{companyId}/runs/{id}/recalculate`, `POST /{companyId}/runs/{id}/approve`, `POST /{companyId}/runs/{id}/post-to-finance`, `POST /{companyId}/runs/{id}/cancel`, `POST /{companyId}/runs/{id}/reverse` |
| Payments | `GET /{companyId}/runs/{id}/payments`, `POST /{companyId}/runs/{id}/payments/pay-all`, `POST /{companyId}/runs/{id}/payments/pay-employee`, `GET /{companyId}/payments/{paymentId}/lines` |
| Audit | `GET /{companyId}/audit-log?entityType=&entityId=` |
| Payslips | `GET /{companyId}/runs/{id}/payslips/{employeeId}` returns PDF |

---

## Database Tables

| Table | Purpose |
|-------|---------|
| `payroll_settings` | Company defaults: currency, frequency, finance accounts |
| `payroll_allowance_type` | Allowance catalogue |
| `payroll_deduction_type` | Deduction catalogue |
| `payroll_salary_profile` | Per-employee salary configuration and effective dates |
| `payroll_salary_component` | Fixed/percentage allowances and deductions per profile |
| `payroll_adjustment` | One-time bonus, penalty, commission lifecycle |
| `payroll_run` | Payroll run header and totals |
| `payroll_run_line` | One employee row per payroll run |
| `payroll_run_line_component` | Allowance/deduction breakdown |
| `payroll_payment` | Payment batch header |
| `payroll_payment_line` | Per-employee payment detail |
| `payroll_audit_log` | Payroll audit trail |

---

## Payroll Run Status Machine

```text
DRAFT -> CALCULATED, CANCELLED
CALCULATED -> APPROVED, CANCELLED
APPROVED -> POSTING_IN_PROGRESS
POSTING_IN_PROGRESS -> POSTED, FAILED_POSTING
FAILED_POSTING -> POSTING_IN_PROGRESS
POSTED -> PARTIALLY_PAID, PAID, REVERSED
PARTIALLY_PAID -> PAID
PAID -> locked
CANCELLED -> locked
REVERSED -> locked
```

No direct editing after `POSTED`. Corrections should use reversal or next-period adjustments.

---

## Finance Posting Rules

| Event | Debit | Credit | sourceType |
|-------|-------|--------|------------|
| Post payroll run | Salary Expense | Salary Payable | `salary_accrual` |
| Pay salaries | Salary Payable | Cash / Bank | `salary_payment` |

Account resolution order: salary profile override -> payroll settings default -> reject if missing.

Idempotency:
- Accrual: reject if `payroll_run.posted_journal_id` is already set.
- Payment: reject if `payroll_payment.journal_id` is already set.
- Source IDs: `payroll-accrual-{companyId}-{runId}` and `payroll-payment-{companyId}-{paymentId}`.

---

## Permissions

```text
payroll.profile.read
payroll.profile.create
payroll.profile.edit
payroll.profile.deactivate
payroll.adjustment.read
payroll.adjustment.create
payroll.adjustment.approve
payroll.run.read
payroll.run.create
payroll.run.recalculate
payroll.run.approve
payroll.run.post
payroll.run.reverse
payroll.payment.read
payroll.payment.create
payroll.settings.read
payroll.settings.edit
payroll.audit.read
```

---

## Verification

Last verified:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd -q -DskipTests compile
```

```powershell
npm run build
```

Frontend build warnings currently observed:
- Duplicate `cancel` key in `src/Components/SideNavBarPro/Messages.js`.
- Existing chunk-size warnings.

---

## Remaining Work

| Task | Notes |
|------|-------|
| Salary advance / employee loans | Future payroll liability workflow |
| Leave integration | Connect approved leave to payroll attendance calculations |
| Bank transfer file export | Export payroll payment batches |
| Reversal journal entries | Complete accounting reversal workflow after `REVERSED` status |
| Payroll reports | Monthly summaries and department/branch breakdowns |
