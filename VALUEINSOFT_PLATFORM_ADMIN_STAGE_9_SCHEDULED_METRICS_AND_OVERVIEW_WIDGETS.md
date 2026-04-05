# VALUEINSOFT PLATFORM ADMIN STAGE 9 - SCHEDULED METRICS AND OVERVIEW WIDGETS

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- move the daily metrics system from manual-only refresh into scheduled background operation
- make the platform overview endpoint consume the latest daily metrics snapshot
- reduce reliance on live cross-tenant aggregation for dashboard money totals

---

## Scope

This stage added:
- scheduled daily metrics refresh
- snapshot-backed overview metric fields
- shared refresh logic for manual and scheduled execution

This stage added no new migrations and no new public endpoints.

---

## Implemented Backend Files

New service:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminMetricsScheduler.java`

Updated services:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminMetricsService.java`
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminOverviewService.java`

Updated repositories:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminDailyMetrics.java`
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminReadModels.java`

New DTO:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformOverviewMetricsSnapshot.java`

Updated DTO:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformOverviewResponse.java`

---

## Implemented Behavior

### Scheduled Daily Metrics Refresh

Implementation:
- a new scheduler bean runs the same daily metrics refresh pipeline used by the manual endpoint
- the scheduler defaults to:
  - cron `0 30 2 * * *`
  - zone `Africa/Cairo`
- the scheduler can be controlled with properties:
  - `platform.admin.metrics.scheduler.enabled`
  - `platform.admin.metrics.scheduler.cron`
  - `platform.admin.metrics.scheduler.zone`

Execution model:
- manual refresh still requires `platform.admin.write`
- scheduled refresh runs as system automation
- both paths write through the same internal refresh method

Audit behavior:
- manual refresh writes `platform.metrics.refresh_daily`
- scheduled refresh writes `platform.metrics.refresh_daily.scheduled`
- scheduled refresh audit actor is recorded as `system`

### Platform Overview Snapshot Enrichment

Endpoint affected:
- `GET /api/platform-admin/overview`

Existing overview counts still come from shared platform tables:
- total companies
- active companies
- suspended companies
- total branches
- active branches
- locked branches
- tenants in onboarding
- unpaid subscriptions
- active subscriptions
- plan distribution

New overview fields now come from the latest `tenant_daily_metrics` snapshot date:
- `metricsSnapshotDate`
- `metricsTenantsRepresented`
- `metricsSalesAmount`
- `metricsExpenseAmount`
- `metricsCollectedAmount`
- `metricsNetAmount`

Fallback behavior:
- if no daily metrics snapshot exists yet, the overview returns zero-value snapshot fields

---

## Read Model Logic

### Latest Overview Snapshot

Source:
- `public.tenant_daily_metrics`

Query behavior:
- load the maximum `metric_date`
- aggregate all tenant rows for that snapshot date
- sum:
  - `sales_amount`
  - `expense_amount`
  - `collected_amount`
  - `sales_amount - expense_amount` as `net_amount`
- count represented tenants

### Why This Matters

Before this stage:
- the overview endpoint was live-table-count based only
- money totals were available through revenue trend, but not surfaced in overview
- daily metrics refresh required manual invocation

After this stage:
- overview can show operational money totals from the latest snapshot
- dashboard totals are closer to production admin expectations
- metrics can refresh automatically without operator action

---

## Reuse Decisions

Reused:
- `PlatformAdminMetricsService`
- `DbPlatformAdminDailyMetrics`
- `DbPlatformAdminReadModels`
- `PlatformAdminOverviewService`
- `platform_admin_audit_log`
- `tenant_daily_metrics`
- existing Spring scheduling support already enabled in the backend

New:
- `PlatformAdminMetricsScheduler`
- `PlatformOverviewMetricsSnapshot`
- system-schedule refresh path inside metrics service

Not changed:
- controller contract
- metrics refresh endpoint shape
- daily metrics table schema

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
- automatic scheduled daily metrics refresh
- overview widget data backed by snapshot tables
- shared internal refresh flow for manual and automated refresh
- audit visibility for scheduled metrics work

This stage did not yet deliver:
- alerting on refresh failures
- recent admin action widgets on overview
- anomaly detection on revenue or branch health
- externally triggered scheduler management endpoints

---

## Next Stage

Recommended next:
- Stage 10 - overview alerting, operational anomaly widgets, and scheduler observability
- or frontend Platform Operations Console MVP over the implemented platform-admin backend
