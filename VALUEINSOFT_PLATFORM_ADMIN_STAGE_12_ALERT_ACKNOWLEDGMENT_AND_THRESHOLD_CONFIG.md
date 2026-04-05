# VALUEINSOFT PLATFORM ADMIN STAGE 12 - ALERT ACKNOWLEDGMENT AND THRESHOLD CONFIG

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- let platform operators acknowledge overview alerts for a limited time
- suppress acknowledged alerts from the active overview payload
- move overview alert thresholds to explicit backend runtime configuration

---

## Scope

This stage added:
- alert acknowledgment persistence
- alert acknowledgment APIs
- overview suppression for acknowledged alerts
- configurable alert thresholds from runtime properties

This stage added one new migration:
- `V21__platform_admin_alert_acknowledgment_foundation.sql`

---

## Implemented Backend Files

New migration:
- `src/main/resources/db/migration/V21__platform_admin_alert_acknowledgment_foundation.sql`

New repository:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminAlertAcknowledgments.java`

New service:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminAlertService.java`

Updated services:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminOverviewService.java`
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminMetricsService.java`

Updated controller:
- `src/main/java/com/example/valueinsoftbackend/Controller/PlatformAdminController.java`

New DTOs:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformAlertAcknowledgmentItem.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformAlertSettingsResponse.java`
- `src/main/java/com/example/valueinsoftbackend/Model/Request/PlatformAdmin/PlatformAlertAcknowledgmentRequest.java`

Updated test:
- `src/test/java/com/example/valueinsoftbackend/Configuration/FlywayMigrationInventoryTest.java`

---

## Implemented Endpoints

### Alert Settings

- `GET /api/platform-admin/overview/alerts/settings`

Authorization:
- `platform.admin.read`

Behavior:
- returns effective alert thresholds from runtime properties
- returns active alert acknowledgments
- returns recent admin actions limit used by overview

### Acknowledge Alert

- `POST /api/platform-admin/overview/alerts/{alertKey}/acknowledge`

Authorization:
- `platform.admin.write`

Behavior:
- creates a persistent alert acknowledgment row
- uses request `expiresAt` when provided
- otherwise uses configured default acknowledgment hours
- audits the action into `platform_admin_audit_log`

### Clear Alert Acknowledgment

- `DELETE /api/platform-admin/overview/alerts/{alertKey}/acknowledgment`

Authorization:
- `platform.admin.write`

Behavior:
- clears the latest active acknowledgment for the given alert key
- audits the clear action into `platform_admin_audit_log`

---

## New Database Object

### `public.platform_admin_alert_acknowledgments`

Purpose:
- store operator acknowledgments for overview alerts

Core fields:
- `acknowledgment_id`
- `alert_key`
- `note`
- `acknowledged_by_user_id`
- `acknowledged_by_user_name`
- `acknowledged_at`
- `expires_at`
- `cleared_at`
- `cleared_by_user_id`
- `cleared_by_user_name`
- `source`
- `created_at`

Behavior:
- an acknowledgment is active when:
  - `cleared_at IS NULL`
  - and `expires_at IS NULL OR expires_at > NOW()`

Indexes added:
- `alert_key`
- latest active lookup ordering
- `acknowledged_at`

---

## Runtime Threshold Configuration

The backend now reads these runtime properties:

- `platform.admin.alerts.stale-metrics-after-days`
  - default `1`

- `platform.admin.alerts.high-unpaid-subscription-ratio`
  - default `0.50`

- `platform.admin.alerts.ack.default-hours`
  - default `12`

- `platform.admin.overview.recent-actions.limit`
  - default `5`

These properties now influence:
- stale snapshot detection
- high unpaid subscription anomaly alerting
- default alert acknowledgment expiry
- recent admin actions count in overview

---

## Overview Behavior Change

Endpoint affected:
- `GET /api/platform-admin/overview`

New rule:
- alerts that have an active acknowledgment are filtered out from the active `alerts` array

This makes the overview behave like a real operator console:
- persistent alert conditions still exist in data
- but acknowledged noise is suppressed until expiry or explicit clear

Recent admin actions still remain visible in overview even when related alerts are acknowledged.

---

## Acknowledgeable Alert Keys

Implemented keys:
- `suspended_companies`
- `locked_branches`
- `tenants_in_onboarding`
- `unpaid_subscriptions`
- `stale_metrics_snapshot`
- `latest_metrics_refresh_failed`
- `metrics_partial_refresh`
- `negative_operational_net`
- `metrics_snapshot_coverage_gap`
- `high_unpaid_subscription_ratio`

Invalid keys are rejected by backend validation.

---

## Reuse Decisions

Reused:
- `PlatformAuthorizationService`
- `DbPlatformAdminOperations`
- `DbPlatformAdminAudit`
- `PlatformAdminOverviewService`
- `PlatformAdminMetricsService`
- `platform_admin_audit_log`

New:
- alert acknowledgment storage and service

Not changed:
- overview endpoint route
- metrics refresh endpoint
- metrics status endpoint

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
- persistent overview alert acknowledgment
- backend-owned threshold configuration
- suppression of acknowledged alerts from overview
- audited acknowledgment and clear workflow

This stage did not yet deliver:
- per-tenant or per-branch alert scope
- configurable thresholds stored in database
- alert acknowledgment comments/history timeline
- alert notification delivery

---

## Next Stage

Recommended next:
- Stage 13 - alert scope expansion, acknowledgment history, and notification hooks
- or start the frontend Platform Operations Console MVP against the implemented backend platform-admin APIs
