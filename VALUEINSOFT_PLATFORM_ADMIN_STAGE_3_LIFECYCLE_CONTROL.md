# VALUEINSOFT PLATFORM ADMIN STAGE 3 - LIFECYCLE CONTROL

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- add safe operational control actions for platform administrators
- persist lifecycle history and audit records for each action

---

## Scope

This stage added:
- company suspend
- company resume
- branch lock
- branch unlock
- lifecycle event persistence
- audit logging for lifecycle actions

---

## Implemented Backend Files

Repository:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminOperations.java`

Service:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminLifecycleService.java`

Request DTO:
- `src/main/java/com/example/valueinsoftbackend/Model/Request/PlatformAdmin/PlatformLifecycleActionRequest.java`

Response DTO:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformLifecycleActionResponse.java`

Controller updated:
- `src/main/java/com/example/valueinsoftbackend/Controller/PlatformAdminController.java`

---

## Implemented Endpoints

### Suspend Company

Endpoint:
- `POST /api/platform-admin/companies/{tenantId}/suspend`

Authorization:
- `platform.company.lifecycle.write`

Persistence:
- updates `public.tenants.status`
- inserts into `public.tenant_lifecycle_events`
- inserts into `public.platform_admin_audit_log`

### Resume Company

Endpoint:
- `POST /api/platform-admin/companies/{tenantId}/resume`

Authorization:
- `platform.company.lifecycle.write`

Persistence:
- updates `public.tenants.status`
- inserts into `public.tenant_lifecycle_events`
- inserts into `public.platform_admin_audit_log`

### Lock Branch

Endpoint:
- `POST /api/platform-admin/branches/{branchId}/lock`

Authorization:
- `platform.branch.lifecycle.write`

Persistence:
- upserts `public.branch_runtime_states`
- inserts into `public.branch_lifecycle_events`
- inserts into `public.platform_admin_audit_log`

### Unlock Branch

Endpoint:
- `POST /api/platform-admin/branches/{branchId}/unlock`

Authorization:
- `platform.branch.lifecycle.write`

Persistence:
- upserts `public.branch_runtime_states`
- inserts into `public.branch_lifecycle_events`
- inserts into `public.platform_admin_audit_log`

---

## Request Contract

Request DTO:
- `PlatformLifecycleActionRequest`

Fields:
- `reason`
- `note`

Validation:
- `reason` is required
- `note` is optional

Response DTO:
- `PlatformLifecycleActionResponse`

Fields:
- `targetType`
- `tenantId`
- `branchId`
- `action`
- `previousStatus`
- `newStatus`
- `changed`
- `reason`
- `note`
- `actorUserName`
- `processedAt`

---

## Transition Rules Implemented

Company rules:
- archived tenant cannot be suspended
- archived tenant cannot be resumed
- resume is only valid from `suspended` or already `active`

Branch rules:
- archived branch cannot be locked
- archived branch cannot be unlocked
- unlock is only valid from `locked` or already `active`

Behavior:
- repeated action on same target does not reapply state change
- repeated action still returns a valid response with `changed = false`
- audit is still recorded for operational traceability

---

## Reused Existing Backend Assets

Reused:
- `DbTenants`
- `DbCompany`
- `DbBranch`
- `PlatformAuthorizationService`

New write repository added:
- `DbPlatformAdminOperations`

Reason:
- lifecycle state, lifecycle events, and platform audit are platform concerns
- they should not be pushed into tenant admin repositories

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
- first platform-admin write actions
- company lifecycle control
- branch runtime control
- lifecycle event persistence
- audit logging for lifecycle operations

This stage did not yet deliver:
- audit read endpoints
- support notes endpoints
- configuration inspector endpoints
- billing analytics endpoints

---

## Next Stage

Recommended next:
- Stage 4 - audit and support APIs

