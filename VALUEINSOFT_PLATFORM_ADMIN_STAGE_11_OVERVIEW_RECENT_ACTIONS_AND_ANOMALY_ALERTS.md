# VALUEINSOFT PLATFORM ADMIN STAGE 11 - OVERVIEW RECENT ACTIONS AND ANOMALY ALERTS

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- make the overview page more useful for real operator monitoring
- expose recent platform actions directly in the overview payload
- add threshold-style anomaly alerts from existing metrics and audit data

---

## Scope

This stage added:
- recent admin actions in overview
- anomaly-style overview alerts

This stage added no new migrations and no new endpoints.

---

## Implemented Backend Files

Updated repository:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminAudit.java`

Updated service:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminOverviewService.java`

Updated DTOs:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformOverviewResponse.java`
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminReadModels.java`

---

## Overview Payload Additions

Endpoint affected:
- `GET /api/platform-admin/overview`

New response field:
- `recentAdminActions`

Behavior:
- overview now includes the latest 5 entries from `public.platform_admin_audit_log`
- actions are ordered by:
  - `created_at` descending
  - then `event_id` descending

Returned shape:
- existing `PlatformAuditEventItem` rows are reused

This gives the frontend enough data to render:
- recent lifecycle actions
- recent metrics refreshes
- recent support note writes
- recent platform admin intervention flow

---

## New Anomaly-Style Overview Alerts

These alerts are backend-derived and appended to the existing overview alert list.

### Implemented Anomaly Alerts

- `metrics_partial_refresh`
  - shown when latest refresh audit context contains failed tenant ids

- `negative_operational_net`
  - shown when latest snapshot `salesAmount - expenseAmount` is negative

- `metrics_snapshot_coverage_gap`
  - shown when latest snapshot tenant coverage is lower than current active company count

- `high_unpaid_subscription_ratio`
  - shown when unpaid subscriptions are at least 50% of active subscriptions

### Existing Alerts Still Present

- `suspended_companies`
- `locked_branches`
- `tenants_in_onboarding`
- `unpaid_subscriptions`
- `stale_metrics_snapshot`
- `latest_metrics_refresh_failed`

### Important Rule

The alert model remains backend-owned:
- frontend should render the returned alerts
- frontend should not invent or re-derive these conditions independently

---

## Reuse Decisions

Reused:
- `DbPlatformAdminAudit`
- `PlatformAuditEventItem`
- `PlatformAdminOverviewService`
- `PlatformMetricsStatusResponse`
- existing overview counts and snapshot values

New:
- no new endpoint
- no new database object
- no new scheduler logic

Not changed:
- audit table schema
- metrics refresh endpoint
- metrics status endpoint

---

## Threshold Logic

### High Unpaid Subscription Ratio

Rule:
- only evaluated when `activeSubscriptions > 0`
- unpaid ratio threshold is `0.50`

Meaning:
- if unpaid subscriptions are half or more of active subscriptions, raise a warning alert

### Snapshot Coverage Gap

Rule:
- compare:
  - `metricsTenantsRepresented`
  - `activeCompanies`

Meaning:
- if latest metrics snapshot covers fewer tenants than current active companies, raise a warning alert

### Negative Operational Net

Rule:
- compare latest `metricsNetAmount` against zero

Meaning:
- if negative, raise a warning alert for operational follow-up

### Partial Refresh

Rule:
- inspect `failedTenantIds` parsed from latest refresh audit context

Meaning:
- if one or more tenant refreshes failed, raise a warning alert with the failure count

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
- backend recent-action feed for overview
- backend anomaly-style overview alerting
- stronger operator-facing overview payload without frontend logic duplication

This stage did not yet deliver:
- alert acknowledgment state
- per-alert persistence
- configurable alert thresholds
- recent admin action detail endpoint for drawer-style UI

---

## Next Stage

Recommended next:
- Stage 12 - alert acknowledgment workflow and configurable anomaly thresholds
- or begin the frontend Platform Operations Console MVP against the implemented overview, metrics status, company list, company 360, audit, and support APIs
