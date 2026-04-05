# VALUEINSOFT PLATFORM ADMIN STAGE 13 - SCOPED ALERT ACKNOWLEDGMENTS AND NOTIFICATION HOOKS

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- add per-tenant and per-branch scope to platform alert acknowledgments
- expose acknowledgment history to operators
- add a notification outbox foundation for future alert integrations

---

## Scope

This stage added:
- scoped alert acknowledgment storage
- alert acknowledgment history API
- alert notification outbox API
- optional notification outbox writes for acknowledge and clear actions

This stage added one new migration:
- `V22__platform_admin_alert_scope_and_notification_outbox.sql`

---

## Implemented Backend Files

New migration:
- `src/main/resources/db/migration/V22__platform_admin_alert_scope_and_notification_outbox.sql`

New repository:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAlertNotificationOutbox.java`

Updated repositories:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminAlertAcknowledgments.java`
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminReadModels.java`

Updated service:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminAlertService.java`

Updated controller:
- `src/main/java/com/example/valueinsoftbackend/Controller/PlatformAdminController.java`

New DTOs:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformAlertAcknowledgmentsPageResponse.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformAlertNotificationOutboxItem.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformAlertNotificationOutboxPageResponse.java`

Updated DTOs:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformAlertAcknowledgmentItem.java`
- `src/main/java/com/example/valueinsoftbackend/Model/Request/PlatformAdmin/PlatformAlertAcknowledgmentRequest.java`

Updated test:
- `src/test/java/com/example/valueinsoftbackend/Configuration/FlywayMigrationInventoryTest.java`

---

## Implemented Endpoints

### Alert Acknowledgment History

- `GET /api/platform-admin/overview/alerts/acknowledgments`

Authorization:
- `platform.admin.read`

Filters:
- `alertKey`
- `tenantId`
- `branchId`
- `activeOnly`
- `page`
- `size`

Behavior:
- returns paged acknowledgment history
- supports global, tenant-scoped, and branch-scoped acknowledgment records

### Alert Notification Outbox

- `GET /api/platform-admin/overview/alerts/notifications/outbox`

Authorization:
- `platform.admin.read`

Filters:
- `alertKey`
- `eventType`
- `status`
- `tenantId`
- `branchId`
- `page`
- `size`

Behavior:
- returns paged notification outbox rows
- gives operators visibility into queued alert-notification work

### Acknowledge Alert

- `POST /api/platform-admin/overview/alerts/{alertKey}/acknowledge`

Authorization:
- `platform.admin.write`

New request fields:
- `tenantId`
- `branchId`
- `notify`

Behavior:
- stores scope with the acknowledgment row
- validates tenant and branch scope
- optionally writes a pending outbox notification row when `notify = true`

### Clear Alert Acknowledgment

- `DELETE /api/platform-admin/overview/alerts/{alertKey}/acknowledgment`

Authorization:
- `platform.admin.write`

New query parameters:
- `tenantId`
- `branchId`
- `notify`

Behavior:
- clears the latest active acknowledgment for the exact requested scope
- optionally writes a pending outbox notification row when `notify = true`

---

## Database Changes

### `public.platform_admin_alert_acknowledgments`

New fields:
- `target_tenant_id`
- `target_branch_id`

Purpose:
- allow an acknowledgment to apply to:
  - all platform overview alerts of a given key
  - one tenant
  - one branch

Indexes added:
- `target_tenant_id`
- `target_branch_id`
- composite scope lookup on `alert_key`, tenant, branch, and acknowledgment time

### `public.platform_alert_notification_outbox`

Purpose:
- store future notification work for alert acknowledgments and clears

Core fields:
- `notification_id`
- `alert_key`
- `target_tenant_id`
- `target_branch_id`
- `event_type`
- `payload`
- `status`
- `attempt_count`
- `requested_by_user_id`
- `requested_by_user_name`
- `created_at`
- `processed_at`
- `last_error`

Current event types:
- `acknowledged`
- `cleared`

Current statuses:
- `pending`
- `processing`
- `processed`
- `failed`

Important note:
- this stage adds the outbox foundation only
- it does not yet add a notification delivery worker

---

## Scope Rules

Supported scope modes:
- global
  - `tenantId = null`
  - `branchId = null`
- tenant-scoped
  - `tenantId != null`
  - `branchId = null`
- branch-scoped
  - `branchId != null`
  - optional `tenantId`

Validation rules:
- tenant scope must reference an existing `tenants` row
- branch scope must reference an existing `Branch` row
- when both tenant and branch are supplied, the branch must belong to that tenant-backed company id

Overview suppression rule:
- `GET /api/platform-admin/overview` still suppresses only globally acknowledged alerts
- tenant-scoped and branch-scoped acknowledgments are stored and visible in history, but do not hide top-level overview alerts

This keeps the main platform overview authoritative while enabling more granular operator workflow tracking.

---

## Reuse Decisions

Reused:
- `PlatformAuthorizationService`
- `DbPlatformAdminOperations`
- `DbPlatformAdminReadModels`
- `platform_admin_audit_log`

Extended:
- `DbPlatformAdminAlertAcknowledgments`
- `PlatformAdminAlertService`

New:
- alert notification outbox repository
- acknowledgment history page response
- outbox page response

Not changed:
- overview route
- metrics refresh route
- metrics status route

---

## Validation

Compile:
- `.\mvnw.cmd -q -DskipTests compile`

Tests:
- `.\mvnw.cmd -q "-Dtest=AuthorizationServiceTest,FlywayMigrationInventoryTest" test`

Startup check:
- `.\mvnw.cmd -q spring-boot:run` with a temporary port after syncing

Status:
- passed

Observed runtime note:
- Flyway migrated schema `public` from `v21` to `v22` successfully
- the startup run then stopped because port `8081` was already in use
- this was a port-binding issue, not a migration or bean-creation failure

---

## Outputs Of This Stage

This stage delivered:
- scoped alert acknowledgment storage
- alert acknowledgment history inspection
- alert notification outbox inspection
- optional outbox event creation for acknowledge and clear workflows

This stage did not yet deliver:
- notification dispatch worker
- retry processor for outbox rows
- alert acknowledgments that suppress tenant-specific or branch-specific UI
- acknowledgment timeline comments beyond the stored note

---

## Next Stage

Recommended next:
- Stage 14 - notification delivery worker, retry handling, and alert workflow audit enrichment
- or begin frontend Platform Operations Console screens for overview alerts, acknowledgment history, and notification queue visibility
