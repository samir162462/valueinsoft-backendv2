# ValueINSoft Branch Dashboard Aggregator Specification

## Overview
The Dashboard Aggregator is a high-performance backend module designed to unify fragmented data fetching for the Branch Owner Dashboard. It replaces multiple frontend requests with a single, secure, and concurrent endpoint.

## 1. API Architecture
- **Endpoint**: `POST /api/dashboard/branch-summary`
- **Controller**: `DashboardSummaryController`
- **Service**: `DashboardSummaryService`

### Request Schema (`DashboardSummaryRequest`)
| Field | Type | Description |
| :--- | :--- | :--- |
| `branchId` | Integer | **Required**. Target branch ID. |
| `date` | String | **Required**. Target date (ISO YYYY-MM-DD). |
| `period` | String | Optional. Default: `TODAY`. Range selector for trend data. |

> [!IMPORTANT]  
> `companyId` is **never** passed in the request body. It is derived server-side from the user's authenticated Principal to ensure strict tenant isolation.

### Response Schema (`DashboardSummaryResponse`)
The response uses a **Section-Level Resilience** model. If one provider fails, the rest of the dashboard still loads.

| Block | Type | Status Tracking |
| :--- | :--- | :--- |
| `context` | Object | Metadata (Branch Name, Date, Currency, etc.) |
| `kpis` | Object | Key Metrics (Sales, Profit, Order Count) |
| `alerts` | Array | Operational notifications (Unposted sales, etc.) |
| `charts` | Object | Trend data (Sales/Profit trend lines) |
| `topPerformers` | Object | Best products, staff, and customers |
| `warnings` | Array | Data completeness notes (e.g., "Profit excludes items without cost") |
| `sectionStatus`| Map | Maps section keys to `success`, `error`, or `not_implemented` |

## 2. Security & Tenant Isolation
1. **Principal Extraction**: The controller extracts the base username from the Spring Security Principal.
2. **Tenant Resolution**: Uses `DbUsers` and `DbCompany` to resolve the authoritative `companyId` for the user.
3. **Capability Check**: Asserts `pos.dashboard.read` capability for the resolved `companyId` and the requested `branchId`.
4. **Ownership Validation**: The service validates that the `branchId` actually belongs to the resolved `companyId`.

## 3. Performance & Concurrency
The aggregator uses a **Provider Pattern** with asynchronous execution.

- **Executor Pool**: A dedicated bounded thread pool (`dashboardExecutor`) with 10 threads to prevent Tomcat thread starvation.
- **Async Execution**: Providers run in parallel using `CompletableFuture.supplyAsync()`.
- **Failsafe Timeouts**: 
  - KPIs: 3 seconds
  - Charts/Analytics: 5 seconds
- **Resilience**: If a provider times out or throws an exception, the `sectionStatus` is set to `error` and the payload remains structural (null/empty) to prevent frontend crashes.

## 4. Caching Strategy
Leverages Spring Cache (`@Cacheable`) with a tenant-safe composite key.

- **Key Pattern**: `#companyId + '_' + #branchId + '_' + #date`
- **TTL Recommendations**:
  - KPIs: 30–60 seconds
  - Trends/Charts: 5–15 minutes
  - Alerts: 1 minute

## 5. Implementation Status
- [x] Phase 1: Aggregator Foundation & Security
- [x] Phase 2: Frontend TanStack Query Integration
- [x] Phase 3: Concurrent Providers (KPIs, Charts)
- [x] Phase 4: Cache Integration
- [ ] Phase 5: Finance & Inventory Providers (PENDING)
