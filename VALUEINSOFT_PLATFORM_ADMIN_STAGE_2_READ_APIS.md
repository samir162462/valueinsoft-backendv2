# VALUEINSOFT PLATFORM ADMIN STAGE 2 - READ APIS

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- deliver the first usable backend surface for platform admin
- provide overview, companies list, and company 360 read endpoints

---

## Scope

This stage added the first platform-admin backend module slice:
- controller
- read-model repository
- overview service
- company service
- platform authorization helper
- response DTOs

---

## Implemented Backend Files

Controller:
- `src/main/java/com/example/valueinsoftbackend/Controller/PlatformAdminController.java`

Repository:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminReadModels.java`

Services:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAuthorizationService.java`
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminOverviewService.java`
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminCompanyService.java`

DTOs:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformOverviewResponse.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformOverviewPackageSummary.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformCompaniesPageResponse.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformCompanyListItem.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformCompany360Response.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformCompanyBranchSummary.java`

---

## Implemented Endpoints

### Platform Overview

Endpoint:
- `GET /api/platform-admin/overview`

Authorization:
- `platform.admin.read`

Response:
- `PlatformOverviewResponse`

Current data sources:
- `public."Company"`
- `public.tenants`
- `public."Branch"`
- `public.branch_runtime_states`
- `public.onboarding_states`
- latest rows from `public."CompanySubscription"`
- `public.package_plans`

### Companies List

Endpoint:
- `GET /api/platform-admin/companies`

Authorization:
- `platform.company.read`

Supported filters:
- `search`
- `status`
- `packageId`
- `templateId`
- `page`
- `size`

Response:
- `PlatformCompaniesPageResponse`

### Company 360 Summary

Endpoint:
- `GET /api/platform-admin/companies/{tenantId}`

Authorization:
- `platform.company.read`

Response:
- `PlatformCompany360Response`

Current summary includes:
- tenant identity
- company identity
- package and template labels
- onboarding status
- branch counts
- user count
- unpaid subscription count
- branch summaries with runtime status

---

## Important Implementation Detail

Platform auth path:
- does not rely on tenant-context capability resolution

Reason:
- normal `AuthorizationService` depends on authenticated tenant context
- platform admin needs global scope

Actual implementation:
- `PlatformAuthorizationService` checks `global_admin` role grants directly from:
  - `platform_capabilities`
  - `role_grants`
  - authenticated user role from `public.users`

---

## Reused Existing Backend Assets

Reused:
- `DbUsers`
- `DbCompany`
- `DbBranch`
- `DbTenants`
- `DbRoleGrants`
- `DbPlatformCapabilities`

Not reused directly:
- tenant-scoped `ConfigurationAdministrationService`
- tenant-context `AuthorizationService`

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
- first real platform-admin backend endpoints
- overview metrics
- companies list
- company 360 summary
- global-admin authorization path

This stage did not yet deliver:
- lifecycle write endpoints
- audit read APIs
- support note APIs
- configuration inspector APIs
- billing analytics APIs

---

## Next Stage

Next:
- Stage 3 - lifecycle control for companies and branches

