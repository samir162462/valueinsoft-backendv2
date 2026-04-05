# VALUEINSOFT PLATFORM ADMIN STAGE 10 - OVERVIEW ALERTING AND METRICS OBSERVABILITY

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- make the platform overview endpoint operationally useful instead of count-only
- expose backend observability for the scheduled daily metrics pipeline
- turn the existing audit log into a concrete platform monitoring signal

---

## Scope

This stage added:
- overview alert items
- daily metrics scheduler status endpoint
- latest refresh observability backed by `platform_admin_audit_log`

This stage added no new migrations.

---

## Implemented Backend Files

Updated repositories:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminAudit.java`
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminReadModels.java`

Updated services:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminMetricsService.java`
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminOverviewService.java`

Updated controller:
- `src/main/java/com/example/valueinsoftbackend/Controller/PlatformAdminController.java`

New DTOs:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformOverviewAlertItem.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformMetricsStatusResponse.java`

Updated DTO:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformOverviewResponse.java`

---

## Implemented Endpoint

### Daily Metrics Status

- `GET /api/platform-admin/metrics/daily/status`

Authorization:
- `platform.admin.read`

Behavior:
- returns whether the scheduler is enabled
- returns the effective scheduler cron
- returns the effective scheduler zone
- returns the latest refresh audit event
- returns the latest successful refresh timestamp
- returns the latest snapshot date and tenant coverage
- returns stale evaluation for the latest snapshot
- returns processed tenant and branch counts from the latest refresh audit context
- returns failed tenant ids from the latest refresh audit context

Source model:
- scheduler config from Spring environment properties
- latest refresh state from `public.platform_admin_audit_log`
- latest snapshot state from `public.tenant_daily_metrics`

---

## Overview Response Additions

Endpoint affected:
- `GET /api/platform-admin/overview`

New response fields:
- `metricsStatus`
- `alerts`

### Metrics Status In Overview

The overview now includes the same scheduler and refresh status model used by:
- `GET /api/platform-admin/metrics/daily/status`

This lets the frontend render:
- last refresh result
- last successful refresh time
- stale snapshot state
- scheduler visibility

### Overview Alerts

The backend now derives alert items directly from live overview counts and metrics status.

Implemented alerts:
- `suspended_companies`
- `locked_branches`
- `tenants_in_onboarding`
- `unpaid_subscriptions`
- `stale_metrics_snapshot`
- `latest_metrics_refresh_failed`

Alert severity values used:
- `info`
- `warning`
- `critical`

Important rule:
- alerts are backend-derived
- frontend should render them, not invent them

---

## Observability Logic

### Latest Metrics Refresh Event

Source:
- `public.platform_admin_audit_log`

Action types tracked:
- `platform.metrics.refresh_daily`
- `platform.metrics.refresh_daily.scheduled`

Read behavior:
- most recent refresh event of either type
- most recent successful refresh event of either type

Parsed audit context:
- `processedTenants`
- `processedBranches`
- `failedTenantIds`

### Snapshot Freshness

Source:
- `public.tenant_daily_metrics`

Logic:
- load latest snapshot date
- compute `snapshotLagDays`
- mark `stale = true` when:
  - no snapshot exists
  - or latest snapshot is older than one day

This avoids false alarms for a same-day refresh that has not yet run while still surfacing genuinely stale analytics.

---

## Reuse Decisions

Reused:
- `PlatformAdminMetricsScheduler`
- `PlatformAdminMetricsService`
- `PlatformAdminOverviewService`
- `DbPlatformAdminAudit`
- `DbPlatformAdminDailyMetrics`
- `platform_admin_audit_log`
- `tenant_daily_metrics`

New:
- `PlatformOverviewAlertItem`
- `PlatformMetricsStatusResponse`
- latest metrics refresh audit lookup helpers

Not changed:
- migration inventory
- daily metrics table schema
- metrics refresh endpoint contract

---

## Validation

Compile:
- `.\mvnw.cmd -q -DskipTests compile`

Tests:
- `.\mvnw.cmd -q "-Dtest=AuthorizationServiceTest,FlywayMigrationInventoryTest" test`

Status:
- passed

Runtime verification:
- application startup reached Flyway current version `20`
- previous `V19` migration failure is no longer present
- final startup check was blocked only by port `8081` already being in use

---

## Outputs Of This Stage

This stage delivered:
- backend-owned overview alerting
- backend-owned metrics refresh status API
- audit-backed scheduler observability
- stale snapshot detection for platform dashboard use

This stage did not yet deliver:
- anomaly thresholds for finance or sales trend outliers
- alert acknowledgment workflow
- recent admin action cards on overview
- scheduler failure escalation or notification hooks

---

## Next Stage

Recommended next:
- Stage 11 - recent admin actions on overview, alert acknowledgment, and anomaly thresholding
- or start the frontend Platform Operations Console MVP against the implemented backend platform-admin endpoints
