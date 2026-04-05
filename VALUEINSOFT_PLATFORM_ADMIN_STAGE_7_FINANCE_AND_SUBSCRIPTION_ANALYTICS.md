# VALUEINSOFT PLATFORM ADMIN STAGE 7 - FINANCE AND SUBSCRIPTION ANALYTICS

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- add a detail-level subscription analytics endpoint for the platform commercial workspace
- expose finance inspection endpoints inside company 360
- reuse the existing company-schema finance tables without redesigning tenant storage

---

## Scope

This stage added:
- platform billing subscriptions list endpoint
- company finance expenses endpoint
- company finance client receipts endpoint
- company finance supplier receipts endpoint

This stage remained read-only and added no new migrations.

---

## Implemented Backend Files

New repository:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminFinanceReadModels.java`

Updated repository:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminBillingReadModels.java`

New services:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminFinanceService.java`

Updated service:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminBillingService.java`

New DTOs:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformBillingSubscriptionItem.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformBillingSubscriptionsPageResponse.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformExpensesPageResponse.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformClientReceiptsPageResponse.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformSupplierReceiptsPageResponse.java`

Updated controller:
- `src/main/java/com/example/valueinsoftbackend/Controller/PlatformAdminController.java`

---

## Implemented Endpoints

### Commercial Detail Endpoint

- `GET /api/platform-admin/billing/subscriptions`

Query params:
- `search` optional
- `status` optional
- `packageId` optional
- `tenantId` optional
- `page`
- `size`

Authorization:
- `platform.billing.read`

Behavior:
- returns the latest known subscription record per branch
- joins tenant, company, branch, and package metadata
- supports filtering by package, tenant, status, and company or branch search text

### Company 360 Finance Detail Endpoints

Expenses:
- `GET /api/platform-admin/companies/{tenantId}/finance/expenses`

Client receipts:
- `GET /api/platform-admin/companies/{tenantId}/finance/client-receipts`

Supplier receipts:
- `GET /api/platform-admin/companies/{tenantId}/finance/supplier-receipts`

Query params:
- `branchId` optional
- `page`
- `size`

Authorization:
- `platform.company.read`

Behavior:
- validates tenant existence
- validates optional branch ownership against the tenant
- reads company-schema finance tables directly
- supports whole-company or one-branch inspection

---

## Reused Existing Storage And Models

Billing subscriptions reused:
- `public."CompanySubscription"`
- `public."Branch"`
- `public."Company"`
- `public.tenants`
- `public.package_plans`

Finance detail reused company-schema tables through `TenantSqlIdentifiers`:
- `c_{companyId}."Expenses"`
- `c_{companyId}."ClientReceipts"`
- `c_{companyId}."supplierReciepts"`

Existing runtime models reused:
- `Expenses`
- `ClientReceipt`
- `SupplierReceipt`

Why this matters:
- platform finance inspection now works without changing tenant data ownership
- commercial detail remains aligned with the same subscription source used by runtime billing logic

---

## New Response Models

### PlatformBillingSubscriptionItem

Fields:
- `subscriptionId`
- `tenantId`
- `companyId`
- `companyName`
- `branchId`
- `branchName`
- `packageId`
- `packageDisplayName`
- `startTime`
- `endTime`
- `amountToPay`
- `amountPaid`
- `outstandingAmount`
- `status`
- `active`

Purpose:
- represent the current latest branch subscription state with enough metadata for platform commercial tables

### PlatformBillingSubscriptionsPageResponse

Fields:
- `items`
- `page`
- `size`
- `totalItems`
- `totalPages`

Purpose:
- paged wrapper for the platform billing subscriptions view

### PlatformExpensesPageResponse

Fields:
- `items`
- `page`
- `size`
- `totalItems`
- `totalPages`

Purpose:
- paged wrapper for company 360 expense inspection

### PlatformClientReceiptsPageResponse

Fields:
- `items`
- `page`
- `size`
- `totalItems`
- `totalPages`

Purpose:
- paged wrapper for company 360 client receipt inspection

### PlatformSupplierReceiptsPageResponse

Fields:
- `items`
- `page`
- `size`
- `totalItems`
- `totalPages`

Purpose:
- paged wrapper for company 360 supplier receipt inspection

---

## Implementation Details

### Billing Subscriptions Analytics

Implementation:
- uses a `latest_subscriptions` CTE over `public."CompanySubscription"`
- keeps only the latest row per branch using `DISTINCT ON ("branchId")`
- joins tenant, company, branch, and package metadata

Filtering:
- `status` filters the latest branch subscription status
- `packageId` filters by tenant package
- `tenantId` filters one tenant directly
- `search` matches company name or branch name

Ordering:
- active subscriptions first
- then nearest end date
- then company and branch names

### Finance Inspection

Implementation choice:
- finance inspection uses a dedicated platform read-model repository instead of trying to reuse tenant runtime controller shapes

Reason:
- tenant runtime finance endpoints are specialized around branch and entity-specific workflows
- platform company 360 needs company-level inspection with optional branch filtering and pagination

Expenses:
- reads from company-schema `Expenses`
- filters by optional `branchId`
- sorts by `time` descending

Client receipts:
- reads from company-schema `ClientReceipts`
- filters by optional `branchId`
- sorts by `time` descending

Supplier receipts:
- reads from company-schema `supplierReciepts`
- filters by optional `branchId`
- sorts by `receiptTime` descending

Validation:
- page is clamped to a minimum of `1`
- size defaults to `20`
- size is capped at `200`

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
- first detail-level subscription analytics API for the platform commercial workspace
- first company 360 finance inspection APIs
- paged platform read models for expenses and receipts

This stage did not yet deliver:
- revenue trend endpoint
- daily metric aggregation jobs
- finance summary widgets
- cross-tenant operational anomaly reporting

---

## Next Stage

Recommended next:
- Stage 8 - revenue trend and daily metrics foundations
- or Stage 8 - frontend Platform Operations Console MVP against the implemented backend APIs
