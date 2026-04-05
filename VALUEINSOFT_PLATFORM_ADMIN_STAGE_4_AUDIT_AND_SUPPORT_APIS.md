# VALUEINSOFT PLATFORM ADMIN STAGE 4 - AUDIT AND SUPPORT APIS

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- expose platform-admin audit history through backend APIs
- expose platform support notes through backend APIs
- keep support operations and audit inspection inside the platform capability model

---

## Scope

This stage added:
- paged audit-event read endpoints
- paged support-note read endpoints
- support-note creation endpoint
- support-note audit logging

This stage reused the shared tables introduced in Stage 1:
- `public.platform_admin_audit_log`
- `public.platform_support_notes`

---

## Implemented Backend Files

Repository layer:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminAudit.java`
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformSupportNotes.java`

Service layer:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminAuditService.java`
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformSupportService.java`

Request DTO:
- `src/main/java/com/example/valueinsoftbackend/Model/Request/PlatformAdmin/CreatePlatformSupportNoteRequest.java`

Response DTOs:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformAuditEventItem.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformAuditEventsPageResponse.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformSupportNoteItem.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformSupportNotesPageResponse.java`

Controller updated:
- `src/main/java/com/example/valueinsoftbackend/Controller/PlatformAdminController.java`

---

## Implemented Endpoints

### Audit Events

Endpoint:
- `GET /api/platform-admin/audit/events`

Authorization:
- `platform.audit.read`

Supported filters:
- `targetTenantId`
- `targetBranchId`
- `actorUserName`
- `actionType`
- `resultStatus`
- `page`
- `size`

Response:
- `PlatformAuditEventsPageResponse`

### Company-Scoped Audit View

Endpoint:
- `GET /api/platform-admin/companies/{tenantId}/audit/events`

Authorization:
- `platform.audit.read`

Purpose:
- same audit view narrowed to one tenant

### Support Notes List

Endpoint:
- `GET /api/platform-admin/support/notes`

Authorization:
- `platform.support.read`

Supported filters:
- `tenantId`
- `branchId`
- `noteType`
- `visibility`
- `page`
- `size`

Response:
- `PlatformSupportNotesPageResponse`

### Company-Scoped Support Notes

Endpoint:
- `GET /api/platform-admin/companies/{tenantId}/support/notes`

Authorization:
- `platform.support.read`

Purpose:
- tenant-scoped support history

### Create Support Note

Endpoint:
- `POST /api/platform-admin/support/notes`

Authorization:
- `platform.support.write`

Request:
- `CreatePlatformSupportNoteRequest`

Response:
- `PlatformSupportNoteItem`

---

## Request And Response Contracts

### CreatePlatformSupportNoteRequest

Fields:
- `tenantId`
- `branchId`
- `noteType`
- `subject`
- `body`
- `visibility`

Validation:
- `tenantId` is required and positive
- `branchId` is optional and positive when present
- `noteType`, `subject`, `body`, and `visibility` are required
- field lengths are validated before service execution

### PlatformAuditEventsPageResponse

Fields:
- `items`
- `page`
- `size`
- `totalItems`
- `totalPages`

### PlatformAuditEventItem

Fields:
- `eventId`
- `actorUserId`
- `actorUserName`
- `capabilityKey`
- `actionType`
- `targetTenantId`
- `targetBranchId`
- `requestSummaryJson`
- `contextSummaryJson`
- `resultStatus`
- `correlationId`
- `createdAt`

### PlatformSupportNotesPageResponse

Fields:
- `items`
- `page`
- `size`
- `totalItems`
- `totalPages`

### PlatformSupportNoteItem

Fields:
- `noteId`
- `tenantId`
- `companyName`
- `branchId`
- `branchName`
- `noteType`
- `subject`
- `body`
- `visibility`
- `createdByUserId`
- `createdByUserName`
- `createdAt`
- `updatedAt`

---

## Service Rules Implemented

Audit service behavior:
- enforces `platform.audit.read`
- returns paged filtered audit data
- does not depend on tenant-context capability resolution

Support service behavior:
- enforces `platform.support.read` for reads
- enforces `platform.support.write` for note creation
- validates tenant existence before create
- validates branch-to-tenant ownership when `branchId` is present
- validates allowed note types:
  - `support`
  - `billing`
  - `risk`
  - `ops`
  - `follow_up`
- validates allowed visibility values:
  - `internal`
  - `restricted`

Audit behavior for support-note creation:
- successful note creation writes an audit event to `public.platform_admin_audit_log`

---

## Reused Existing Backend Assets

Reused:
- `PlatformAuthorizationService`
- `DbTenants`
- `DbCompany`
- `DbBranch`
- `DbPlatformAdminOperations` for audit write behavior

New specialized read repositories:
- `DbPlatformAdminAudit`
- `DbPlatformSupportNotes`

Reason:
- audit and support are platform concerns
- they should stay separate from tenant-admin repositories and tenant runtime services

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
- first audit read API
- first support operations API
- paged audit inspection
- paged support-note inspection
- support-note write path
- support-note audit logging

This stage did not yet deliver:
- audit event detail endpoint
- support note update or delete
- configuration inspector APIs
- billing analytics APIs
- company 360 child endpoints for users, subscriptions, and config detail

---

## Next Stage

Recommended next:
- Stage 5 - configuration inspector and company 360 child endpoints

