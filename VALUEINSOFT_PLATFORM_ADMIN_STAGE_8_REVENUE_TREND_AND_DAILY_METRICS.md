# VALUEINSOFT PLATFORM ADMIN STAGE 8 - REVENUE TREND AND DAILY METRICS

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- activate the shared daily metrics tables added in Stage 1
- provide a platform revenue trend endpoint that reads from those snapshots
- add a backend refresh path so platform analytics no longer depend only on live cross-tenant reads

---

## Scope

This stage added:
- platform revenue trend endpoint
- platform daily metrics refresh endpoint
- tenant and branch daily metric snapshot computation

This stage added no new migrations because the daily metric tables already exist in `V19__platform_admin_foundation.sql`.

---

## Implemented Backend Files

New repository:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminDailyMetrics.java`

New service:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminMetricsService.java`

Updated controller:
- `src/main/java/com/example/valueinsoftbackend/Controller/PlatformAdminController.java`

New DTOs:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformRevenueTrendPoint.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformRevenueTrendResponse.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformMetricsRefreshResponse.java`

---

## Implemented Endpoints

### Revenue Trend

- `GET /api/platform-admin/billing/revenue-trend`

Query params:
- `days` default `30`
- `tenantId` optional
- `packageId` optional

Authorization:
- `platform.billing.read`

Behavior:
- reads from `public.tenant_daily_metrics`
- aggregates by day
- returns sales, expense, collected, and net amounts
- supports filtering by tenant or package

### Daily Metrics Refresh

- `POST /api/platform-admin/metrics/daily/refresh`

Query params:
- `metricDate` optional, ISO date, defaults to today
- `tenantId` optional

Authorization:
- `platform.admin.write`

Behavior:
- refreshes one tenant or all tenants
- upserts `public.tenant_daily_metrics`
- upserts `public.branch_daily_metrics`
- writes a platform admin audit log entry
- returns processed tenant and branch counts plus failed tenant ids

---

## Daily Metric Snapshot Rules

### Tenant Daily Metrics

Stored in:
- `public.tenant_daily_metrics`

Refreshed fields:
- `branch_count`
- `user_count`
- `client_count`
- `product_count`
- `active_branch_count`
- `locked_branch_count`
- `unpaid_branch_subscriptions`
- `collected_amount`
- `sales_amount`
- `expense_amount`

Interpretation:
- count fields are end-of-day snapshot values
- money fields are same-day totals for the requested metric date

### Branch Daily Metrics

Stored in:
- `public.branch_daily_metrics`

Refreshed fields:
- `branch_status`
- `active_users_count`
- `client_count`
- `product_count`
- `shift_count`
- `sales_count`
- `sales_amount`
- `inventory_adjustment_count`

Interpretation:
- count fields combine current branch inventory or client state with same-day activity totals

---

## Reused Existing Runtime Sources

Shared metadata:
- `public.tenants`
- `public."Company"`
- `public."Branch"`
- `public.branch_runtime_states`
- `public."CompanySubscription"`
- `public.users`

Company-schema tables:
- `c_{companyId}."Client"`
- `c_{companyId}."Expenses"`
- `c_{companyId}."ClientReceipts"`
- `c_{companyId}."PosShiftPeriod"`

Branch tables:
- `c_{companyId}."PosProduct_{branchId}"`
- `c_{companyId}."PosOrder_{branchId}"`
- `c_{companyId}."InventoryTransactions_{branchId}"`

---

## Implementation Details

### Revenue Trend

Implementation:
- revenue trend reads from `tenant_daily_metrics`
- grouped by `metric_date`
- filtered by:
  - trailing `days`
  - optional `tenantId`
  - optional `packageId`

Returned amounts:
- `salesAmount`
- `expenseAmount`
- `collectedAmount`
- `netAmount = salesAmount - expenseAmount`

Important note:
- `netAmount` intentionally excludes `collectedAmount`
- this keeps it aligned with operational sales versus expenses rather than payment cashflow

### Daily Metrics Refresh

Implementation:
- refresh iterates tenant ids from `public.tenants`
- loads company and branch metadata from existing repositories
- computes branch snapshots first
- then computes tenant snapshot totals
- upserts both daily metric tables

Branch metrics:
- `branch_status` from `branch_runtime_states`
- `active_users_count` from distinct `salesUser` in branch orders on the metric date
- `client_count` from company client table filtered by branch
- `product_count` from branch product table
- `shift_count` from `PosShiftPeriod` shift starts on the metric date
- `sales_count` from branch order rows on the metric date
- `sales_amount` from net branch order totals on the metric date
- `inventory_adjustment_count` from branch inventory transactions on the metric date excluding `Sold` and `BounceBackInv`

Tenant metrics:
- `branch_count` from branch list size
- `user_count` from `public.users` joined to tenant branches
- `client_count` from full company client table
- `product_count` from summed branch product counts
- `active_branch_count` and `locked_branch_count` from branch runtime state
- `unpaid_branch_subscriptions` from latest subscription state per branch
- `collected_amount` from company client receipts on the metric date
- `sales_amount` from summed branch sales amounts on the metric date
- `expense_amount` from company expenses on the metric date

Audit:
- refresh writes `platform.metrics.refresh_daily` into `platform_admin_audit_log`

Failure handling:
- when refreshing all tenants, failed tenants are collected and the refresh continues
- when refreshing one explicit tenant, failures are raised directly

---

## Important Current Limitation

Subscription payments do not currently expose a dedicated payment-event timestamp in the platform read model.

Because of that:
- `collected_amount` in daily metrics is currently based on client receipts, not subscription payment events
- subscription cash collection trend is still incomplete

This is acceptable for the current foundation because:
- it activates the daily analytics tables
- it provides real operational trend data now
- it keeps the model honest about what the current backend can verify

---

## Validation

Compile:
- `.\mvnw.cmd -q -DskipTests compile`

Tests:
- `.\mvnw.cmd -q "-Dtest=AuthorizationServiceTest,FlywayMigrationInventoryTest" test`

Status:
- passed

---

## Outputs Of This Stage

This stage delivered:
- first snapshot-backed platform revenue trend API
- first refresh pipeline for `tenant_daily_metrics`
- first refresh pipeline for `branch_daily_metrics`
- audited platform admin metrics refresh

This stage did not yet deliver:
- scheduled background metrics refresh
- subscription payment-event trend
- branch activity trend endpoints
- anomaly detection or alerting

---

## Next Stage

Recommended next:
- Stage 9 - scheduled metrics refresh and platform overview widgets backed by daily metrics
- or Stage 9 - frontend Platform Operations Console MVP over the implemented backend platform-admin APIs
