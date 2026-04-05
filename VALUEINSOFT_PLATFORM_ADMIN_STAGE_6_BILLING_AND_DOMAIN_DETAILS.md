# VALUEINSOFT PLATFORM ADMIN STAGE 6 - BILLING AND DOMAIN DETAILS

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- add the first platform billing summary endpoint
- expose client and product detail reads inside company 360
- reuse existing shared subscription, client, and product repositories without changing tenant storage design

---

## Scope

This stage added:
- billing summary endpoint for platform commercial monitoring
- company clients detail endpoint
- company products detail endpoint

This stage intentionally stayed read-only and did not add new database migrations.

---

## Implemented Backend Files

New repository:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminBillingReadModels.java`

New service:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminBillingService.java`

Updated service:
- `src/main/java/com/example/valueinsoftbackend/Service/PlatformAdminCompanyService.java`

New DTOs:
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformBillingSummaryResponse.java`
- `src/main/java/com/example/valueinsoftbackend/Model/PlatformAdmin/PlatformBillingPackageSummary.java`

Updated controller:
- `src/main/java/com/example/valueinsoftbackend/Controller/PlatformAdminController.java`

---

## Implemented Endpoints

### Billing Summary

- `GET /api/platform-admin/billing/summary`

Query params:
- `packageId` optional

Authorization:
- `platform.billing.read`

Behavior:
- summarizes latest branch subscription state across the platform
- supports optional filtering by tenant package
- returns aggregate counts, amount collected, amount outstanding, and package breakdown

### Company 360 Domain Detail Endpoints

Clients:
- `GET /api/platform-admin/companies/{tenantId}/clients`

Query params:
- `branchId` optional
- `max` optional

Products:
- `GET /api/platform-admin/companies/{tenantId}/products`

Query params:
- `branchId` optional
- `max` optional

Authorization:
- `platform.company.read`

Behavior:
- validates tenant existence before returning data
- validates optional branch filter ownership against the requested tenant
- clamps `max` to a safe backend range

---

## Reused Existing Repositories

Billing summary reused shared commercial metadata:
- `public."CompanySubscription"`
- `public."Branch"`
- `public.tenants`
- `public.package_plans`

Clients detail reused:
- `DbClient.getLatestClients(...)`

Products detail reused:
- `DbPosProduct.getProductsAllRange(...)`
- `DbBranch.getBranchByCompanyId(...)`

Why this matters:
- no new billing storage was introduced for the first commercial read slice
- no product schema redesign was required
- company 360 can inspect current tenant runtime data using the existing storage model

---

## New Response Models

### PlatformBillingSummaryResponse

Fields:
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

Purpose:
- provide a single platform commercial snapshot for the initial billing dashboard

### PlatformBillingPackageSummary

Fields:
- `packageId`
- `packageDisplayName`
- `tenantCount`
- `activeSubscriptions`
- `unpaidSubscriptions`
- `collectedAmount`
- `outstandingAmount`

Purpose:
- provide package-level commercial breakdown inside the billing summary response

---

## Implementation Details

### Billing Summary Read Model

Implementation:
- uses a `latest_subscriptions` CTE with `DISTINCT ON ("branchId")`
- derives current branch subscription state from the latest known subscription row per branch
- joins branches back to tenants and package plans

Aggregate rules:
- active subscription:
  - latest status is `PD`
  - `endTime >= CURRENT_DATE`
- unpaid subscription:
  - latest status is not `PD`
- expired paid subscription:
  - latest status is `PD`
  - `endTime < CURRENT_DATE`

Commercial totals:
- `collectedAmount` is based on summed latest `amountPaid`
- `outstandingAmount` is based on positive remainder of `amountToPay - amountPaid`

### Company Clients Detail

Implementation:
- validates `platform.company.read`
- validates tenant existence
- validates optional branch ownership
- returns latest clients through `DbClient.getLatestClients(...)`

Default and limits:
- default `max` is `200`
- backend clamps `max` to `1..1000`

### Company Products Detail

Important storage constraint:
- products live in per-branch dynamic tables, not in one company table

Implementation:
- validates `platform.company.read`
- validates tenant existence
- validates optional branch ownership
- if `branchId` is supplied:
  - reads only that branch product table
- if `branchId` is omitted:
  - loads all company branches
  - reads each branch product table
  - merges results in memory
  - sorts by `buyingDay` descending, then `productId` descending
  - applies the final `max` cap after merge

Safe default filter:
- uses `new ProductFilter(false, false, false, false, 0, 100000, null, null)`

Reason:
- default constructor leaves `rangeMax = 0`
- that would incorrectly collapse product reads to zero-price items only

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
- first platform billing summary API
- client detail reads for company 360
- product detail reads for company 360
- package-level subscription breakdown for commercial dashboards

This stage did not yet deliver:
- billing trend series
- paged subscription list endpoint
- finance detail endpoints for company 360
- tenant or branch daily metric population jobs

---

## Next Stage

Recommended next:
- Stage 7 - finance and subscription analytics detail endpoints
- or Stage 7 - frontend platform operations console MVP against the implemented backend APIs
