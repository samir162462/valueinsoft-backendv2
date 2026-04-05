# VALUEINSOFT PLATFORM ADMIN STAGE 5 - CONFIGURATION AND COMPANY 360 DETAILS

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- expose deeper company 360 detail endpoints
- expose platform-side configuration inspection for one tenant
- reuse existing tenant-admin and effective-configuration contracts where practical

---

## Scope

This stage added:
- company branches detail endpoint
- company users detail endpoint
- company subscriptions detail endpoint
- configuration inspection endpoints for one tenant

This stage intentionally reused existing backend models for configuration inspection instead of inventing a second config contract.

---

## Implemented Backend Files

Service:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminConfigurationInspectorService.java`

Updated service:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminCompanyService.java`

Updated repository:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminReadModels.java`

New DTOs:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformCompanySubscriptionItem.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformConfigAssignmentsResponse.java`

Updated controller:
- `src/main/java/com/example/valueinsoftbackend/Controller/PlatformAdminController.java`

---

## Implemented Endpoints

### Company 360 Child Endpoints

Branches:
- `GET /api/platform-admin/companies/{tenantId}/branches`

Users:
- `GET /api/platform-admin/companies/{tenantId}/users`

Query params:
- `branchId` optional

Subscriptions:
- `GET /api/platform-admin/companies/{tenantId}/subscriptions`

Authorization:
- `platform.company.read`

Behavior:
- tenant existence is validated before returning data
- branch filter is validated against the requested tenant

### Configuration Inspector Endpoints

Effective tenant configuration aggregate:
- `GET /api/platform-admin/companies/{tenantId}/config/effective`

Effective modules:
- `GET /api/platform-admin/companies/{tenantId}/config/modules`

Effective workflow flags:
- `GET /api/platform-admin/companies/{tenantId}/config/workflows`

Assignments aggregate:
- `GET /api/platform-admin/companies/{tenantId}/config/assignments`

User overrides:
- `GET /api/platform-admin/companies/{tenantId}/config/user-overrides`

Query params:
- `branchId` optional for all config endpoints

Authorization:
- `platform.configuration.read`

---

## Reused Existing Contracts

Configuration aggregate reused:
- `TenantAdminPortalConfig`

Why reused:
- it already represents tenant/package/template/branches/users/roles/grants/overrides/module state/workflow state
- it avoids creating a second competing backend shape for the same configuration domain

Other reused model types:
- `ConfigurationAdminUserSummary`
- `EffectiveModuleConfig`
- `ResolvedWorkflowFlag`
- `TenantUserGrantOverrideConfig`

---

## New Response Models

### PlatformCompanySubscriptionItem

Fields:
- `subscriptionId`
- `branchId`
- `branchName`
- `startTime`
- `endTime`
- `amountToPay`
- `amountPaid`
- `orderId`
- `status`

Purpose:
- show latest subscription state per branch inside company 360

### PlatformConfigAssignmentsResponse

Fields:
- `roleDefinitions`
- `roleGrants`
- `roleAssignments`

Purpose:
- expose the assignments portion of tenant configuration inspection without requiring the full portal payload

---

## Implementation Details

### Company Detail Reads

Users endpoint:
- reuses `DbConfigurationAdmin.getUsersForTenant(...)`
- preserves the same tenant-admin user summary shape

Branches endpoint:
- reuses `DbPlatformAdminReadModels.getCompanyBranches(...)`

Subscriptions endpoint:
- uses latest subscription per branch from `public."CompanySubscription"`
- joins branch name from `public."Branch"`

### Configuration Inspection

Platform-side config inspector:
- validates `platform.configuration.read`
- validates tenant existence
- validates optional branch-to-tenant ownership
- loads tenant/package/template metadata
- reuses `EffectiveConfigurationService` for effective module and workflow resolution
- reuses `DbConfigurationAdmin` for users, assignments, and user overrides
- reuses role definitions, role grants, platform modules, and platform capabilities

Important implementation choice:
- the effective configuration call uses the tenant company owner as the user context only to resolve module and workflow state
- user-specific role assignments and overrides returned by inspector endpoints come from direct tenant-admin data sources, not from the owner-specific resolved capability list

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
- deeper company 360 read endpoints
- first platform-side configuration inspector
- reusable tenant configuration aggregate for platform operations
- latest branch subscription visibility per tenant

This stage did not yet deliver:
- configuration write endpoints at platform scope
- billing analytics APIs
- audit event detail endpoint
- support note update or delete
- products and clients detail endpoints for company 360

---

## Next Stage

Recommended next:
- Stage 6 - billing analytics and richer company 360 domain detail endpoints

