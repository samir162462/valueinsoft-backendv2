# VALUEINSOFT PLATFORM ADMIN BACKEND IMPLEMENTATION SPEC

Purpose:
- define the backend-only implementation plan for a Platform Operations Console
- stay aligned with the current real codebase
- reuse existing services and repositories where practical
- keep backend authoritative for access, lifecycle, and audit behavior

Interpretation rules:
- `Existing` means verified in code
- `Inferred` means derived from concrete code and documentation
- `Proposed` means recommended new implementation

This document is a companion to:
- `VALUEINSOFT_PLATFORM_ADMIN_DISCOVERY_AND_DELIVERY_PLAN.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_1_DATABASE_FOUNDATION.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_2_READ_APIS.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_3_LIFECYCLE_CONTROL.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_4_AUDIT_AND_SUPPORT_APIS.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_5_CONFIGURATION_AND_COMPANY360_DETAILS.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_6_BILLING_AND_DOMAIN_DETAILS.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_7_FINANCE_AND_SUBSCRIPTION_ANALYTICS.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_8_REVENUE_TREND_AND_DAILY_METRICS.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_9_SCHEDULED_METRICS_AND_OVERVIEW_WIDGETS.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_10_OVERVIEW_ALERTING_AND_METRICS_OBSERVABILITY.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_11_OVERVIEW_RECENT_ACTIONS_AND_ANOMALY_ALERTS.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_12_ALERT_ACKNOWLEDGMENT_AND_THRESHOLD_CONFIG.md`
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_13_SCOPED_ALERT_ACKNOWLEDGMENTS_AND_NOTIFICATION_HOOKS.md`

---

## 1. Scope And Positioning

### Existing

- Tenant administration already exists through:
  - `ConfigurationAdministrationController`
  - `ConfigurationAdministrationService`
  - effective configuration resolution
- Platform administration does not yet exist as a proper backend module.
- `WebAdminController` exists but has no real implementation.

### Proposed

- Build a separate backend module for platform operations.
- Do not extend tenant admin endpoints into platform admin endpoints.
- Keep platform admin logically separate from:
  - tenant company control center
  - tenant effective configuration APIs
  - tenant-scoped runtime endpoints

### Primary Goals

- platform-wide monitoring
- company 360 inspection
- branch control
- subscription and revenue visibility
- configuration inspection
- auditability
- support workflows

---

## 2. Existing Backend Assets To Reuse

### Existing Services To Reuse

- `EffectiveConfigurationService`
  - reuse for platform-side configuration inspection
- `AuthorizationService`
  - reuse pattern, but not directly for platform permissions until platform capabilities are added
- `CompanyService`
  - reuse read logic carefully
- `BranchService`
  - reuse branch lookup and provisioning knowledge
- `SubscriptionService`
  - reuse billing and subscription access patterns

### Existing Repositories To Reuse

- `DbCompany`
- `DbBranch`
- `DbUsers`
- `DbTenants`
- `DbPackagePlans`
- `DbCompanyTemplates`
- `DbPlatformModules`
- `DbPlatformCapabilities`
- `DbRoleDefinitions`
- `DbRoleGrants`
- `DbTenantRoleAssignments`
- `DbTenantUserGrantOverrides`
- `DbSubscription`
- `DbConfigurationAdmin`

### Existing Utility And Cross-Cutting Layers To Reuse

- `TenantSqlIdentifiers`
- `GlobalExceptionHandler`
- JWT security configuration
- existing request validation model

### Existing Constraints

- user identity is still authenticated from `public.users`
- Spring Security principal still carries legacy role-derived identity
- many operational records are stored in dynamic company schemas and per-branch tables

---

## 3. Proposed Backend Module Structure

### Proposed Package Layout

Under `src/main/java/com/example/valueinsoftbackend/` add:

- `Controller/PlatformAdminController.java`
- `Service/PlatformAdminOverviewService.java`
- `Service/PlatformAdminCompanyService.java`
- `Service/PlatformAdminLifecycleService.java`
- `Service/PlatformAdminConfigurationInspectorService.java`
- `Service/PlatformAdminBillingService.java`
- `Service/PlatformAdminAuditService.java`
- `Service/PlatformSupportService.java`
- `DatabaseRequests/DbPlatformAdminReadModels.java`
- `DatabaseRequests/DbPlatformAdminAudit.java`
- `DatabaseRequests/DbPlatformSupportNotes.java`
- `Model/PlatformAdmin/*`
- `Model/Request/PlatformAdmin/*`
- `Model/Response/PlatformAdmin/*`

### Proposed Responsibility Split

- `PlatformAdminController`
  - transport layer only
  - validation and endpoint mapping
- `PlatformAdminOverviewService`
  - global metrics and dashboard summaries
- `PlatformAdminCompanyService`
  - company list, company 360, branch/user/client/product lookups
- `PlatformAdminLifecycleService`
  - suspend/resume company
  - lock/unlock branch
- `PlatformAdminConfigurationInspectorService`
  - effective config inspection
  - override inspection
  - assignment inspection
- `PlatformAdminBillingService`
  - subscription health
  - collection summaries
  - revenue trends
- `PlatformAdminAuditService`
  - admin audit writes and reads
- `PlatformSupportService`
  - support notes and support timelines

---

## 4. Proposed Platform Capabilities

### Proposed New Capability Keys

- `platform.admin.read`
- `platform.admin.write`
- `platform.company.read`
- `platform.company.lifecycle.write`
- `platform.branch.read`
- `platform.branch.lifecycle.write`
- `platform.configuration.read`
- `platform.configuration.write`
- `platform.billing.read`
- `platform.audit.read`
- `platform.support.read`
- `platform.support.write`

### Enforcement Rules

- all platform endpoints must require authenticated JWT
- all platform endpoints must enforce platform capabilities in backend
- tenant owner role must not automatically imply platform admin
- support read and write should be separately permissioned
- lifecycle writes must be audit-logged

### Existing To Reuse

- the capability enforcement pattern from `AuthorizationService`

### Proposed Extension

- add a platform-aware authorization helper, for example:
  - `PlatformAuthorizationService`

This service should:
- validate platform capabilities for authenticated user
- optionally support future support-admin vs billing-admin separation

---

## 5. Proposed API Surface

## 5.1 Overview

### Feature

Platform overview dashboard

### Proposed Endpoint

- `GET /api/platform-admin/overview`

### Current Implementation Status

Implemented now:
- `GET /api/platform-admin/overview`

Implemented behavior:
- live overview counts still come from shared platform tables
- latest snapshot totals are merged from `public.tenant_daily_metrics`
- overview now exposes:
  - `metricsSnapshotDate`
  - `metricsTenantsRepresented`
  - `metricsSalesAmount`
  - `metricsExpenseAmount`
  - `metricsCollectedAmount`
  - `metricsNetAmount`
  - `metricsStatus`
  - `alerts`

Implemented metrics automation:
- scheduled refresh now runs through `PlatformAdminMetricsScheduler`
- scheduler uses the same service path as manual refresh
- scheduler can be controlled by:
- `platform.admin.metrics.scheduler.enabled`
- `platform.admin.metrics.scheduler.cron`
- `platform.admin.metrics.scheduler.zone`

Implemented alerting:
- overview now derives alert cards from:
  - suspended company count
  - locked branch count
  - onboarding tenant count
  - unpaid subscription count
  - stale daily metrics snapshot
  - latest refresh failure state
  - partial metrics refresh failure count
  - negative operational net snapshot
  - snapshot coverage gap
  - high unpaid subscription ratio

Implemented alert workflow:
- operators can acknowledge overview alerts through backend APIs
- acknowledged alerts are suppressed from active overview alert output until expiry or manual clear
- acknowledgment actions are audited

Implemented threshold configuration:
- backend now resolves alert thresholds from runtime properties
- current configurable values include:
  - stale metrics snapshot threshold days
  - high unpaid subscription ratio threshold
  - default acknowledgment hours
  - recent admin actions limit

Implemented overview activity feed:
- overview now includes recent platform admin audit events
- recent actions are read from `platform_admin_audit_log`
- current overview limit is the latest 5 actions

### Auth

- `platform.admin.read`

### Reuse

- `DbCompany`
- `DbBranch`
- `DbTenants`
- `DbSubscription`

### New

- `PlatformAdminOverviewService`
- summary DTOs
- optional read-model query helper

### Proposed Response DTO

`PlatformOverviewResponse`

Fields:
- `totalCompanies`
- `activeCompanies`
- `suspendedCompanies`
- `totalBranches`
- `activeBranches`
- `lockedBranches`
- `tenantsInOnboarding`
- `unpaidSubscriptions`
- `activeSubscriptions`
- `planDistribution`
- `metricsSnapshotDate`
- `metricsTenantsRepresented`
- `metricsSalesAmount`
- `metricsExpenseAmount`
- `metricsCollectedAmount`
- `metricsNetAmount`
- `metricsStatus`
- `alerts`
- `recentAdminActions`
- `generatedAt`

---

## 5.2 Companies List

### Feature

Platform-wide companies list with filtering

### Proposed Endpoint

- `GET /api/platform-admin/companies`

### Query Params

- `search`
- `status`
- `packageId`
- `templateId`
- `page`
- `size`
- `sort`

### Auth

- `platform.company.read`

### Reuse

- `DbCompany`
- `DbTenants`
- `DbPackagePlans`
- `DbCompanyTemplates`
- `DbSubscription`

### New

- `PlatformAdminCompanyService`
- filter DTO
- paged response DTO

### Proposed Response DTO

`PlatformCompaniesPageResponse`

Fields:
- `items`
- `page`
- `size`
- `totalItems`
- `totalPages`

`PlatformCompanyListItem`

Fields:
- `tenantId`
- `companyId`
- `companyName`
- `ownerId`
- `packageId`
- `packageDisplayName`
- `templateId`
- `templateDisplayName`
- `status`
- `branchCount`
- `userCount`
- `subscriptionStatus`
- `createdAt`

---

## 5.3 Company 360

### Feature

Full company inspection surface

### Proposed Endpoints

- `GET /api/platform-admin/companies/{tenantId}`
- `GET /api/platform-admin/companies/{tenantId}/branches`
- `GET /api/platform-admin/companies/{tenantId}/users`
- `GET /api/platform-admin/companies/{tenantId}/clients`
- `GET /api/platform-admin/companies/{tenantId}/products`
- `GET /api/platform-admin/companies/{tenantId}/subscriptions`
- `GET /api/platform-admin/companies/{tenantId}/config`
- `GET /api/platform-admin/companies/{tenantId}/audit`
- `GET /api/platform-admin/companies/{tenantId}/support-notes`

### Auth

- `platform.company.read`
- `platform.configuration.read` for `/config`
- `platform.audit.read` for `/audit`
- `platform.support.read` for `/support-notes`

### Reuse

- `DbCompany`
- `DbBranch`
- `DbUsers`
- `DbClient`
- product and inventory repositories
- `EffectiveConfigurationService`
- `DbSubscription`

### New

- `PlatformAdminCompany360Service`
- company 360 summary DTOs
- cross-domain read aggregation DTOs

### Proposed Response DTOs

`PlatformCompany360Response`

Fields:
- `tenant`
- `company`
- `packagePlan`
- `companyTemplate`
- `onboardingState`
- `branchSummary`
- `userSummary`
- `clientSummary`
- `productSummary`
- `subscriptionSummary`
- `lifecycleState`
- `generatedAt`

`PlatformCompanyBranchesResponse`

Fields:
- `items`

`PlatformCompanyBranchItem`

Fields:
- `branchId`
- `branchName`
- `branchLocation`
- `branchStatus`
- `userCount`
- `lastSubscriptionStatus`
- `lastActivityAt`

`PlatformCompanyUsersResponse`

Fields:
- `items`

`PlatformCompanyUserItem`

Fields:
- `userId`
- `userName`
- `email`
- `legacyRole`
- `branchId`
- `branchName`
- `configAssignments`
- `overrideCount`
- `createdAt`

---

## 5.4 Suspend Company

### Feature

Suspend a company at platform level

### Proposed Endpoint

- `POST /api/platform-admin/companies/{tenantId}/suspend`

### Auth

- `platform.company.lifecycle.write`

### Reuse

- `DbCompany`
- `DbTenants`

### New

- `PlatformAdminLifecycleService`
- lifecycle state persistence
- audit logging

### Proposed Request DTO

`SuspendCompanyRequest`

Fields:
- `reason`
- `note`

### Proposed Response DTO

`CompanyLifecycleActionResponse`

Fields:
- `tenantId`
- `companyId`
- `previousStatus`
- `newStatus`
- `reason`
- `changedAt`
- `changedBy`

---

## 5.5 Resume Company

### Feature

Resume a suspended company

### Proposed Endpoint

- `POST /api/platform-admin/companies/{tenantId}/resume`

### Auth

- `platform.company.lifecycle.write`

### Reuse

- same lifecycle service and repositories as suspend

### Proposed Request DTO

`ResumeCompanyRequest`

Fields:
- `reason`
- `note`

### Proposed Response DTO

- same as `CompanyLifecycleActionResponse`

---

## 5.6 Lock Branch

### Feature

Lock a branch without deleting it

### Proposed Endpoint

- `POST /api/platform-admin/branches/{branchId}/lock`

### Auth

- `platform.branch.lifecycle.write`

### Reuse

- `DbBranch`

### New

- branch lifecycle status model
- audit logging

### Proposed Request DTO

`LockBranchRequest`

Fields:
- `reason`
- `note`

### Proposed Response DTO

`BranchLifecycleActionResponse`

Fields:
- `tenantId`
- `branchId`
- `previousStatus`
- `newStatus`
- `reason`
- `changedAt`
- `changedBy`

---

## 5.7 Unlock Branch

### Feature

Unlock a previously locked branch

### Proposed Endpoint

- `POST /api/platform-admin/branches/{branchId}/unlock`

### Auth

- `platform.branch.lifecycle.write`

### Reuse

- same lifecycle service and branch repository

### Proposed Request DTO

`UnlockBranchRequest`

Fields:
- `reason`
- `note`

### Proposed Response DTO

- same as `BranchLifecycleActionResponse`

---

## 5.8 Configuration Inspector

### Feature

Inspect tenant configuration and effective resolution

### Proposed Endpoints

- `GET /api/platform-admin/companies/{tenantId}/config/effective`
- `GET /api/platform-admin/companies/{tenantId}/config/modules`
- `GET /api/platform-admin/companies/{tenantId}/config/workflows`
- `GET /api/platform-admin/companies/{tenantId}/config/assignments`
- `GET /api/platform-admin/companies/{tenantId}/config/user-overrides`

### Auth

- `platform.configuration.read`

### Reuse

- `EffectiveConfigurationService`
- `DbConfigurationAdmin`
- `DbTenants`
- `DbRoleGrants`
- `DbPlatformModules`
- `DbPlatformCapabilities`

### New

- `PlatformAdminConfigurationInspectorService`
- platform-scoped read DTOs

### Proposed Response DTOs

`PlatformConfigEffectiveResponse`

Fields:
- `tenant`
- `packagePlan`
- `companyTemplate`
- `activeBranchId`
- `modules`
- `workflowFlags`
- `roleAssignments`
- `roleGrants`
- `userGrantOverrides`
- `resolvedCapabilities`

---

## 5.9 Commercial Summary

### Feature

Billing and subscription monitoring

### Proposed Endpoints

- `GET /api/platform-admin/billing/summary`
- `GET /api/platform-admin/billing/subscriptions`
- `GET /api/platform-admin/billing/revenue-trend`

### Current Implementation Status

Implemented now:
- `GET /api/platform-admin/billing/summary`
- `GET /api/platform-admin/billing/subscriptions`
- `GET /api/platform-admin/billing/revenue-trend`

Implemented response fields:
- `packageFilter`
- `activeSubscriptions`
- `unpaidSubscriptions`
- `expiredPaidSubscriptions`
- `tenantsWithUnpaidSubscriptions`
- `tenantsRepresented`
- `collectedAmount`
- `outstandingAmount`
- `packageBreakdown`
- `generatedAt`

Implemented subscriptions list behavior:
- latest known subscription row per branch
- optional filtering by `search`, `status`, `packageId`, and `tenantId`
- paged response for platform commercial tables

Implemented revenue trend behavior:
- reads from `tenant_daily_metrics`
- supports trailing-day range and optional tenant or package filters
- returns sales, expense, collected, and net daily totals

Implemented metrics foundation:
- `POST /api/platform-admin/metrics/daily/refresh`
- `GET /api/platform-admin/metrics/daily/status`
- upserts `tenant_daily_metrics`
- upserts `branch_daily_metrics`
- audits the refresh action
- exposes scheduler configuration and latest refresh state

Implemented alert workflow:
- `GET /api/platform-admin/overview/alerts/settings`
- `POST /api/platform-admin/overview/alerts/{alertKey}/acknowledge`
- `DELETE /api/platform-admin/overview/alerts/{alertKey}/acknowledgment`
- returns active alert acknowledgments and effective thresholds
- suppresses acknowledged alerts from overview output

### Auth

- `platform.billing.read`

### Reuse

- `DbSubscription`
- `DbTenants`
- `DbPackagePlans`

### New

- `PlatformAdminBillingService`
- revenue summary DTOs
- summary read-model tables

### Proposed Response DTOs

`PlatformBillingSummaryResponse`

Fields:
- `activeSubscriptions`
- `unpaidSubscriptions`
- `paidToday`
- `collectedThisPeriod`
- `revenueTrend`
- `planMix`

---

## 5.10 Audit Events

### Feature

Audit all platform operations

### Proposed Endpoints

- `GET /api/platform-admin/audit/events`
- `GET /api/platform-admin/audit/events/{eventId}`

### Auth

- `platform.audit.read`

### Reuse

- existing error contract only

### New

- `PlatformAdminAuditService`
- `DbPlatformAdminAudit`
- audit event model

### Proposed Response DTO

`PlatformAuditEventPageResponse`

Fields:
- `items`
- `page`
- `size`
- `totalItems`

`PlatformAuditEventItem`

Fields:
- `eventId`
- `actorUserName`
- `actorUserId`
- `capabilityKey`
- `actionType`
- `targetTenantId`
- `targetBranchId`
- `requestSummary`
- `result`
- `createdAt`

---

## 5.11 Support Notes

### Feature

Platform support notes per tenant

### Proposed Endpoints

- `GET /api/platform-admin/support/notes`
- `POST /api/platform-admin/support/notes`
- `GET /api/platform-admin/companies/{tenantId}/support-notes`

### Auth

- `platform.support.read`
- `platform.support.write`

### Reuse

- company and branch lookups

### New

- `PlatformSupportService`
- `DbPlatformSupportNotes`
- support note request and response DTOs

### Proposed Request DTO

`CreateSupportNoteRequest`

Fields:
- `tenantId`
- `branchId`
- `noteType`
- `subject`
- `body`
- `visibility`

### Proposed Response DTO

`SupportNoteResponse`

Fields:
- `noteId`
- `tenantId`
- `branchId`
- `noteType`
- `subject`
- `body`
- `visibility`
- `createdByUserId`
- `createdByUserName`
- `createdAt`

---

## 6. Proposed Service Contracts

## 6.1 PlatformAdminOverviewService

### Proposed Contract

```java
public interface PlatformAdminOverviewService {
    PlatformOverviewResponse getOverview(PlatformOverviewFilter filter, AuthenticatedPlatformActor actor);
}
```

Responsibilities:
- aggregate platform-wide operational metrics
- aggregate commercial snapshot metrics
- load recent platform audit events for dashboard summary

Depends on:
- `DbCompany`
- `DbBranch`
- `DbTenants`
- `DbSubscription`
- `DbPlatformAdminAudit`
- future daily metrics repositories

---

## 6.2 PlatformAdminCompanyService

### Proposed Contract

```java
public interface PlatformAdminCompanyService {
    PlatformCompaniesPageResponse getCompanies(PlatformCompaniesFilter filter, AuthenticatedPlatformActor actor);
    PlatformCompany360Response getCompany360(int tenantId, AuthenticatedPlatformActor actor);
    PlatformCompanyBranchesResponse getCompanyBranches(int tenantId, AuthenticatedPlatformActor actor);
    PlatformCompanyUsersResponse getCompanyUsers(int tenantId, AuthenticatedPlatformActor actor);
    PlatformCompanyClientsResponse getCompanyClients(int tenantId, AuthenticatedPlatformActor actor);
    PlatformCompanyProductsResponse getCompanyProducts(int tenantId, AuthenticatedPlatformActor actor);
    PlatformCompanySubscriptionsResponse getCompanySubscriptions(int tenantId, AuthenticatedPlatformActor actor);
}
```

Responsibilities:
- central company list and company 360 read model
- cross-domain aggregation
- tenant-scoped inspection from platform context

Depends on:
- `DbCompany`
- `DbBranch`
- `DbUsers`
- domain repositories for clients, products, subscriptions

---

## 6.3 PlatformAdminLifecycleService

### Proposed Contract

```java
public interface PlatformAdminLifecycleService {
    CompanyLifecycleActionResponse suspendCompany(int tenantId, SuspendCompanyRequest request, AuthenticatedPlatformActor actor);
    CompanyLifecycleActionResponse resumeCompany(int tenantId, ResumeCompanyRequest request, AuthenticatedPlatformActor actor);
    BranchLifecycleActionResponse lockBranch(int branchId, LockBranchRequest request, AuthenticatedPlatformActor actor);
    BranchLifecycleActionResponse unlockBranch(int branchId, UnlockBranchRequest request, AuthenticatedPlatformActor actor);
}
```

Responsibilities:
- lifecycle transitions
- validation of allowed state transitions
- audit logging
- event persistence

Depends on:
- `DbCompany`
- `DbBranch`
- `DbPlatformAdminAudit`
- lifecycle event repositories

---

## 6.4 PlatformAdminConfigurationInspectorService

### Proposed Contract

```java
public interface PlatformAdminConfigurationInspectorService {
    PlatformConfigEffectiveResponse getEffectiveConfiguration(int tenantId, Integer branchId, AuthenticatedPlatformActor actor);
    PlatformConfigModulesResponse getModuleState(int tenantId, AuthenticatedPlatformActor actor);
    PlatformConfigWorkflowResponse getWorkflowState(int tenantId, AuthenticatedPlatformActor actor);
    PlatformConfigAssignmentsResponse getAssignments(int tenantId, Integer branchId, AuthenticatedPlatformActor actor);
    PlatformConfigUserOverridesResponse getUserOverrides(int tenantId, Integer branchId, AuthenticatedPlatformActor actor);
}
```

Responsibilities:
- read-only inspection of config state
- platform-level diagnostic access

Depends on:
- `EffectiveConfigurationService`
- `DbConfigurationAdmin`
- configuration repositories

---

## 6.5 PlatformAdminBillingService

### Proposed Contract

```java
public interface PlatformAdminBillingService {
    PlatformBillingSummaryResponse getBillingSummary(PlatformBillingFilter filter, AuthenticatedPlatformActor actor);
    PlatformSubscriptionsPageResponse getSubscriptions(PlatformSubscriptionsFilter filter, AuthenticatedPlatformActor actor);
    RevenueTrendResponse getRevenueTrend(PlatformRevenueTrendFilter filter, AuthenticatedPlatformActor actor);
}
```

Responsibilities:
- billing summary
- subscription health
- revenue trend reporting

Depends on:
- `DbSubscription`
- `DbTenants`
- `DbPackagePlans`
- future daily metric repositories

---

## 6.6 PlatformAdminAuditService

### Proposed Contract

```java
public interface PlatformAdminAuditService {
    void logAction(PlatformAdminAuditCommand command);
    PlatformAuditEventPageResponse getAuditEvents(PlatformAuditFilter filter, AuthenticatedPlatformActor actor);
    PlatformAuditEventResponse getAuditEvent(long eventId, AuthenticatedPlatformActor actor);
}
```

Responsibilities:
- record every platform-sensitive action
- support audit filtering and detail inspection

Depends on:
- `DbPlatformAdminAudit`

---

## 6.7 PlatformSupportService

### Proposed Contract

```java
public interface PlatformSupportService {
    SupportNotesPageResponse getNotes(PlatformSupportNotesFilter filter, AuthenticatedPlatformActor actor);
    SupportNoteResponse createNote(CreateSupportNoteRequest request, AuthenticatedPlatformActor actor);
    SupportNotesPageResponse getTenantNotes(int tenantId, AuthenticatedPlatformActor actor);
}
```

Responsibilities:
- support note storage
- tenant support history
- support note filtering

Depends on:
- `DbPlatformSupportNotes`
- `DbCompany`
- `DbBranch`

---

## 7. Authorization Model For Platform Admin

### Existing

- current backend authorization is capability-based in application logic
- current authentication context is still legacy-role-rooted

### Proposed

- keep authentication flow as-is for first platform admin delivery
- add platform capability checks in backend services and controllers
- do not depend on frontend route visibility

### Proposed Implementation Approach

Add:
- `PlatformAuthorizationService`

Responsibilities:
- resolve authenticated user
- verify presence of platform capability
- centralize platform authorization failures

Suggested methods:

```java
public interface PlatformAuthorizationService {
    AuthenticatedPlatformActor requirePlatformCapability(String authenticatedName, String capabilityKey);
    AuthenticatedPlatformActor requireAnyPlatformCapability(String authenticatedName, List<String> capabilityKeys);
}
```

`AuthenticatedPlatformActor` fields:
- `userId`
- `userName`
- `legacyRole`
- `grantedPlatformCapabilities`

---

## 8. Data Model Additions

### Proposed New Tables

#### `platform_admin_audit_log`

Purpose:
- record every platform action and critical read inspection

Suggested columns:
- `event_id`
- `actor_user_id`
- `actor_user_name`
- `capability_key`
- `action_type`
- `target_tenant_id`
- `target_branch_id`
- `request_summary`
- `result_status`
- `correlation_id`
- `created_at`

#### `platform_support_notes`

Purpose:
- record support notes and operational context per tenant or branch

Suggested columns:
- `note_id`
- `tenant_id`
- `branch_id`
- `note_type`
- `subject`
- `body`
- `visibility`
- `created_by_user_id`
- `created_by_user_name`
- `created_at`
- `updated_at`

#### `tenant_daily_metrics`

Purpose:
- support platform overview and company 360 summaries

Suggested columns:
- `metric_date`
- `tenant_id`
- `branch_count`
- `user_count`
- `client_count`
- `product_count`
- `subscription_status`
- `collected_amount`
- `sales_amount`
- `expense_amount`

#### `branch_daily_metrics`

Purpose:
- support branch operations dashboards and branch activity health

Suggested columns:
- `metric_date`
- `tenant_id`
- `branch_id`
- `branch_status`
- `shift_count`
- `sales_count`
- `sales_amount`
- `inventory_adjustment_count`
- `active_users_count`

#### `tenant_lifecycle_events`

Purpose:
- company status transitions

#### `branch_lifecycle_events`

Purpose:
- branch status transitions

---

## 9. Reuse Versus New Build Matrix

### Keep And Reuse

- `EffectiveConfigurationService`
- `AuthorizationService` pattern
- `DbCompany`
- `DbBranch`
- `DbUsers`
- `DbSubscription`
- `GlobalExceptionHandler`

### Extend

- security capability dictionary
- tenant and branch repositories for richer status reads
- subscription reporting
- configuration inspection wrappers

### Replace Or Avoid Reusing Directly

- `WebAdminController`
- legacy web-admin assumptions
- tenant admin service as a platform admin service

---

## 10. Validation And Testing Plan

### Proposed Backend Tests

- controller authorization tests for every platform endpoint
- lifecycle transition tests:
  - suspend company
  - resume company
  - lock branch
  - unlock branch
- audit write tests
- billing summary aggregation tests
- configuration inspector tests
- support notes CRUD tests

### Important Existing Test Gap

- current migration inventory test only checks through `V17`
- platform-admin migrations will require migration test updates

---

## 11. Delivery Sequence

### Phase 1 - Read-Only Backend

- add platform capabilities
- add platform controller
- add overview endpoint
- add companies list endpoint
- add company 360 read endpoints
- add configuration inspector read endpoints

### Phase 2 - Lifecycle And Audit

- add lifecycle state model
- add lifecycle write endpoints
- add audit log table and service
- add support notes table and service

### Phase 3 - Commercial Analytics

- add daily metrics tables
- add billing summary and revenue trend endpoints

### Phase 4 - Hardening

- expand integration tests
- reduce legacy-role coupling where practical
- tighten public endpoint surface where safe

---

## 12. Final Backend Position

- The backend is already strong enough to support a real tenant admin control center.
- The backend is not yet strong enough to serve as a market-standard platform admin console without additional APIs, auditability, lifecycle state, and reporting read models.
- The correct backend strategy is:
  - reuse the configuration and authorization foundation
  - add a separate platform admin module
  - introduce explicit platform capabilities
  - add audit before sensitive write operations
  - add read models for platform analytics instead of querying dynamic tenant tables directly
