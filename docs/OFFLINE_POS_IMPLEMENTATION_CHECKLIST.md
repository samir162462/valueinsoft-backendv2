# Offline POS Implementation Checklist

## Current Phase Status

Phase 10H - Offline Cart Draft and Local Order Queue: completed on 2026-05-05.
Phase 10I - Manual Sync Upload from Local Queue: completed on 2026-05-05.
Phase 10J - Backend Status Polling and Local Queue Result Mapping: completed on 2026-05-05.
Phase 10K - Cashier Per-Order Sync Result Endpoint: completed on 2026-05-05.
Phase 10L - Persist Offline Order Reference Columns: completed on 2026-05-05.
Phase 10M - Offline Device Registration UX: completed on 2026-05-05.

## Tenant Isolation Decision

Final decision: Option B - move offline sync tables fully into each tenant schema (`c_{companyId}`).

Reason:
- The main application already stores tenant runtime data in tenant schemas via `TenantSqlIdentifiers.companySchema(companyId)`.
- Existing POS order, order detail, branch product, inventory, shift, attendance, payroll, and finance runtime tables are tenant-scoped.
- Offline sync payloads include tenant-sensitive order JSON, idempotency keys, device sessions, sync errors, and audit events. Keeping these in `public` increases blast radius for query mistakes and tenant delete/export/backup work.
- Existing Flyway migrations already use idempotent `DO $$` loops over `information_schema.schemata WHERE schema_name LIKE 'c_%'`, so creating tenant offline tables is practical and consistent with the repo.
- No serious blocker was found for tenant-scoped device registration. Devices are branch/company-specific, and the current uniqueness model is already `(company_id, branch_id, device_code)`, not globally unique.

Global/public data:
- Do not keep any current offline sync table in `public` for normal runtime use.
- Existing `public.pos_*` tables from V74/V75 should be treated as deprecated compatibility artifacts until a later data migration/backfill decision.
- A future global device registry summary may be added only if platform operations need cross-tenant fleet visibility, and it must not store raw order payloads, idempotency payload hashes, detailed errors, sessions, or tenant audit payloads.

## Completed Items

- Phase 1: Added tenant offline table identifiers in `TenantSqlIdentifiers`.
- Phase 1: Added `V77__create_pos_offline_sync_tenant_tables.sql`.
- Phase 1: Added `public.create_offline_sync_tables_for_tenant(schema_name text)`.
- Phase 1: V77 runs the helper for all existing `c_%` tenant schemas.
- Phase 1: Updated offline repositories to resolve table names with `TenantSqlIdentifiers.posXTable(companyId)`.
- Phase 1: Updated repository lookups/updates to require `company_id` and `branch_id` where relevant.
- Phase 1: Added guarded startup migration service controlled by `valueinsoft.pos.offline.run-tenant-migration-on-startup=false`.
- Phase 1: Added environment override `VLS_POS_OFFLINE_RUN_TENANT_MIGRATION_ON_STARTUP`.
- Phase 1: Kept V74/V75 public `pos_*` tables in place as deprecated compatibility artifacts.
- Phase 2: Added principal-aware authorization to every offline POS endpoint.
- Phase 2: Added `V78__pos_offline_sync_capabilities.sql` for offline POS capability registration and role grants.
- Phase 2: Secured device registration with `pos.device.register`.
- Phase 2: Secured device heartbeat with `pos.device.heartbeat`.
- Phase 2: Secured bootstrap data with `pos.bootstrap.read`.
- Phase 2: Secured offline sync upload with `pos.offline.sync`.
- Phase 2: Secured sync status with `pos.offline.status`.
- Phase 2: Secured sync errors with `pos.offline.errors`.
- Phase 2: Secured retry with `pos.offline.retry`.
- Phase 2: Retry capability is granted to Owner and BranchManager only, not Cashier.
- Phase 2: Added unauthenticated principal handling with `UNAUTHENTICATED`.
- Phase 2: Added audit events for device registration requested/succeeded, heartbeat received, batch received, status viewed, errors viewed, and retry requested.
- Phase 3A: Added compact bootstrap response DTOs for products, prices, payment methods, settings, cashier permissions, taxes, and discounts.
- Phase 3B: Implemented PRODUCTS bootstrap from tenant `inventory_product` joined to branch-scoped `inventory_branch_stock_balance`.
- Phase 3C: Implemented PRICES bootstrap from tenant `inventory_product`, branch stock, and `inventory_pricing_policy`.
- Phase 3D: Added cursor pagination by `product_id`, `hasMore`, `nextCursor`, `lastUpdatedAt`, and max page-size enforcement.
- Phase 3D: Added `V79__pos_offline_bootstrap_indexes.sql` for tenant product cursor and branch stock lookup indexes.
- Phase 3E: Added manual bootstrap endpoint test coverage notes.
- Phase 4: Replaced empty sync error response with tenant-scoped error retrieval.
- Phase 4: Added paginated `SyncErrorListResponse` and compact `SyncErrorItemResponse`.
- Phase 4: Error reads filter by `companyId`, `branchId`, and `batchId`, joining tenant import/error tables without N+1 queries.
- Phase 4: Added retry result DTO `OfflineRetryResultResponse`.
- Phase 4: Added `PENDING_RETRY` import status for accepted retry baseline.
- Phase 4: Added `V80__pos_offline_retry_baseline.sql` for `last_retry_at`, retry-safe status check, and retry/error indexes.
- Phase 4: Retry is accepted only for `FAILED` and `NEEDS_REVIEW` imports.
- Phase 4: Retry rejects `PENDING`, `PENDING_RETRY`, `PROCESSING`, `SYNCED`, `DUPLICATE`, and unknown/non-eligible states.
- Phase 4: Retry increments `retry_count`, sets `last_retry_at`, clears top-level import error fields, and marks import `PENDING_RETRY`.
- Phase 4: Retry does not create invoices, payments, inventory movements, or finance postings.
- Phase 4: Added audit events for retry requested, accepted, and rejected.
- Phase 5: Added atomic idempotency claim behavior before offline import creation.
- Phase 5: Idempotency claims insert tenant `pos_idempotency_key` rows with `RECEIVED` status.
- Phase 5: Duplicate idempotency key with same payload hash returns the existing import/result state instead of creating another import row.
- Phase 5: Duplicate idempotency key with different payload hash returns `IDEMPOTENCY_PAYLOAD_MISMATCH`.
- Phase 5: Duplicate-key insert races are handled by re-reading the existing idempotency record.
- Phase 5: Retry now validates that the import payload hash matches the idempotency record before accepting `PENDING_RETRY`.
- Phase 5: Added `PAYLOAD_MISMATCH` idempotency status.
- Phase 5: Added `V81__pos_offline_idempotency_hardening.sql` for idempotency status/default and lookup indexes.
- Phase 6: Added internal processing service boundaries without exposing a public processing endpoint.
- Phase 6: Added per-import processing skeleton in `OfflineSingleOrderProcessor`.
- Phase 6: Added atomic import claiming from `PENDING` and `PENDING_RETRY` using conditional tenant SQL with `FOR UPDATE SKIP LOCKED`.
- Phase 6: Added `READY_FOR_VALIDATION` placeholder import status; Phase 6 does not mark imports `SYNCED`.
- Phase 6: Added `processing_started_at` tracking for claimed imports.
- Phase 6: Processing verifies the existing idempotency record and payload hash before moving an import forward.
- Phase 6: Payload hash mismatch during processing marks the import `FAILED` and writes `IDEMPOTENCY_PAYLOAD_MISMATCH` to tenant error rows.
- Phase 6: Unexpected skeleton processing failures mark the import `FAILED`, write a compact error row, and audit the failure.
- Phase 6: Added audit events for processing started, skipped, failed, and placeholder completed.
- Phase 6: Added `V82__pos_offline_processing_skeleton.sql` for `processing_started_at`, `READY_FOR_VALIDATION`, and processing claim indexes.
- Phase 6: Upload remains storage-focused and does not run real posting.
- Phase 7: Added internal validation service boundaries without exposing a public validation endpoint.
- Phase 7: Added `OfflineOrderValidationProcessor` to claim and validate one `READY_FOR_VALIDATION` import per transaction.
- Phase 7: Added `OfflineOrderImportValidationService` for payload, tenant, device, cashier, product, price, totals, and payment validation.
- Phase 7: Added `OfflineOrderValidationRepository` with tenant-aware batch product/branch stock lookup.
- Phase 7: Added `VALIDATING`, `VALIDATED`, and `VALIDATION_FAILED` import statuses; `SYNCED` remains reserved for future posting.
- Phase 7: Valid imports move from `READY_FOR_VALIDATION` to `VALIDATING`, then `VALIDATED`.
- Phase 7: Invalid imports move to `VALIDATION_FAILED` and write compact tenant error rows through `SyncErrorService`.
- Phase 7: Product validation fetches all referenced product IDs/barcodes in one query and validates branch availability without N+1 queries.
- Phase 7: Validation checks idempotency payload hash again before accepting the import.
- Phase 7: Added audit events for validation started, passed, failed, and skipped.
- Phase 7: Added `V83__pos_offline_order_validation.sql` for validation statuses and lookup indexes.
- Phase 7: No invoice, payment, inventory, or finance posting was implemented.
- Phase 7.5: Completed static SQL review for migrations V77 through V83.
- Phase 7.5: Confirmed offline runtime code does not read or write `public.pos_*` tables.
- Phase 7.5: Confirmed every offline controller endpoint requires `Principal` and `AuthorizationService.assertAuthenticatedCapability(...)`.
- Phase 7.5: Confirmed bootstrap PRODUCTS/PRICES are branch-scoped, cursor-paginated, and capped by `maxBootstrapPageSize`.
- Phase 7.5: Confirmed idempotency statuses in enum, migration constraints, and repository writes are aligned.
- Phase 7.5: Confirmed validation does not create invoices, payments, inventory movements, or finance journal entries.
- Phase 7.5: Fixed V80 status constraint so all migration constraints include the current import status set.
- Phase 7.5: Fixed validation numeric parsing so quantities are not rounded before line-total validation.
- Phase 8A: Inspected existing online POS checkout flow from `OrderController` to `OrderService.createOrder`.
- Phase 8A: Mapped online POS posting side effects in `DbPosOrder.addOrder`: order header/detail insert, branch stock decrement, inventory transaction rows, stock ledger rows, shift cash movement, and finance posting request enqueue.
- Phase 8A: Confirmed finance POS posting uses `FinanceOperationalPostingService.enqueuePosSale` and `FinancePosPostingAdapter`.
- Phase 8A: Designed one-import-per-transaction posting boundary from `VALIDATED` imports.
- Phase 8A: Proposed atomic posting claim from `VALIDATED` to `POSTING`.
- Phase 8A: Recommended successful posting final status `SYNCED` with `POSTING_FAILED` for failed attempts; `POSTED` is not needed unless product wants a separate post-sync acknowledgement state.
- Phase 8A: Designed idempotency duplicate-posting protection and result metadata capture.
- Phase 8A: Designed future posting migration columns and indexes; no migration was created in Phase 8A.
- Phase 8A: No invoice, payment, inventory, or finance posting code was implemented.
- Phase 8B: Added `POSTING` and `POSTING_FAILED` import statuses.
- Phase 8B: Added `V84__pos_offline_posting_mvp.sql` for tenant posting metadata columns, status constraint update, posting indexes, and a reusable tenant posting upgrade helper.
- Phase 8B: Added `OfflineOrderPostingProcessor` for one-import posting from `VALIDATED` to `POSTING` to `SYNCED` or `POSTING_FAILED`.
- Phase 8B: Added tenant-aware atomic posting claims from `VALIDATED` only.
- Phase 8B: Added posting metadata updates for `posted_order_id`, `official_order_id`, `posting_started_at`, `posting_completed_at`, and posting error fields.
- Phase 8B: Added `postValidatedImports(companyId, branchId, batchId)` and `postSingleImport(companyId, branchId, offlineOrderImportId)` service boundaries.
- Phase 8B: Extracted reusable `PosSalePostingService` for online POS sale side effects so offline posting does not call controllers.
- Phase 8B: Online `OrderService.createOrder` now delegates to `PosSalePostingService` during normal operation while retaining the old fallback path.
- Phase 8B: Offline posting reuses the same POS sale side effects: order header/detail insert, branch stock decrement, legacy inventory transaction, modern stock ledger, active-shift cash movement, and existing after-commit finance enqueue.
- Phase 8B: Added integer compatibility guards for offline quantity, price, totals, and discounts; decimal values fail safely without rounding.
- Phase 8B: Added single-payment MVP guard; multi-tender offline sales fail safely with `OFFLINE_MULTI_TENDER_NOT_SUPPORTED`.
- Phase 8B: Posting verifies idempotency payload hash again before posting and blocks duplicate posted metadata with `OFFLINE_DUPLICATE_POSTING_ATTEMPT`.
- Phase 8B: Successful posting marks import `SYNCED` and idempotency `SYNCED` with posted order metadata.
- Phase 8B: Posting failures mark import `POSTING_FAILED`, write tenant error rows through `SyncErrorService`, and write tenant audit events.
- Phase 8B: No public `pos_*` runtime table access was added.
- Phase 8C: Added `V85__pos_offline_batch_finalization.sql` for expanded tenant batch counters and batch lifecycle statuses.
- Phase 8C: Added batch statuses `IN_PROGRESS`, `COMPLETED`, and `COMPLETED_WITH_ERRORS` while keeping legacy statuses compatible.
- Phase 8C: Added tenant batch summary recalculation in `PosSyncBatchRepository`.
- Phase 8C: Recalculation now updates legacy counters and expanded counters after processing, validation, posting, and retry state changes.
- Phase 8C: Batch status is not marked completed while imports remain in `PENDING`, `PENDING_RETRY`, `PROCESSING`, `READY_FOR_VALIDATION`, `VALIDATING`, `VALIDATED`, or `POSTING`.
- Phase 8C: Added stuck-state recovery baseline for `PROCESSING`, `VALIDATING`, and `POSTING`.
- Phase 8C: Stuck `POSTING` imports move to `NEEDS_REVIEW` and are not automatically reposted.
- Phase 8C: Reviewed finance visibility; `FinanceOperationalPostingService.enqueuePosSale` remains after-commit/non-blocking and does not expose posting request IDs in MVP.
- Phase 8C: Focused search confirmed no runtime offline access to `public.pos_*`; only migration comments mention deprecated public compatibility tables.
- Phase 8C: No scheduler or public/admin posting endpoint was added.
- Phase 8D: Added disabled-by-default offline worker configuration properties for future processing, validation, posting, batch size, delay, and stuck threshold.
- Phase 8D: Added focused `OfflineOrderPostingProcessorTest` coverage for posting skip, successful posting, idempotency sync, duplicate posted metadata blocking, integer payload mapping, decimal quantity rejection, and multi-tender rejection.
- Phase 8D: Added `PosOfflineSyncServiceOperationalTest` coverage for stuck recovery calling the safe `POSTING` to `NEEDS_REVIEW` path and recalculating batch summary.
- Phase 8D: Added live PostgreSQL migration validation guide for V77 through V85 tenant schema verification.
- Phase 8D: Documented safe operational worker flow: recover stuck imports, process pending imports, validate ready imports, post validated imports only when explicitly enabled, then recalculate batch summary.
- Phase 8D: Documented future admin trigger requirement for high-privilege `pos.offline.admin.process`; no public/admin endpoint was added.
- Phase 8D: Targeted offline posting tests passed.
- Phase 8D: Compile and test compilation passed.
- Phase 9A: Added dedicated `OfflinePosWorkerProperties` bound to `valueinsoft.pos.offline.worker.*` with safe disabled defaults.
- Phase 9A: Added explicit `valueinsoft.pos.offline.worker.targets` allowlist in `companyId:branchId` format so the worker does not scan all tenant schemas.
- Phase 9A: Added `OfflinePosWorker` scheduled service that exits immediately unless `worker.enabled=true`.
- Phase 9A: Worker processing, validation, and posting steps are independently gated; posting never runs from `worker.enabled=true` alone.
- Phase 9A: Added tenant-aware active batch selection for configured company/branch targets.
- Phase 9A: Worker flow recovers stuck imports, optionally processes, optionally validates, optionally posts, and recalculates batch summary per batch.
- Phase 9A: Worker catches exceptions per batch and logs compact status/counts without raw payloads.
- Phase 9A: No public/admin endpoint was added and no automatic posting was enabled by default.
- Phase 9B: Added `V86__pos_offline_admin_capability.sql` for high-privilege `pos.offline.admin.process`.
- Phase 9B: `pos.offline.admin.process` is granted to high-privilege roles only where present (`Owner`, `Admin`, `SupportAdmin`) and not to Cashier.
- Phase 9B: Added `PosOfflineAdminController` under `/api/admin/pos/offline-sync`.
- Phase 9B: Added secure admin triggers for recover stuck, process, validate, post, and recalculate summary.
- Phase 9B: Every admin trigger requires `Principal` and `AuthorizationService.assertAuthenticatedCapability(..., "pos.offline.admin.process")`.
- Phase 9B: Admin triggers reuse existing service boundaries and atomic claims; posting still only claims `VALIDATED` imports.
- Phase 9B: Added compact `OfflineAdminOperationResponse`.
- Phase 9B: Added tenant audit events for each admin operation request without raw payloads.
- Phase 9C: Expanded admin operation responses with batch status, processed/posted/skipped counts, failure counters, eligible posting count, recalculation flag, and warnings.
- Phase 9C: Added tenant-aware import status count aggregation for admin responses without N+1 queries.
- Phase 9C: Added `POST /api/admin/pos/offline-sync/batches/{batchId}/post-preview` as a read-only posting dry run.
- Phase 9C: Post preview requires `pos.offline.admin.process` and does not create orders, decrement stock, update import statuses, or update idempotency rows.
- Phase 9C: Post preview reports eligible `VALIDATED` import count and warning conditions for `POSTING`, `NEEDS_REVIEW`, and empty eligible sets.
- Phase 9C: Actual admin post now returns warnings when `POSTING` or `NEEDS_REVIEW` rows are present before posting.
- Phase 9C: Added optional admin operation request body with `reason`; reasons are written only to audit metadata.
- Phase 9C: No decimal posting support, multi-tender support, finance behavior change, automatic posting enablement, or public `pos_*` runtime access was added.
- Phase 9D: Added `OfflinePosAdminProperties` for disabled-by-default admin posting controls.
- Phase 9D: Added `valueinsoft.pos.offline.admin.posting-enabled=false` and `valueinsoft.pos.offline.admin.max-post-batch-size=50`.
- Phase 9D: Admin post preview remains allowed when actual admin posting is disabled.
- Phase 9D: Actual admin post now returns `accepted=false` without posting when admin posting is disabled.
- Phase 9D: Admin post and recover-stuck now require a nonblank reason before execution.
- Phase 9D: Admin post now requires `force=true` when `POSTING` or `NEEDS_REVIEW` warning states exist.
- Phase 9D: Admin post now rejects batches whose eligible `VALIDATED` count exceeds the configured max post batch size.
- Phase 9D: Blocked admin post/recover operations write compact blocked audit events when tenant audit is available.
- Phase 9D: Process, validate, recalculate-summary, and post-preview remain reason-optional.
- Phase 9E: Added `GET /api/admin/pos/offline-sync/batches/{batchId}` for secure admin batch details.
- Phase 9E: Batch details requires `Principal` and `pos.offline.admin.process`.
- Phase 9E: Added compact `OfflineAdminBatchDetailsResponse` with batch status, timestamps, legacy counters, import status counts, warnings, readiness, recent admin events, and error summary.
- Phase 9E: Added readiness flags and blocked reason lists for recover-stuck, process, validate, post, and recalculate-summary.
- Phase 9E: Readiness reports `ADMIN_POSTING_DISABLED`, `NO_VALIDATED_IMPORTS`, `POSTING_ROWS_EXIST`, `NEEDS_REVIEW_ROWS_EXIST`, and `MAX_POST_BATCH_SIZE_EXCEEDED` for post blocking.
- Phase 9E: Added tenant recent admin event lookup from `pos_sync_audit_log` without raw payload exposure.
- Phase 9E: Added tenant error summary lookup grouped by `error_code` without reading raw order payload columns.
- Phase 10B: Added `FinanceOperationalPostingService.enqueuePosSaleAndReturnRequest(...)` while preserving the existing void `enqueuePosSale(...)` wrapper.
- Phase 10B: Extended `PosSalePostingService` with optional after-commit finance success/failure callbacks without changing existing online POS behavior.
- Phase 10B: Added `V87__pos_offline_finance_request_capture.sql` for tenant `finance_enqueue_status`, `finance_enqueue_error`, and idempotency `result_metadata`.
- Phase 10B: Offline posting now captures `finance_posting_request_id` when after-commit finance enqueue returns a request.
- Phase 10B: Offline imports remain `SYNCED` when finance request ID capture is unavailable or enqueue fails.
- Phase 10B: Idempotency result metadata now includes `postedOrderId`, `officialOrderId`, `financePostingRequestId` when available, and compact finance status.
- Phase 10B: Finance enqueue failures keep existing logging behavior and add compact tenant warning/error metadata when available.
- Phase 10C: Added `GET /api/admin/pos/offline-sync/imports/{offlineOrderImportId}` for secure admin import details.
- Phase 10C: Import details requires `Principal` and `pos.offline.admin.process`.
- Phase 10C: Import details validates company/branch access and returns safe not found when the import is outside the requested tenant/branch.
- Phase 10C: Added compact import details DTOs for posting metadata, finance enqueue metadata, masked idempotency fields, latest errors, latest import audit events, and online order reference.
- Phase 10C: Import details does not select or return raw offline order payload JSON.
- Phase 10C: Long finance errors, import errors, and metadata are truncated for support-safe responses.
- Phase 10D: Added frontend Offline Sync Batches admin screen under the existing app shell as `OfflineSyncAdmin`.
- Phase 10D: Added capability-gated sidebar/settings navigation requiring `pos.offline.admin.process`.
- Phase 10D: Added manual batch id lookup using the existing secure batch details endpoint.
- Phase 10D: Added guarded admin action controls for recover stuck, process, validate, post preview, post, and recalculate summary.
- Phase 10D: UI treats `accepted=false` responses as blocked/no-side-effect outcomes, not successful completion.
- Phase 10D: Post flow calls post-preview before confirmation, requires a reason, and supports backend force-confirmation requirements.
- Phase 10D: Added manual import id lookup using the Phase 10C import details endpoint.
- Phase 10D: Import details UI displays posting, finance, idempotency, error, audit, and online order reference metadata without raw payload exposure.
- Phase 10E: Added `GET /api/admin/pos/offline-sync/batches` for secure, tenant-scoped admin batch listing.
- Phase 10E: Batch list requires `Principal` and `pos.offline.admin.process`.
- Phase 10E: Batch list filters by requested `companyId` and `branchId`, with optional `status` and `activeOnly`.
- Phase 10E: Batch list uses newest-first cursor pagination with page size capped at 100.
- Phase 10E: Batch list returns compact batch summary fields only and does not expose raw offline order payloads.
- Phase 10E: Offline Sync Admin UI now loads a recent batch table from the list endpoint.
- Phase 10E: UI supports status filter, active-only filter, refresh, cursor-based load more, and row-click batch details.
- Phase 10E: Manual batch id lookup remains available as fallback.
- Phase 10F: Added `GET /api/admin/pos/offline-sync/batches/{batchId}/imports` for secure, tenant-scoped admin import listing.
- Phase 10F: Import list requires `Principal` and `pos.offline.admin.process`.
- Phase 10F: Import list filters by requested `companyId`, `branchId`, and `batchId`, with optional `status` and `errorCode`.
- Phase 10F: Import list uses newest-first cursor pagination with page size capped at 100.
- Phase 10F: Import list returns compact import items and does not expose raw offline order payloads.
- Phase 10F: Offline Sync Admin UI now loads an imports table under the selected batch details.
- Phase 10F: UI supports import status filter, error code filter, refresh, cursor-based load more, and row-click import details.
- Phase 10F: Manual import id lookup remains available as fallback.
- Phase 10G: Added `dexie` to `package.json` for IndexedDB abstraction.
- Phase 10G: Added `offlinePosDb.js` defining local tables for products, prices, bootstrap state, and order queue.
- Phase 10G: Added `useOfflineStatus` hook listening to `window` `online`/`offline` events.
- Phase 10G: Added `offlineDataSyncService.js` to coordinate paginated bootstrap downloads (PRODUCTS and PRICES).
- Phase 10G: Added `offlineSyncApi.js` wrapper to hit the existing POS bootstrap endpoints.
- Phase 10G: Created `OfflinePosReadinessPanel.js` showing offline status, sync counts, and a trigger for manual download.
- Phase 10G: Integrated `OfflinePosReadinessPanel` into `PosSalesPage.js` without disrupting online checkout workflows.
- Phase 10G: Updated `usePosCatalog.js` to intelligently fall back to IndexedDB local `searchOfflineProducts` when `isOffline` is true.
- Phase 10H: Implemented `useOfflineBootstrapState` hook to cleanly verify data readiness.
- Phase 10H: Added `queueOfflineOrder` to `offlineDataSyncService.js` to serialize payloads and manage local IndexedDB insert.
- Phase 10H: Implemented local order generation (`localOrderId`) and initial payload hashing using standard `crypto.subtle`.
- Phase 10H: Updated `OfflinePosReadinessPanel.js` with a "View Queue" modal to list `QUEUED` and `SYNC_FAILED` orders locally.
- Phase 10H: Updated `PayStatment.js` with offline conditional logic, exposing a "Save Offline Order" button instead of the direct backend checkout.
- Phase 10H: Protected offline saves by ensuring `isOfflineReady` checks for successful bootstrap data completion.
- Phase 10I: Implemented `uploadOfflineOrders` inside `offlineSyncApi.js` targeting `POST /api/pos/offline-sync/upload`.
- Phase 10I: Implemented `syncQueuedOfflineOrders` inside `offlineDataSyncService.js` to format `QUEUED`/`SYNC_FAILED` orders into `OfflineSyncUploadRequest`.
- Phase 10I: Mapped local cart entries safely into backend-expected `OfflineOrderRequest` and `OfflineOrderItemRequest` shapes.
- Phase 10I: Tracked local queue statuses natively without deletion (`QUEUED` -> `SYNCING` -> `BACKEND_RECEIVED`).
- Phase 10I: Enhanced `OfflinePosReadinessPanel.js` queue modal with manual "Sync Queued Orders" capability.
- Phase 10J: Implemented `fetchOfflineBatchStatus` API call to GET `/api/pos/offline-sync/status/{batchId}`.
- Phase 10J: Added `pollQueuedOfflineOrderStatuses` to update local `backendStatus` based on syncBatchId.
- Phase 10J: Upgraded Queue modal with a "Refresh Sync Status" button.
- Phase 10J: Clarified UI boundaries matching backend limits where batch-level status dictates overarching progress.
- Inspected `TenantSqlIdentifiers`.
- Inspected current offline sync schema usage.
- Inspected offline repositories and services that access `pos_*` tables.
- Reviewed tenant schema patterns in migrations and Java table identifier usage.
- Chose tenant-schema isolation before implementing business logic.

## Affected Files

- `src/main/java/com/example/valueinsoftbackend/util/TenantSqlIdentifiers.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/PosDeviceRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/PosSyncBatchRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/OfflineOrderImportRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/PosSyncBatchRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/PosIdempotencyRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/OfflineOrderErrorRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/BootstrapVersionRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/SyncAuditLogRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/config/OfflinePosProperties.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/config/OfflinePosAdminProperties.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/config/OfflinePosWorkerProperties.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/controller/PosOfflineSyncController.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/controller/PosOfflineAdminController.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/PosDeviceService.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/PosOfflineSyncService.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/SyncErrorService.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/OfflineSyncTenantMigrationService.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/BootstrapDataRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/BootstrapDataResponse.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/BootstrapPage.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineBootstrapProductItem.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineBootstrapPriceItem.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineBootstrapPaymentMethodItem.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineBootstrapPosSettingItem.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineBootstrapCashierPermissionItem.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineBootstrapTaxItem.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineBootstrapDiscountItem.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/SyncErrorListResponse.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/SyncErrorItemResponse.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineRetryResultResponse.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminOperationResponse.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminBatchDetailsResponse.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminReadiness.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminRecentEvent.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminErrorSummary.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminErrorCodeCount.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminImportDetailsResponse.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminImportErrorItem.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminImportAuditEvent.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminOnlineOrderReference.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/request/OfflineAdminOperationRequest.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/enums/OfflineOrderImportStatus.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/enums/PosSyncBatchStatus.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/enums/PosIdempotencyStatus.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/model/OfflineValidationProductSnapshot.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/model/OfflineImportStatusCounts.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/model/OfflineAdminImportDetailsSnapshot.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/OfflineOrderValidationRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/IdempotencyClaimResult.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/OfflineSingleOrderProcessor.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/OfflineOrderImportValidationService.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/OfflineOrderValidationProcessor.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/OfflinePosWorker.java`
- `src/main/java/com/example/valueinsoftbackend/Service/FinanceOperationalPostingService.java`
- `src/main/java/com/example/valueinsoftbackend/Service/PosSalePostingService.java`
- `src/main/java/com/example/valueinsoftbackend/Service/OrderService.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/OfflineOrderPostingProcessor.java`
- `src/test/java/com/example/valueinsoftbackend/pos/offline/service/OfflineOrderPostingProcessorTest.java`
- `src/test/java/com/example/valueinsoftbackend/pos/offline/service/PosOfflineSyncServiceOperationalTest.java`
- `src/main/resources/application.properties`
- `docs/OFFLINE_POS_MIGRATION_VALIDATION.md`
- Frontend: `src/domains/pos-offline/api/offlineAdminApi.js`
- Frontend: `src/domains/pos-offline/pages/OfflineSyncAdminPage.js`
- Frontend: `src/domains/pos-offline/pages/OfflineSyncAdminPage.css`
- Frontend: `src/Components/SideNavBarPro/Main.js`
- Frontend: `src/Components/SideNavBarPro/Aside.js`
- Frontend: `src/domains/app-shell/config/appShellAccess.js`
- Frontend: `src/domains/navigation/config/appNavigationSchema.js`
- Frontend: `docs/OFFLINE_POS_ADMIN_UI.md`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminBatchListResponse.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/dto/response/OfflineAdminBatchListItem.java`
- `src/main/resources/db/migration/V77__create_pos_offline_sync_tenant_tables.sql`
- `src/main/resources/db/migration/V78__pos_offline_sync_capabilities.sql`
- `src/main/resources/db/migration/V79__pos_offline_bootstrap_indexes.sql`
- `src/main/resources/db/migration/V80__pos_offline_retry_baseline.sql`
- `src/main/resources/db/migration/V81__pos_offline_idempotency_hardening.sql`
- `src/main/resources/db/migration/V82__pos_offline_processing_skeleton.sql`
- `src/main/resources/db/migration/V83__pos_offline_order_validation.sql`
- `src/main/resources/db/migration/V84__pos_offline_posting_mvp.sql`
- `src/main/resources/db/migration/V85__pos_offline_batch_finalization.sql`
- `src/main/resources/db/migration/V86__pos_offline_admin_capability.sql`
- `src/main/resources/db/migration/V87__pos_offline_finance_request_capture.sql`
- `src/main/resources/db/migration/V74__create_pos_offline_sync_tables.sql`
- `src/main/resources/db/migration/V75__create_pos_offline_sync_indexes.sql`
- `src/main/resources/db/migration/V76__alter_sales_tables_for_offline_sync.sql`

## Known Risks

- Existing public V74/V75 offline rows are not automatically copied to tenant schemas.
- V74/V75 created public offline tables; existing deployments may already contain data there.
- Moving table access to tenant schemas makes older public offline data invisible to normal runtime access until a data migration/backfill is approved.
- New tenant onboarding must call `public.create_offline_sync_tables_for_tenant(schema_name)` after tenant schema creation, or enable the guarded startup runner intentionally.
- `GET /api/pos/offline-sync/status/{batchId}`, `GET /api/pos/offline-sync/errors/{batchId}`, and `POST /api/pos/offline-sync/retry/{offlineOrderImportId}` now require `companyId` and `branchId` query parameters so they can resolve the tenant schema safely.
- V77 SQL was added but not executed against a live PostgreSQL database in this phase.
- V78 capability grants must be migrated before the secured endpoints can be used by normal roles.
- `registered_by` remains null during device registration until a principal-to-user-id resolver is added.
- Audit events use `pos_sync_audit_log`; if tenant offline tables have not been created yet, audit write failures are caught and logged without breaking the main request.
- PRODUCTS and PRICES depend on modern tenant inventory tables (`inventory_product`, `inventory_branch_stock_balance`, `inventory_pricing_policy`) existing for the tenant schema.
- PAYMENT_METHODS, POS_SETTINGS, CASHIER_PERMISSIONS, TAXES, and DISCOUNTS currently return safe compact defaults/placeholders until their final source tables are approved.
- Product active status uses a conservative default because the current product state column represents condition (`New`/`Used`) rather than lifecycle active/inactive.
- V80 must be migrated before `PENDING_RETRY` can be persisted in databases that already have tenant offline tables.
- Accepted retries are only queued by status; actual reprocessing is intentionally deferred to a later processing pipeline phase.
- Previous detailed error rows are retained for audit/history. Phase 4 clears only top-level import `error_code` and `error_message`.
- V81 must be migrated before `RECEIVED` and `PAYLOAD_MISMATCH` idempotency states are persisted in existing tenant schemas.
- The tenant idempotency table scopes uniqueness by `company_id`, `branch_id`, `device_id`, and `idempotency_key`; `device_code` is not duplicated in `pos_idempotency_key`.
- If an idempotency claim is created but import insertion fails for an unexpected non-duplicate error, the claim may remain `RECEIVED` until a later reconciliation/processing phase handles it.
- V82 must be migrated before `READY_FOR_VALIDATION` and `processing_started_at` can be used in existing tenant schemas.
- Phase 6 processing is internal service-level skeleton only; there is no scheduler or public/admin processing endpoint yet.
- Imports marked `READY_FOR_VALIDATION` are intentionally parked for a later validation phase and are not invoice/payment/inventory/finance posted.
- Batch summary counts are not advanced by Phase 6 because no final order outcome is produced yet.
- V83 must be migrated before `VALIDATING`, `VALIDATED`, and `VALIDATION_FAILED` can be used in existing tenant schemas.
- Phase 7 validation is internal service-level only; there is no scheduler or public/admin validation endpoint yet.
- Price validation is conservative because final pricing policy enforcement is not approved yet; Phase 7 checks non-negative unit price and rejects prices below `inventory_product.lowest_price`.
- Cashier validation uses `public.users` branch membership because the current user identity table remains public in the project architecture.
- Validated imports are not posted and batch summary counts are not finalized until a later posting phase.
- Phase 7.5 did not run live Flyway/PostgreSQL migration tests because Docker is installed but the Docker daemon is not running in this environment.
- Static SQL review did not identify PostgreSQL syntax problems after the V80 status-constraint fix.
- Phase 8B must decide whether to call `OrderService.createOrder` directly or extract a lower-level `PosSalePostingService`; direct reuse currently enqueues finance after commit, which may complicate all-or-nothing offline posting semantics.
- Existing online order tables store integer quantity/price/total fields, while offline payloads allow decimal quantities and BigDecimal amounts. Phase 8B must either constrain offline posting to integer-compatible quantities/amounts or add an approved decimal-capable posting path.
- Existing online POS flow has no separate payment table for POS sale tenders; finance posting supports multiple payment tenders in payload, but operational payment persistence must be designed before posting multi-tender offline sales.
- Existing online POS finance enqueue happens after order commit and logs failures without rolling back the order. Offline posting should decide whether finance enqueue failure blocks final `SYNCED` status.
- Phase 8A did not create `posting_started_at`, `posted_order_id`, or related columns; they are design-only until Phase 8B approval.
- V84 must be migrated before `POSTING`, `POSTING_FAILED`, or posting metadata columns can be used in existing tenant schemas.
- Phase 8B uses the existing online POS integer-compatible order tables. Decimal quantities or decimal amounts are rejected with explicit errors instead of rounded.
- Phase 8B supports a single payment tender only. Multi-tender offline orders are marked `POSTING_FAILED` until operational payment persistence is approved.
- Phase 8B uses server posting time because the current online POS insert path uses server time.
- Phase 8B stores cashier as `offline-cashier-{cashierId}` in the online order `salesUser` field until a principal/cashier-name resolver is approved.
- Phase 8B sets online `orderIncome` to the posted total for MVP compatibility; exact profit calculation should be revisited before broad reporting rollout.
- Phase 8B follows existing online finance behavior: finance enqueue is after-commit and non-blocking. A finance enqueue failure can be logged by the online posting service after the import is already `SYNCED`.
- `finance_posting_request_id` and `finance_journal_entry_id` columns are added by V84. Phase 10B populates `finance_posting_request_id` only when after-commit enqueue returns a request; `finance_journal_entry_id` remains unavailable until the finance posting worker posts the request.
- `OrderService.createOrder` delegates to `PosSalePostingService`, but the previous inline online path remains as a fallback branch for safety.
- V85 must be migrated before expanded batch counters and `IN_PROGRESS`/`COMPLETED`/`COMPLETED_WITH_ERRORS` statuses can be persisted in existing tenant schemas.
- Batch finalization is service-triggered only; no scheduler or endpoint invokes it automatically.
- Stuck-state recovery is explicit/internal only. `POSTING` rows are moved to `NEEDS_REVIEW` because side effects may have partially committed.
- Finance posting request IDs are captured after commit and may appear shortly after the import is marked `SYNCED`; admin/operator views should tolerate this brief metadata lag.
- Phase 8D added focused Mockito unit coverage for the posting processor, but there is still no real PostgreSQL/Testcontainers integration suite for offline posting side effects.
- Live migration validation is documented in `docs/OFFLINE_POS_MIGRATION_VALIDATION.md`, but it was not executed against a live PostgreSQL server in this phase.
- Offline worker properties are disabled by default and configuration-only in Phase 8D; no scheduler or admin trigger executes them yet.
- Future operational triggers must not enable posting unless both `valueinsoft.pos.offline.worker.enabled=true` and `valueinsoft.pos.offline.worker.posting-enabled=true`.
- Future admin processing endpoints must require a high-privilege capability such as `pos.offline.admin.process`; cashier/offline sync capabilities must not trigger posting.
- Phase 9A worker executes only for explicitly configured `valueinsoft.pos.offline.worker.targets`; if no targets are configured it skips even when globally enabled.
- Phase 9A worker depends on the application's scheduling infrastructure. Existing global scheduling disablement can prevent the worker from running even if offline worker properties are enabled.
- `valueinsoft.pos.offline.worker.enabled=true` by itself runs only stuck recovery and summary recalculation for configured active batches; processing, validation, and posting remain separately gated.
- Automatic posting remains high risk and must be enabled only in a controlled local/staging environment until PostgreSQL integration tests are approved.
- V86 must be migrated before admin/internal operational endpoints can be used by high-privilege roles.
- Admin operational endpoints are powerful; they must remain restricted to `pos.offline.admin.process` and must not be granted to Cashier or normal offline sync roles.
- `SupportAdmin` grant is global-admin scoped and depends on the existing effective capability resolver including global admin grants for tenant/branch operations.
- Phase 9C admin responses derive failure and eligible counters from tenant import status counts after the operation; `processedCount` remains the direct service operation count.
- Phase 9C `post-preview` is intentionally read-only and does not parse raw order payloads, so decimal quantity/amount and multi-tender warnings are generic until actual posting guards run.
- Phase 9C actual admin post warns about `POSTING` and `NEEDS_REVIEW` rows but does not block posting of other `VALIDATED` rows; manual operators must review warnings before proceeding.
- Phase 9D actual admin posting is disabled by default even for privileged operators; production enablement requires `valueinsoft.pos.offline.admin.posting-enabled=true`.
- Phase 9D force confirmation is required only for `POSTING`/`NEEDS_REVIEW` safety warnings; it does not override status claims, decimal guards, multi-tender guards, or max batch size.
- Phase 9D blocked operations are returned as HTTP 200 with `accepted=false` so operators receive current batch status and warnings without side effects.
- Phase 9E batch details exposes recent admin events and reason metadata for operational visibility; it intentionally does not expose full audit payloads or raw offline order payloads.
- Phase 9E readiness is advisory for UI/support operators. Actual mutation endpoints still enforce authorization, reason, force, config, max-size, and atomic status claims independently.
- Phase 9E error summary is grouped by current tenant error rows; older retained historical errors remain included until an approved archival/filtering policy exists.
- V87 must be migrated before `finance_enqueue_status`, `finance_enqueue_error`, or idempotency `result_metadata` can be persisted in existing tenant schemas.
- Phase 10B does not change finance rollback semantics; a finance enqueue failure after order commit leaves the offline import `SYNCED` with `financeStatus=ENQUEUE_FAILED`.
- Phase 10B does not expose import-level finance visibility in the admin batch details endpoint; a future import details endpoint should show per-import request IDs and finance status.
- Existing finance request processing still owns final journal creation, so `finance_journal_entry_id` remains null until a later capture path is approved.
- Phase 10C import details is support-facing and compact; it intentionally omits raw `payload_json`, full payload hash, full idempotency key, and full audit payload JSON.
- Phase 10C import audit history includes only events directly correlated by `offline_order_import_id`; batch-level admin operations are visible through the batch details endpoint instead.
- Phase 10C online order reference is a lightweight table/id pointer only and does not join online order detail rows.
- Phase 10D initially used manual batch id lookup before the Phase 10E batch list endpoint was added.
- Phase 10D action buttons are advisory-disabled from readiness flags, but backend authorization and safety gates remain the source of truth.
- Phase 10D post confirmation depends on backend `requiresForceForPost`; operators must still use a controlled environment before enabling backend posting.
- Phase 10D import lookup depends on support knowing the `offlineOrderImportId` until a future import list endpoint is approved.
- Phase 10E batch list depends on V85 summary columns for validated/posting-failed/validation-failed counts in existing tenant schemas.
- Phase 10E cursor pagination is newest-first by `created_at` and batch id; newly inserted batches can appear before the current cursor during operator refresh.
- Phase 10E finance enqueue status summary is not included in the list because it would require extra import aggregation; finance metadata remains available in import details.

## API Examples

Existing endpoints under review:
- `POST /api/pos/device/register` requires `pos.device.register`
- `POST /api/pos/device/heartbeat` requires `pos.device.heartbeat`
- `GET /api/pos/bootstrap-data` requires `pos.bootstrap.read`
- `GET /api/pos/bootstrap-data?companyId=1095&branchId=1&dataType=PRODUCTS&size=100`
- `GET /api/pos/bootstrap-data?companyId=1095&branchId=1&dataType=PRICES&cursor=100&size=100`
- `POST /api/pos/offline-sync/upload` requires `pos.offline.sync`
- `GET /api/pos/offline-sync/status/{batchId}?companyId=1095&branchId=1`
- `GET /api/pos/offline-sync/errors/{batchId}?companyId=1095&branchId=1&size=100`
- `GET /api/pos/offline-sync/errors/{batchId}?companyId=1095&branchId=1&cursor=250&size=100`
- `POST /api/pos/offline-sync/retry/{offlineOrderImportId}?companyId=1095&branchId=1`
- Duplicate upload with same idempotency key and same payload hash returns an idempotent replay result with no new import row.
- Duplicate upload with same idempotency key and different payload hash returns `IDEMPOTENCY_PAYLOAD_MISMATCH`.

## Manual Test Steps

Phase 0 documentation-only checks:
- Confirm `docs/OFFLINE_POS_IMPLEMENTATION_CHECKLIST.md` exists.
- Confirm the Tenant Isolation Decision section selects Option B.

Phase 1 manual checks:
- Create or use tenant schema `c_1095`.
- Run Flyway migration V77 or call `SELECT public.create_offline_sync_tables_for_tenant('c_1095');`.
- Verify these tables exist:
  - `c_1095.pos_device`
  - `c_1095.pos_sync_batch`
  - `c_1095.pos_offline_order_import`
  - `c_1095.pos_idempotency_key`
  - `c_1095.pos_offline_order_error`
  - `c_1095.pos_bootstrap_version`
  - `c_1095.pos_device_session`
  - `c_1095.pos_sync_audit_log`
- Register a device and confirm the row is inserted into `c_1095.pos_device`, not `public.pos_device`.
- Confirm no new runtime row inserts into `public.pos_device`.
- Upload a sync batch and confirm raw orders are inserted into `c_1095.pos_offline_order_import`.
- Confirm no new runtime row inserts into `public.pos_offline_order_import`.

Phase 2 manual checks:
- Call device register without a token and confirm the request is rejected.
- Call bootstrap without a token and confirm the request is rejected.
- Call sync upload without a token and confirm the request is rejected.
- Call status, errors, and retry without a token and confirm each request is rejected.
- Call with a valid user but wrong branch and confirm the request is rejected.
- Call with a valid user and correct branch/capability and confirm the request is allowed.
- Confirm device register writes to `c_{companyId}.pos_device`.
- Confirm retry requires `pos.offline.retry` capability.
- Confirm a Cashier can use heartbeat/bootstrap/sync/status/errors but cannot retry.

Phase 3 manual checks:
- Call `GET /api/pos/bootstrap-data?companyId=1095&branchId=1&dataType=PRODUCTS&size=50` with a token that has `pos.bootstrap.read`.
- Confirm response contains only compact product fields: `productId`, `barcode`, `name`, `price`, `lowestPrice`, `currentStock`, `category`, `active`, `uomCode`, `pricingPolicyCode`, `updatedAt`.
- Confirm products are returned only when they have branch stock rows for the requested `branchId`.
- Call the next page using `nextCursor` and confirm no duplicate `productId` values.
- Call `GET /api/pos/bootstrap-data?companyId=1095&branchId=1&dataType=PRICES&size=50`.
- Confirm price rows include `productId`, `retailPrice`, `lowestPrice`, `buyingPrice`, `pricingPolicyCode`, `pricingStrategyType`, and `pricingConfigJson`.
- Request a size larger than `valueinsoft.pos.offline.max-bootstrap-page-size` and confirm the service caps the page size.
- Call with invalid `dataType` and confirm `UNSUPPORTED_BOOTSTRAP_DATA_TYPE`.
- Call with invalid `cursor` and confirm `INVALID_BOOTSTRAP_CURSOR`.
- Call without token or with wrong branch access and confirm authorization rejects the request before data access.

Phase 4 manual checks:
- Call errors endpoint without token and confirm rejection.
- Call errors endpoint with wrong branch and confirm rejection.
- Call errors endpoint with valid branch and `pos.offline.errors` and confirm tenant-scoped errors are returned.
- Call errors endpoint with `size` and `cursor`; confirm `hasMore` and `nextCursor` work without duplicate `errorId` values.
- Retry a `FAILED` import with `pos.offline.retry`; confirm status changes to `PENDING_RETRY`, `retry_count` increments, and `last_retry_at` is set.
- Retry a `NEEDS_REVIEW` import with `pos.offline.retry`; confirm status changes to `PENDING_RETRY`.
- Retry a `SYNCED`, `PROCESSING`, `PENDING`, `PENDING_RETRY`, or `DUPLICATE` import and confirm `OFFLINE_ORDER_RETRY_NOT_ALLOWED`.
- Retry with Cashier role and confirm authorization rejection.
- Confirm retry creates tenant audit events for requested and accepted/rejected.
- Confirm no invoice, payment, inventory, or finance rows are created or modified by retry in Phase 4.

Phase 5 manual checks:
- Upload the same offline order twice with the same idempotency key and identical payload; confirm only one `c_{companyId}.pos_offline_order_import` row exists.
- Confirm the second identical upload returns the existing import status/result and includes an idempotent replay warning.
- Upload the same idempotency key with a changed payload; confirm `IDEMPOTENCY_PAYLOAD_MISMATCH`.
- Simulate two concurrent uploads with the same key/hash; confirm one idempotency row and one import row.
- Simulate a duplicate-key race; confirm the service re-reads the existing idempotency row and returns a safe result.
- Retry a `FAILED` import; confirm the existing idempotency row is reused and not duplicated.
- Retry an import whose payload hash differs from the idempotency record; confirm `IDEMPOTENCY_PAYLOAD_MISMATCH`.
- Confirm all idempotency reads/writes are in `c_{companyId}.pos_idempotency_key`, not `public.pos_idempotency_key`.
- Confirm no invoice, payment, inventory, or finance posting happens in Phase 5.

Phase 6 manual checks:
- Upload a batch and confirm imports are stored with Phase 5 idempotency protection.
- Invoke `PosOfflineSyncService.processPendingImports(companyId, branchId, batchId)` from an internal test/shell path and confirm eligible imports move from `PENDING` or `PENDING_RETRY` to `PROCESSING`, then `READY_FOR_VALIDATION`.
- Invoke `PosOfflineSyncService.processSingleImport(companyId, branchId, importId)` for `PROCESSING`, `READY_FOR_VALIDATION`, `SYNCED`, `DUPLICATE`, and `FAILED` imports and confirm no claim occurs.
- Simulate two workers trying to claim the same import and confirm only one receives the row from the conditional update.
- Simulate a processing exception and confirm the import becomes `FAILED`, a compact tenant error row is inserted, and an audit event is written.
- Simulate payload hash mismatch against the idempotency record and confirm `IDEMPOTENCY_PAYLOAD_MISMATCH` is handled safely.
- Confirm no invoice rows are created.
- Confirm no payment rows are created.
- Confirm no inventory rows are modified.
- Confirm no finance journal rows are created.
- Confirm all processing reads/writes happen in `c_{companyId}`, not `public`.

Phase 7 manual checks:
- Move an import to `READY_FOR_VALIDATION`, invoke `PosOfflineSyncService.validateSingleImport(companyId, branchId, importId)`, and confirm a valid import moves to `VALIDATED`.
- Invoke `PosOfflineSyncService.validateReadyImports(companyId, branchId, batchId)` and confirm one invalid import does not fail validation of the full batch.
- Validate import with empty lines and confirm `OFFLINE_ORDER_EMPTY_LINES` is written to tenant error rows.
- Validate import with unknown product and confirm `OFFLINE_PRODUCT_NOT_FOUND`.
- Validate import with product not available in branch and confirm `OFFLINE_PRODUCT_NOT_AVAILABLE_IN_BRANCH`.
- Validate import with invalid quantity and confirm `OFFLINE_INVALID_QUANTITY`.
- Validate import with negative or below-lowest unit price and confirm `OFFLINE_INVALID_PRICE`.
- Validate import with wrong line/order total and confirm `OFFLINE_TOTAL_MISMATCH`.
- Validate import with wrong payment total and confirm `OFFLINE_PAYMENT_TOTAL_MISMATCH`.
- Validate import with idempotency hash mismatch and confirm `IDEMPOTENCY_PAYLOAD_MISMATCH`.
- Confirm `PROCESSING`, `PENDING`, `PENDING_RETRY`, `SYNCED`, `DUPLICATE`, `FAILED`, and `VALIDATION_FAILED` imports are not claimed for validation.
- Confirm no invoice rows are created.
- Confirm no payment rows are created.
- Confirm no inventory rows are modified.
- Confirm no finance journal rows are created.
- Confirm all offline validation reads/writes happen in `c_{companyId}`, not `public.pos_*`.

Phase 8A design checks:
- Confirm no Phase 8A migration was added.
- Confirm no Phase 8A Java posting service was added.
- Confirm no code creates invoice rows from offline imports.
- Confirm no code creates payment rows from offline imports.
- Confirm no code modifies inventory rows from offline imports beyond the existing online POS path design review.
- Confirm no code creates finance journal rows from offline imports.
- Review the proposed Phase 8B transaction plan before approving implementation.

Future Phase 8B posting manual test plan:
- Prepare one `VALIDATED` import and invoke the future posting service; confirm only that import is claimed.
- Simulate two workers posting the same `VALIDATED` import; confirm one atomic claim succeeds and the other is skipped.
- Confirm successful posting writes exactly one online POS order in `c_{companyId}."PosOrder_{branchId}"`.
- Confirm successful posting writes matching details in `c_{companyId}."PosOrderDetail_{branchId}"`.
- Confirm branch stock and `inventory_stock_ledger` are updated exactly once.
- Confirm shift cash movement is written only for applicable cash/direct sales with an active shift.
- Confirm finance posting request or journal behavior matches the approved all-or-nothing decision.
- Confirm the import status becomes `SYNCED` only after all approved posting side effects succeed.
- Confirm duplicate posting attempts are rejected and logged as `OFFLINE_DUPLICATE_POSTING_ATTEMPT`.
- Confirm `pos_idempotency_key` result metadata stores the posted order id and finance reference if approved.

Phase 8B manual checks:
- Run Flyway migration V84 and confirm tenant `pos_offline_order_import` has `posting_started_at`, `posting_completed_at`, `posted_order_id`, `finance_posting_request_id`, `finance_journal_entry_id`, `posting_error_code`, and `posting_error_message`.
- Prepare one `VALIDATED` import and invoke `PosOfflineSyncService.postSingleImport(companyId, branchId, importId)`.
- Confirm import moves `VALIDATED -> POSTING -> SYNCED`.
- Confirm exactly one online POS order row is created in `c_{companyId}."PosOrder_{branchId}"`.
- Confirm matching detail rows are created in `c_{companyId}."PosOrderDetail_{branchId}"`.
- Confirm branch stock decreases exactly once for each inventory product line.
- Confirm `c_{companyId}.inventory_stock_ledger` has one `SALE_OUT` row per applicable line.
- Confirm legacy `c_{companyId}."InventoryTransactions_{branchId}"` behavior matches the existing online sale flow.
- Confirm shift cash movement is written only for direct/cash-style sale with an active shift.
- Confirm idempotency status becomes `SYNCED` and `official_order_id` contains the posted order id.
- Simulate two workers posting the same `VALIDATED` import and confirm only one atomic claim succeeds.
- Retry posting an import with existing posted metadata and confirm `OFFLINE_DUPLICATE_POSTING_ATTEMPT`.
- Post payload with decimal quantity and confirm `POSTING_FAILED` with `OFFLINE_DECIMAL_QUANTITY_NOT_SUPPORTED`.
- Post payload with decimal amount and confirm `POSTING_FAILED` with `OFFLINE_DECIMAL_AMOUNT_NOT_SUPPORTED`.
- Post payload with multiple payments and confirm `POSTING_FAILED` with `OFFLINE_MULTI_TENDER_NOT_SUPPORTED`.
- Simulate stock/update exception and confirm `POSTING_FAILED` plus tenant error row.
- Confirm no runtime read/write occurs against `public.pos_*`.

Phase 8C manual checks:
- Run V84/V85 migrations against a real PostgreSQL tenant schema.
- Confirm tenant `pos_sync_batch` has expanded counters: `pending_orders`, `pending_retry_orders`, `processing_orders`, `ready_for_validation_orders`, `validating_orders`, `validated_orders`, `posting_orders`, `posting_failed_orders`, and `validation_failed_orders`.
- Post one `VALIDATED` import and confirm one online order is created.
- Confirm branch stock decreases exactly once and `SALE_OUT` ledger rows are created exactly once.
- Confirm duplicate post attempt is blocked.
- Confirm posting failure marks import `POSTING_FAILED`.
- Invoke posting/validation/processing/retry service methods and confirm batch summary counters update.
- Confirm batch status is `IN_PROGRESS` while any import is non-terminal.
- Confirm batch status is `COMPLETED` only when every import is terminal and all are `SYNCED`.
- Confirm batch status is `COMPLETED_WITH_ERRORS` when terminal rows include at least one issue and at least one synced row.
- Confirm batch status is `FAILED` when terminal rows have failures/issues and no synced rows.
- Invoke `recoverStuckImports(companyId, branchId, batchId, thresholdMinutes)` and confirm old `POSTING` rows move to `NEEDS_REVIEW`, not retry/repost.
- Confirm no runtime access to `public.pos_*`.

Phase 8D manual checks:
- Run `mvnw.cmd "-Dtest=OfflineOrderPostingProcessorTest,PosOfflineSyncServiceOperationalTest" test` and confirm all focused offline tests pass.
- Run the SQL checks in `docs/OFFLINE_POS_MIGRATION_VALIDATION.md` against a real PostgreSQL database and tenant schema `c_1095`.
- Confirm V84 posting columns and V85 batch counter columns exist in tenant tables.
- Confirm status constraints include `POSTING`, `POSTING_FAILED`, and batch finalization statuses.
- Confirm a successful posting test creates exactly one online POS order and stores `posted_order_id`/`official_order_id`.
- Confirm decimal quantity still fails with `OFFLINE_DECIMAL_QUANTITY_NOT_SUPPORTED`.
- Confirm multi-tender payload still fails with `OFFLINE_MULTI_TENDER_NOT_SUPPORTED`.
- Confirm duplicate posted metadata still fails with `OFFLINE_DUPLICATE_POSTING_ATTEMPT`.
- Confirm future worker settings remain disabled by default in `application.properties`.
- Confirm no scheduler or public/admin endpoint triggers processing, validation, or posting automatically.
- Confirm focused search finds no runtime `public.pos_*` access in offline Java code.

Phase 9A manual checks:
- Confirm all worker properties default to disabled or safe values in `application.properties`.
- Start the app with default settings and confirm the offline worker skips because `valueinsoft.pos.offline.worker.enabled=false`.
- Set `valueinsoft.pos.offline.worker.enabled=true` with no `targets` and confirm the worker skips without scanning tenant schemas.
- Set `valueinsoft.pos.offline.worker.targets=1095:1` and `worker.enabled=true` only; confirm stuck recovery and summary recalculation may run, but processing, validation, and posting do not.
- Set `processing-enabled=true` and confirm only pending import processing runs for active batches in the configured target.
- Set `validation-enabled=true` and confirm validation runs only for ready imports.
- Keep `posting-enabled=false` and confirm no `VALIDATED` import is posted.
- In a controlled local environment only, set `posting-enabled=true` and confirm only `VALIDATED` imports are claimed for posting.
- Confirm old `POSTING` imports move to `NEEDS_REVIEW`, not automatic repost.
- Confirm malformed worker targets are ignored with compact warnings.
- Confirm no runtime access to `public.pos_*`.

Phase 9B manual checks:
- Run V86 and confirm `pos.offline.admin.process` exists in `public.platform_capabilities`.
- Confirm `pos.offline.admin.process` is not granted to Cashier.
- Call `POST /api/admin/pos/offline-sync/batches/{batchId}/recover-stuck?companyId=1095&branchId=1` without token and confirm rejection.
- Call each admin endpoint with Cashier and confirm rejection.
- Call each admin endpoint with Owner/Admin and correct branch and confirm allowed.
- Call each admin endpoint with wrong branch and confirm authorization rejection.
- Trigger recover-stuck and confirm old `POSTING` rows move to `NEEDS_REVIEW`.
- Trigger process and confirm only `PENDING`/`PENDING_RETRY` imports are processed.
- Trigger validate and confirm only `READY_FOR_VALIDATION` imports are validated.
- Trigger post and confirm only `VALIDATED` imports are posted.
- Trigger recalculate-summary and confirm tenant batch counters update.
- Confirm tenant audit rows are written for admin request events.
- Confirm responses are compact and contain no raw order payloads.
- Confirm no runtime access to `public.pos_*`.

Phase 9C manual checks:
- Call `POST /api/admin/pos/offline-sync/batches/{batchId}/post-preview?companyId=1095&branchId=1` without token and confirm rejection.
- Call post preview with Cashier and confirm rejection.
- Call post preview with Owner/Admin and correct branch and confirm allowed.
- Confirm post preview does not create online orders, decrement stock, change import statuses, or update idempotency rows.
- Confirm post preview returns `eligibleForPostingCount`, current failure counters, `skippedCount`, and warnings.
- Call actual admin post when `POSTING` rows exist and confirm the response includes a `POSTING` warning.
- Call actual admin post when `NEEDS_REVIEW` rows exist and confirm the response includes a `NEEDS_REVIEW` warning.
- Send `{ "reason": "manual controlled posting" }` to an admin operation and confirm tenant audit metadata includes the reason.
- Confirm richer responses include `batchStatus`, `postedCount`, `failedCount`, `validationFailedCount`, `postingFailedCount`, `needsReviewCount`, and `summaryRecalculated`.
- Confirm no runtime access to `public.pos_*`.

Phase 9D manual checks:
- Confirm `valueinsoft.pos.offline.admin.posting-enabled=false` by default.
- Confirm `valueinsoft.pos.offline.admin.max-post-batch-size=50` by default.
- Call post preview while admin posting is disabled and confirm it still returns counts/warnings without side effects.
- Call actual post while admin posting is disabled and confirm `accepted=false`, `summaryRecalculated=false`, and no posting occurs.
- Enable admin posting and call post without a nonblank `reason`; confirm `accepted=false` and no posting occurs.
- Call recover-stuck without a nonblank `reason`; confirm `accepted=false` and no recovery occurs.
- Create `POSTING` or `NEEDS_REVIEW` warning state and call post without `force=true`; confirm `accepted=false` and no posting occurs.
- Call post with `force=true` and a reason; confirm it proceeds only for `VALIDATED` imports.
- Create more than `max-post-batch-size` eligible `VALIDATED` rows and confirm post is rejected.
- Confirm process, validate, recalculate-summary, and post-preview do not require a reason.
- Confirm blocked post/recover operations write compact blocked audit events when tenant audit tables exist.
- Confirm no runtime access to `public.pos_*`.

Phase 9E manual checks:
- Call `GET /api/admin/pos/offline-sync/batches/{batchId}?companyId=1095&branchId=1` without token and confirm rejection.
- Call batch details with Cashier and confirm rejection.
- Call batch details with Owner/Admin and correct branch and confirm allowed.
- Call batch details with wrong branch and confirm authorization rejection.
- Confirm response includes `batchStatus`, timestamps, legacy counters, and import status counts.
- Confirm `readiness.canPost=false` and `ADMIN_POSTING_DISABLED` when admin posting is disabled.
- Confirm `readiness.canPost=false`, `POSTING_ROWS_EXIST`, and `requiresForceForPost=true` when `POSTING` rows exist.
- Confirm `readiness.canPost=false`, `NEEDS_REVIEW_ROWS_EXIST`, and `requiresForceForPost=true` when `NEEDS_REVIEW` rows exist.
- Confirm `readiness.canPost=false` and `MAX_POST_BATCH_SIZE_EXCEEDED` when eligible rows exceed the configured max.
- Confirm recent admin events include event type, created time, actor/reason when available, and blocked flag without raw payloads.
- Confirm error summary returns `totalErrors`, `latestErrorAt`, and grouped `topErrorCodes`.
- Confirm no runtime access to `public.pos_*`.

Phase 10B manual checks:
- Run Flyway migration V87 and confirm tenant `pos_offline_order_import` has `finance_enqueue_status` and `finance_enqueue_error`.
- Confirm tenant `pos_idempotency_key` has `result_metadata`.
- Post a `VALIDATED` import in controlled local/staging and confirm the online POS order is created.
- Confirm finance enqueue still runs after commit and remains non-blocking.
- Confirm `finance_posting_request_id` is populated when finance enqueue returns a request.
- Confirm the import remains `SYNCED` when finance request ID is unavailable.
- Simulate finance enqueue failure and confirm the import remains `SYNCED` with compact `finance_enqueue_status=ENQUEUE_FAILED`.
- Confirm idempotency `result_metadata` includes `postedOrderId`, `officialOrderId`, `financePostingRequestId` when available, and `financeStatus`.
- Confirm no raw offline order payloads are written to logs, audit metadata, idempotency metadata, or error metadata.
- Confirm no runtime access to `public.pos_*`.

Phase 10C manual checks:
- Call `GET /api/admin/pos/offline-sync/imports/{offlineOrderImportId}?companyId=1095&branchId=1` without token and confirm rejection.
- Call import details with Cashier and confirm rejection.
- Call import details with Owner/Admin and correct branch and confirm allowed.
- Call import details with wrong branch and confirm authorization rejection or safe not found.
- Confirm response includes status, timestamps, posting metadata, and finance enqueue metadata.
- Confirm response includes latest import error rows without raw values.
- Confirm response includes latest import-specific audit events without full audit payload JSON.
- Confirm response masks `idempotencyKey` and returns only a `payloadHashPrefix`.
- Confirm response does not include raw offline order payload JSON.
- Confirm long finance/import errors are truncated.
- Confirm no runtime access to `public.pos_*`.

Phase 10D manual UI checks:
- Open `/MainApp/{email}/{companyId}/{branchName}/OfflineSyncAdmin` as a user with `pos.offline.admin.process`.
- Load batch details by batch id and confirm status, counters, readiness, warnings, error summary, and recent admin events are visible.
- Confirm blocked reasons display when admin posting is disabled.
- Call post-preview and confirm the UI labels it read-only/no side effects.
- Try actual post without a reason and confirm the UI blocks confirmation before sending the operation.
- Try post with warning states and no force when force is required; confirm the UI shows blocked.
- Try post with force and reason only in a controlled environment; confirm details refresh after the backend response.
- Confirm `accepted=false` action responses are shown as blocked/no side effects, not as successful completion.
- Load import details by import id and confirm posting metadata, finance metadata, masked idempotency fields, errors, audit events, and online order reference display.
- Confirm the UI does not display raw offline order payload JSON.
- Confirm finance metadata displays when present.
- Confirm the Offline Sync Batches sidebar item is visible only through the admin capability-gated app shell.

Phase 10E manual API/UI checks:
- Call `GET /api/admin/pos/offline-sync/batches?companyId=1095&branchId=1` without token and confirm rejection.
- Call the list endpoint with Cashier and confirm rejection.
- Call the list endpoint with Owner/Admin and correct branch and confirm allowed.
- Call the list endpoint with wrong branch and confirm authorization rejection.
- Confirm the list returns only requested company/branch batches.
- Confirm `status=` filter works.
- Confirm `activeOnly=true` returns active batches when no status filter is supplied.
- Confirm cursor pagination returns `hasMore` and `nextCursor` without duplicate rows.
- Confirm no raw payload data is returned.
- Open Offline Sync Admin UI and confirm the batch table loads.
- Change status filter and refresh.
- Click a batch row and confirm batch details load.
- Confirm manual batch lookup still works.

## Compile/Test Result

- Phase 2: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 2: `mvnw.cmd test` failed during test compilation because existing finance tests still call old `DbFinanceSetup.resolveActiveAccountMapping(...)` and `FinanceAccountMappingItem(...)` signatures. This failure is unrelated to the offline POS Phase 2 changes and was already present after Phase 0 and Phase 1.
- Phase 3: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 4: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 5: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 6: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 7: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 7.5: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 7.5: No offline-specific tests were found under `src/test`, so targeted offline tests were not available.
- Phase 8A: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`. Only documentation was changed.
- Phase 8B: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 8C: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 8C: No offline-specific tests were found under `src/test`, so targeted automated offline tests were not available.
- Phase 8D: `mvnw.cmd compile test-compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 8D: `mvnw.cmd "-Dtest=OfflineOrderPostingProcessorTest,PosOfflineSyncServiceOperationalTest" test` passed with 7 tests, 0 failures, 0 errors.
- Phase 9A: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 9A: Broad tests were skipped per request.
- Phase 9B: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 9B: Broad tests were skipped per request.
- Phase 9C: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 9C: Broad tests were skipped per request.
- Phase 9D: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 9D: Broad tests were skipped per request.
- Phase 9E: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 9E: Broad tests were skipped per request.
- Phase 10B: `mvnw.cmd -DskipTests clean compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 10B: Broad tests were skipped per request.
- Phase 10C: `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 10C: Broad tests were skipped per request.
- Phase 10D: Frontend `npm run build` passed.
- Phase 10D: Backend compile was not rerun because no backend code changed in this phase.
- Phase 10E: Backend `mvnw.cmd compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Phase 10E: Frontend `npm run build` passed.
- Phase 10E: Broad tests were skipped per request.

## Phase 7.5 Verification Audit

### Migration Validation Result

- Reviewed `V77__create_pos_offline_sync_tenant_tables.sql` through `V83__pos_offline_order_validation.sql`.
- `V77` uses `format('%I', schema_name)`-style identifier quoting through `EXECUTE format(... %I ...)` and rejects schemas that do not start with `c_`.
- `CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, `DROP CONSTRAINT IF EXISTS`, and `CREATE INDEX IF NOT EXISTS` are used where needed.
- V77 creates tenant tables and runs `public.create_offline_sync_tables_for_tenant(schema_name)` for existing `c_%` schemas.
- V78 only registers capabilities and role grants in public authorization tables; it does not touch offline runtime data.
- V79 indexes tenant inventory tables used by bootstrap.
- V80, V82, and V83 rebuild `chk_order_import_status`; after the Phase 7.5 fix they all include the current status set.
- V81 rebuilds `chk_idempotency_status`; it includes all current idempotency statuses.
- No migration writes runtime offline POS rows to `public.pos_*`; V74/V75 public tables remain deprecated compatibility artifacts.
- Live PostgreSQL validation was not run because the Docker daemon is unavailable.

### Import Status Transition Table

| Status | Meaning | Set by | Allowed previous statuses | Allowed next statuses | Retry allowed | Validation allowed | Posting later |
|---|---|---|---|---|---|---|---|
| `PENDING` | Raw import stored and waiting for processing claim | `OfflineOrderImportRepository.insertImport` | New import | `PROCESSING`, `DUPLICATE`, `FAILED` | No | No | No |
| `PENDING_RETRY` | Manager retry accepted; ready to be claimed again | `markPendingRetry` | `FAILED`, `NEEDS_REVIEW` | `PROCESSING`, `FAILED` | No | No | No |
| `PROCESSING` | Import atomically claimed for skeleton processing | `claimNextPendingImport`, `claimImportForProcessing` | `PENDING`, `PENDING_RETRY` | `READY_FOR_VALIDATION`, `FAILED` | No | No | No |
| `READY_FOR_VALIDATION` | Processing boundary completed; ready for validation | `markReadyForValidation` | `PROCESSING` | `VALIDATING` | No | Yes | No |
| `VALIDATING` | Import atomically claimed for validation | `claimNextReadyForValidation`, `claimImportForValidation` | `READY_FOR_VALIDATION` | `VALIDATED`, `VALIDATION_FAILED` | No | In progress | No |
| `VALIDATED` | Order passed validation; not posted | `markValidated` | `VALIDATING` | Future posting status | No | No | Yes |
| `VALIDATION_FAILED` | Order failed validation | `markValidationFailed` | `VALIDATING` | Future review/retry path if approved | No currently | No | No |
| `SYNCED` | Reserved for future successful posting | Future posting phase | Future posting phase | Terminal or adjustment flow | No | No | Already posted |
| `FAILED` | Storage, processing, or system failure | `markFailed`, `markProcessingFailed` | `PENDING`, `PROCESSING`, future stages | `PENDING_RETRY` | Yes | No | No |
| `DUPLICATE` | Reserved for duplicate import semantics | Future duplicate handling | `PENDING` | Terminal | No | No | No |
| `NEEDS_REVIEW` | Reserved for manager review state | Future validation/review logic | Validation/processing phases | `PENDING_RETRY` | Yes | No | No |

### Idempotency Status Audit

- Enum and migration constraints include `RECEIVED`, `PROCESSING`, `SYNCED`, `FAILED`, `DUPLICATE`, `NEEDS_REVIEW`, and `PAYLOAD_MISMATCH`.
- Current upload path writes `RECEIVED`; mismatch path writes `PAYLOAD_MISMATCH`.
- Existing future helper methods write `SYNCED` and `FAILED`, both allowed by constraints.
- Duplicate same key/hash re-reads and returns existing import state.
- Duplicate same key/different hash returns `IDEMPOTENCY_PAYLOAD_MISMATCH`.
- Duplicate-key races are caught via `DuplicateKeyException` and resolved by re-reading the existing idempotency record.

### Tenant Isolation Audit

- Search for `public.pos_`, `FROM pos_`, `INTO pos_`, `UPDATE pos_`, and `DELETE FROM pos_` in runtime offline Java code found no runtime public/unqualified offline table access.
- Offline repositories use `TenantSqlIdentifiers.posXTable(companyId)` for offline runtime tables.
- Status, errors, and retry endpoints require `companyId` and `branchId`.
- Tenant queries keep `company_id` and `branch_id` predicates where relevant.

### Authorization Audit

- Every endpoint in `PosOfflineSyncController` accepts `Principal`.
- Null/blank principal is rejected with `UNAUTHENTICATED`.
- Every endpoint calls `AuthorizationService.assertAuthenticatedCapability(...)` before service execution.
- Final capabilities are `pos.device.register`, `pos.device.heartbeat`, `pos.bootstrap.read`, `pos.offline.sync`, `pos.offline.status`, `pos.offline.errors`, and `pos.offline.retry`.
- Retry requires `pos.offline.retry`; V78 grants it to Owner and BranchManager only.

### Bootstrap Audit

- PRODUCTS and PRICES join tenant `inventory_product` to tenant `inventory_branch_stock_balance` by `branch_id`.
- Pagination uses `product_id` cursor with `LIMIT pageSize + 1`.
- Page size is capped by `OfflinePosProperties.maxBootstrapPageSize`.
- Unsupported data types return `UNSUPPORTED_BOOTSTRAP_DATA_TYPE`.
- Invalid cursor returns `INVALID_BOOTSTRAP_CURSOR`.
- PAYMENT_METHODS, POS_SETTINGS, CASHIER_PERMISSIONS, TAXES, and DISCOUNTS remain compact defaults/placeholders.

### Validation Audit

- Validation does not call invoice, payment, inventory movement, or finance journal services.
- Product validation fetches all referenced product IDs/barcodes in one tenant query.
- Line/order/payment totals use `BigDecimal` with `0.01` tolerance.
- Phase 7.5 fixed quantity parsing so quantities preserve payload precision.
- Invalid orders write compact tenant error rows through `SyncErrorService`.
- Valid orders move only to `VALIDATED`.
- Batch validation loops one import per transaction and can continue after an invalid order.

### Bugs Fixed In Phase 7.5

- Fixed `V80__pos_offline_retry_baseline.sql` so its status check constraint includes `READY_FOR_VALIDATION`, `VALIDATING`, `VALIDATED`, and `VALIDATION_FAILED`.
- Fixed validation decimal parsing so quantities are not rounded to two decimals before total validation.

## Phase 8A Posting Design

### Existing Online POS Flow Findings

- Online sale endpoint: `POST /Order/{companyId}/saveOrder`.
- Controller capability: `pos.sale.create`.
- Main service: `OrderService.createOrder(CreateOrderRequest request, int companyId)`.
- Main repository: `DbPosOrder.addOrder(Order order, int companyId)`.
- Online order header table: `TenantSqlIdentifiers.orderTable(companyId, branchId)` -> `c_{companyId}."PosOrder_{branchId}"`.
- Online order detail table: `TenantSqlIdentifiers.orderDetailTable(companyId, branchId)` -> `c_{companyId}."PosOrderDetail_{branchId}"`.
- Inventory side effects:
  - Decrement `c_{companyId}.inventory_branch_stock_balance`.
  - Insert legacy row into `c_{companyId}."InventoryTransactions_{branchId}"`.
  - Insert modern stock ledger row into `c_{companyId}.inventory_stock_ledger` with movement type `SALE_OUT`.
- Shift/cash side effect:
  - For direct cash-style sales with active shift, insert `CASH_SALE` into `c_{companyId}.shift_cash_movement`.
- Finance side effect:
  - `OrderService` registers an after-commit callback to `FinanceOperationalPostingService.enqueuePosSale(...)`.
  - `FinanceOperationalPostingService` creates a finance posting request with `source_module = 'pos'`, `source_type = 'sale'`, and `source_id = 'order-{orderId}'`.
  - `FinancePosPostingAdapter` posts POS sale journals from the posting request and supports cash/card/wallet/receivable tender mappings plus inventory valuation lines.

### Offline Payload Mapping To Online Posting Model

Header mapping:
- `companyId`, `branchId`: from `pos_offline_order_import`, validated against tenant and branch.
- `offlineOrderNo`: preserve as offline reference; do not map to online `orderId`.
- `localOrderCreatedAt`: preferred online `orderTime` if approved; current online `DbPosOrder.addOrder` uses current server time, so Phase 8B must decide whether to add a timestamp-aware insert path.
- `customerId`: maps to online `clientId`; `localCustomer` may require a separate approved customer creation/matching phase.
- `saleType`: maps to online `orderType`; default should match existing direct sale semantics (`Direct`/`Dirict`) if absent.
- `subtotalAmount`, `discountAmount`, `taxAmount`, `totalAmount`: map to online `orderIncome`, `orderDiscount`, and `orderTotal` with integer compatibility handled explicitly.
- `cashierId`: maps to user context/sales user after resolving user name.
- `deviceId`: should remain offline audit metadata and not be forced into current online order tables unless a migration is approved.
- `localShiftId`: should map to active tenant shift only after local-to-server shift mapping is approved.

Line mapping:
- `productId` / `barcode`: resolve to tenant `inventory_product.product_id`.
- `productSnapshotName`: maps to online order detail `itemName`.
- `quantity`: maps to online detail `quantity`; current online table is integer, so decimal quantities require an explicit Phase 8B decision.
- `unitPrice`: maps to online detail `price`.
- `lineTotal`: maps to online detail `total`.
- `discountAmount`, `taxRate`, `taxAmount`: current online detail table has no explicit columns; keep in finance payload or add approved columns later.

Payment mapping:
- Offline `payments[]` should feed finance POS payload `payments[]`.
- Existing online operational sale path does not persist separate POS payment rows.
- Cash/direct sale should still create shift cash movement when an active shift exists.
- Multi-tender persistence requires either finance-only tender metadata or an approved operational POS payment table.

Offline/idempotency references:
- Store posted online identifiers on tenant `pos_offline_order_import` and/or `pos_idempotency_key` metadata.
- Recommended result metadata: `postedOrderId`, `postedInvoiceId` if introduced later, `financePostingRequestId`, `financeJournalEntryId` when available, `postedAt`.

### Proposed Transaction Boundary

- Process exactly one offline import per transaction.
- Atomic claim should update one tenant import from `VALIDATED` to `POSTING` and return the row.
- All approved operational posting side effects for that one order should occur inside the same transaction where feasible.
- If order insert, detail insert, inventory decrement, ledger insert, or required finance enqueue fails, rollback the order transaction and mark the import `POSTING_FAILED` in a separate failure-safe transaction.
- Do not process a whole batch in one transaction.
- Do not mark `SYNCED` until all approved posting side effects have succeeded.

### Proposed Status Model

Recommended additions for Phase 8B:
- `POSTING`: import is atomically claimed for final posting.
- `POSTING_FAILED`: posting attempted and failed after validation.

Recommended final success:
- Use existing `SYNCED` as final successful posting status.
- Do not add `POSTED` initially. `SYNCED` already represents that the offline order has been accepted and posted by the backend. Add `POSTED` only if product later needs a separate "posted but not acknowledged by device" state.

Proposed transition:
- `VALIDATED -> POSTING -> SYNCED`
- `VALIDATED -> POSTING -> POSTING_FAILED`
- `POSTING_FAILED -> PENDING_RETRY` only if a manager retry policy is approved for posting failures.

### Idempotency And Duplicate Posting Protection

- Posting claim must be conditional on `status = 'VALIDATED'`.
- Before posting, re-read and verify the tenant idempotency record for `company_id`, `branch_id`, `device_id`, and `idempotency_key`.
- If the idempotency status/result already contains a posted order id, block posting with `OFFLINE_DUPLICATE_POSTING_ATTEMPT`.
- If payload hash differs, mark `POSTING_FAILED` or `NEEDS_REVIEW` with `IDEMPOTENCY_PAYLOAD_MISMATCH`.
- After successful posting, update idempotency status to `SYNCED` and store compact result metadata containing posted references.
- Duplicate same-key uploads should continue returning the stored posted result instead of creating or posting another import.

### Error Handling Design

Future posting error codes:
- `OFFLINE_POSTING_FAILED`
- `OFFLINE_INVENTORY_POSTING_FAILED`
- `OFFLINE_PAYMENT_POSTING_FAILED`
- `OFFLINE_FINANCE_POSTING_FAILED`
- `OFFLINE_DUPLICATE_POSTING_ATTEMPT`
- `IDEMPOTENCY_PAYLOAD_MISMATCH`

Rules:
- Store compact errors through `SyncErrorService`.
- Keep historical validation and processing error rows.
- Do not log full raw order payload in audit/error messages.

### Audit Design

Future tenant audit events:
- `OFFLINE_POSTING_STARTED`
- `OFFLINE_POSTING_SUCCEEDED`
- `OFFLINE_POSTING_FAILED`
- `OFFLINE_POSTING_SKIPPED`
- `OFFLINE_DUPLICATE_POSTING_BLOCKED`

### Migration Design For Phase 8B

Likely tenant `pos_offline_order_import` columns:
- `posting_started_at TIMESTAMPTZ`
- `posting_completed_at TIMESTAMPTZ`
- `posted_order_id BIGINT`
- `posted_invoice_id BIGINT`
- `posted_payment_id BIGINT` or `posted_payment_ids JSONB` if multi-tender is supported
- `finance_posting_request_id UUID`
- `finance_journal_entry_id UUID`
- `posting_error_code VARCHAR(100)`
- `posting_error_message TEXT`

Likely indexes:
- `(company_id, branch_id, batch_id, status, id)`
- `(company_id, branch_id, posted_order_id) WHERE posted_order_id IS NOT NULL`
- `(company_id, branch_id, posting_started_at)`

Constraint update:
- Add `POSTING` and `POSTING_FAILED` to `chk_order_import_status`.
- No public `pos_*` table changes should be required.

### Proposed Phase 8B Implementation Plan

1. Add migration for posting columns, statuses, and tenant indexes.
2. Add `OfflineOrderPostingProcessor` with one-import transaction boundary.
3. Add tenant repository methods: claim validated import, mark posting failed, mark synced with posted references.
4. Add an offline-to-online mapper that converts validated payload into the current order model or a new internal command.
5. Prefer extracting online POS side effects from `OrderService.createOrder` into a reusable internal service before calling it from offline posting.
6. Decide and implement integer/decimal handling before writing online `PosOrderDetail` rows.
7. Integrate finance enqueue behavior according to the approved rollback policy.
8. Update idempotency result metadata only after successful posting.
9. Add manual and targeted integration tests around duplicate posting, inventory rollback, finance failures, and branch isolation.

### Phase 8A Decision

Phase 8B is not blocked by design, but it should not begin until these decisions are explicit:
- Whether offline posting must preserve `localOrderCreatedAt` as online order time.
- Whether decimal quantities/prices are supported in posted online POS tables or rejected before posting.
- Whether POS payments remain finance-payload-only or need operational payment persistence.
- Whether finance enqueue failure should rollback the offline order posting or leave a posted order with finance retry pending.
- Whether to extract a reusable online POS posting service before offline posting implementation.

## Phase 8B Posting MVP Implementation

### Implemented Posting Boundary

- Posting remains internal service-level only; no public posting endpoint or scheduler was added.
- `PosOfflineSyncService.postValidatedImports(companyId, branchId, batchId)` loops over `VALIDATED` imports and processes one import per transaction boundary through `OfflineOrderPostingProcessor`.
- `PosOfflineSyncService.postSingleImport(companyId, branchId, offlineOrderImportId)` posts one eligible import.
- `OfflineOrderPostingProcessor` atomically claims only `VALIDATED` imports by setting `POSTING`.
- If no import can be claimed, posting is skipped and audited without throwing.
- On success, the import is marked `SYNCED` and `posted_order_id`/`official_order_id` are set to the online POS order id.
- On failure, the import is marked `POSTING_FAILED`, posting error fields are set, and a compact tenant error row is written.

### Reused Online POS Sale Logic

- Added `PosSalePostingService` as the shared internal service for POS sale side effects.
- Offline posting calls `PosSalePostingService.postSale(...)`; it does not call `OrderController`.
- Online `OrderService.createOrder(...)` delegates to `PosSalePostingService` during normal dependency-injected runtime.
- `PosSalePostingService` performs the existing online side effects:
  - Inserts `c_{companyId}."PosOrder_{branchId}"`.
  - Inserts `c_{companyId}."PosOrderDetail_{branchId}"`.
  - Decrements `c_{companyId}.inventory_branch_stock_balance`.
  - Inserts legacy `c_{companyId}."InventoryTransactions_{branchId}"` rows.
  - Inserts modern `c_{companyId}.inventory_stock_ledger` `SALE_OUT` rows.
  - Inserts `CASH_SALE` shift cash movement for direct/cash sales when a shift is active.
  - Enqueues finance posting after commit using the existing online behavior.

### MVP Guards

- Decimal quantities fail with `OFFLINE_DECIMAL_QUANTITY_NOT_SUPPORTED`.
- Decimal prices, discounts, and totals fail with `OFFLINE_DECIMAL_AMOUNT_NOT_SUPPORTED`.
- Multiple payments fail with `OFFLINE_MULTI_TENDER_NOT_SUPPORTED`.
- Duplicate posting metadata fails with `OFFLINE_DUPLICATE_POSTING_ATTEMPT`.
- Payload hash mismatch still fails with `IDEMPOTENCY_PAYLOAD_MISMATCH`.

### Tenant Isolation Result

- V84 upgrades only tenant schemas matching `c_%`.
- Runtime posting uses tenant `pos_offline_order_import`, tenant idempotency, tenant audit/error tables, and existing tenant online POS/inventory tables.
- Focused search found no runtime `public.pos_*` access in the offline package or new posting code; V84 contains only documentation comments mentioning deprecated public compatibility tables.

### Status Transition Update

Posting additions:
- `VALIDATED -> POSTING -> SYNCED`
- `VALIDATED -> POSTING -> POSTING_FAILED`

`SYNCED` remains the final successful state for MVP.

### Phase 8B Limitations

- MVP does not persist separate operational POS payment rows.
- MVP does not return finance posting request or journal IDs because existing finance enqueue returns void.
- MVP does not preserve `localOrderCreatedAt` as online `orderTime`.
- MVP does not support decimal quantity or decimal money posting into current integer online POS tables.
- MVP does not finalize batch summary counts.

## Phase 8C Posting Hardening

### Batch Finalization

- Added V85 to expand tenant `pos_sync_batch` summary counters.
- Added `PosSyncBatchRepository.recalculateSummary(companyId, branchId, batchId)`.
- Recalculation derives counters from tenant `pos_offline_order_import` rows and updates:
  - Legacy counters: `total_orders`, `synced_orders`, `failed_orders`, `duplicate_orders`, `needs_review_orders`.
  - Expanded counters: pending, pending retry, processing, ready for validation, validating, validated, posting, posting failed, and validation failed.
- Batch status rules:
  - `IN_PROGRESS` if any import is still active/non-terminal.
  - `COMPLETED` if all imports are terminal and all are `SYNCED`.
  - `COMPLETED_WITH_ERRORS` if all imports are terminal and at least one issue exists with some synced rows.
  - `FAILED` if all imports are terminal, issues exist, and no rows synced.
  - `RECEIVED` if a batch has no imports.
- `sync_completed_at` is set only for terminal batches and cleared while active rows remain.

### Stuck-State Recovery Baseline

- Added `PosOfflineSyncService.recoverStuckImports(companyId, branchId, batchId, thresholdMinutes)`.
- Recovery is internal/service-level only.
- `PROCESSING` older than threshold moves to `FAILED`.
- `VALIDATING` older than threshold moves to `VALIDATION_FAILED`.
- `POSTING` older than threshold moves to `NEEDS_REVIEW`.
- `POSTING` rows are not automatically retried or reposted because online order, inventory, shift, or finance side effects may have partially completed.
- Recovery writes a tenant audit event summarizing recovered counts and recalculates the batch summary.

### Finance Visibility Review

- `FinancePostingRequestService.createPostingRequestFromSystem(...)` returns `FinancePostingRequestItem`.
- `FinanceOperationalPostingService.enqueuePosSale(...)` currently returns `void`.
- `PosSalePostingService` calls `enqueuePosSale(...)` after transaction commit to match the existing online POS behavior.
- Because the finance enqueue happens after commit and returns no value to the offline posting transaction, Phase 8C does not populate `finance_posting_request_id` or `finance_journal_entry_id`.
- Finance rollback semantics were not changed in Phase 8C.

### Verification Result

- `mvnw.cmd compile` passed.
- Focused search found no runtime offline `public.pos_*` access; the only matches are migration comments documenting deprecated public compatibility tables.
- No offline-specific automated tests were found under `src/test`.

## Phase 8D Automated Posting Tests and Operational Trigger Design

### Automated Posting Tests

- Added `OfflineOrderPostingProcessorTest` as focused service-level coverage using Mockito and the existing Spring/JUnit test stack.
- Covered posting skip when atomic claim returns empty.
- Covered successful posting from a claimed import, including `markPostingSynced(...)` and idempotency `markSynced(...)`.
- Covered online order mapping for integer-compatible payloads, including branch id, total, discount, quantity, and unit price.
- Covered duplicate posted metadata blocking with `OFFLINE_DUPLICATE_POSTING_ATTEMPT`.
- Covered decimal quantity rejection with `OFFLINE_DECIMAL_QUANTITY_NOT_SUPPORTED` and verified posting is not called.
- Covered multi-tender rejection with `OFFLINE_MULTI_TENDER_NOT_SUPPORTED` and verified posting is not called.
- Added `PosOfflineSyncServiceOperationalTest` to verify stuck recovery uses `markStuckPostingNeedsReview(...)`, recalculates batch summary, and writes the recovery audit event.
- Full database side-effect checks for order rows, order details, stock decrement, and `inventory_stock_ledger` still require a real PostgreSQL integration harness.

### Migration Validation Support

- Added `docs/OFFLINE_POS_MIGRATION_VALIDATION.md`.
- The guide includes PostgreSQL checks for tenant table creation, V84 posting columns, V85 batch counters, status constraints, indexes, and focused runtime `public.pos_*` access search.
- Live migration validation was documented but not executed in this phase.

### Disabled Worker Design

- Added disabled-by-default worker properties under `valueinsoft.pos.offline.worker.*`.
- Defaults:
  - `enabled=false`
  - `processing-enabled=false`
  - `validation-enabled=false`
  - `posting-enabled=false`
  - `batch-size=25`
  - `fixed-delay-ms=30000`
  - `stuck-threshold-minutes=15`
- Proposed future worker flow:
  - `recoverStuckImports(companyId, branchId, batchId, stuckThresholdMinutes)`
  - `processPendingImports(companyId, branchId, batchId)` only when processing is enabled
  - `validateReadyImports(companyId, branchId, batchId)` only when validation is enabled
  - `postValidatedImports(companyId, branchId, batchId)` only when posting is enabled
  - `recalculateSummary(companyId, branchId, batchId)`
- Automatic posting must require both global worker enablement and explicit posting enablement.
- No scheduler was implemented in Phase 8D.

### Admin Trigger Design

- No public or admin processing/posting endpoint was added.
- A future internal/admin trigger should require a high-privilege capability such as `pos.offline.admin.process`.
- Cashier capabilities and normal sync capabilities must not trigger processing, validation, posting, or stuck recovery.

## Phase 9A Controlled Offline Worker

### Worker Configuration

- Added `OfflinePosWorkerProperties` bound to `valueinsoft.pos.offline.worker`.
- Defaults remain safe:
  - `enabled=false`
  - `processing-enabled=false`
  - `validation-enabled=false`
  - `posting-enabled=false`
  - `batch-size=25`
  - `fixed-delay-ms=30000`
  - `stuck-threshold-minutes=15`
  - `targets=` empty
- `targets` is an explicit comma-separated allowlist using `companyId:branchId`, for example `1095:1,1095:2`.
- The worker does not discover tenants by scanning `c_%` schemas.

### Worker Flow

- Added `OfflinePosWorker`.
- The scheduled method exits immediately when `enabled=false`.
- If enabled with no targets, the worker skips and does not scan tenants.
- For each configured target, the worker selects active tenant batches through `PosSyncBatchRepository.findActiveBatchesForWorker(...)`.
- Active batch statuses are `RECEIVED`, `IN_PROGRESS`, `PROCESSING`, and `PARTIALLY_SYNCED`.
- Per batch flow:
  - `recoverStuckImports(...)`
  - `processPendingImports(...)` only when processing is enabled
  - `validateReadyImports(...)` only when validation is enabled
  - `postValidatedImports(...)` only when posting is enabled
  - `recalculateBatchSummary(...)`
- Exceptions are caught per batch so one bad batch does not stop the worker cycle.
- Logs are compact and do not include raw order payloads.
- Existing per-import transaction boundaries and atomic claim methods are reused.
- Stuck `POSTING` rows still move to `NEEDS_REVIEW`; the worker never automatically reposts them through recovery.

### Safety Result

- `worker.enabled=true` alone does not process, validate, or post imports.
- Posting requires `worker.enabled=true`, a configured target, and `posting-enabled=true`.
- No public/admin endpoint was added.
- No decimal posting support, multi-tender support, or finance behavior changes were added.

## Phase 9B Admin/Internal Operational Trigger

### Capability

- Added `V86__pos_offline_admin_capability.sql`.
- Registered `pos.offline.admin.process` with branch scope.
- Granted it only to high-privilege roles that exist in the installation:
  - `Owner` with company scope.
  - `Admin` with company scope.
  - `SupportAdmin` with global admin scope.
- Cashier is not granted this capability.

### Admin Controller

- Added `PosOfflineAdminController`.
- Base path: `/api/admin/pos/offline-sync`.
- Endpoints:
  - `POST /batches/{batchId}/recover-stuck?companyId=&branchId=&thresholdMinutes=`
  - `POST /batches/{batchId}/process?companyId=&branchId=`
  - `POST /batches/{batchId}/validate?companyId=&branchId=`
  - `POST /batches/{batchId}/post?companyId=&branchId=`
  - `POST /batches/{batchId}/recalculate-summary?companyId=&branchId=`
- Every endpoint requires `Principal`.
- Every endpoint calls `AuthorizationService.assertAuthenticatedCapability(...)` with `pos.offline.admin.process`.
- Null/blank principal is rejected with `UNAUTHENTICATED`.

### Operation Behavior

- Recover stuck calls `recoverStuckImports(...)`.
- Process calls `processPendingImports(...)`.
- Validate calls `validateReadyImports(...)`.
- Post calls `postValidatedImports(...)`.
- Recalculate summary calls `recalculateBatchSummary(...)`.
- Posting remains limited by the existing atomic `VALIDATED -> POSTING` claim.
- Stuck `POSTING` rows are not automatically retried or reposted.
- Responses use compact `OfflineAdminOperationResponse` and do not include raw payloads.

### Audit

- Added tenant audit events for admin requests:
  - `OFFLINE_ADMIN_RECOVER_STUCK_REQUESTED`
  - `OFFLINE_ADMIN_PROCESS_REQUESTED`
  - `OFFLINE_ADMIN_VALIDATE_REQUESTED`
  - `OFFLINE_ADMIN_POST_REQUESTED`
  - `OFFLINE_ADMIN_RECALCULATE_REQUESTED`
- Audit event messages include the principal name and no raw order payload.

## Phase 9C Admin Response Hardening and Dry Run Preview

### Rich Admin Responses

- Expanded `OfflineAdminOperationResponse` with:
  - `batchStatus`
  - `processedCount`
  - `postedCount`
  - `skippedCount`
  - `failedCount`
  - `validationFailedCount`
  - `postingFailedCount`
  - `needsReviewCount`
  - `eligibleForPostingCount`
  - `summaryRecalculated`
  - `warnings`
- Added `OfflineImportStatusCounts` and `PosSyncBatchRepository.findImportStatusCounts(...)`.
- Status counts are read from tenant `pos_offline_order_import` in one aggregate query.
- Admin responses derive summary counters after the operation so operators can see the current batch/import state.
- `processedCount` remains the direct count returned by the invoked service method.

### Posting Preview

- Added `POST /api/admin/pos/offline-sync/batches/{batchId}/post-preview?companyId=&branchId=`.
- The preview endpoint requires `pos.offline.admin.process`.
- Preview is read-only:
  - It does not create online POS orders.
  - It does not decrement stock.
  - It does not update import statuses.
  - It does not update idempotency rows.
- Preview reports current `VALIDATED` imports as `eligibleForPostingCount`.
- Preview reports skipped rows as total imports that are not currently `VALIDATED`.
- Preview warns when:
  - `POSTING` rows exist.
  - `NEEDS_REVIEW` rows exist.
  - No `VALIDATED` imports are eligible for posting.
  - Decimal quantity/amount and multi-tender checks will still run during actual posting.

### Admin Reason and Audit

- Added optional `OfflineAdminOperationRequest` with `reason`.
- Recover stuck, process, validate, post, and recalculate summary accept the optional reason body.
- Nonblank reasons are written to tenant audit metadata as compact JSON.
- Raw offline order payloads are never included in admin responses, logs, or audit metadata.
- Added preview audit event `OFFLINE_ADMIN_POST_PREVIEW_REQUESTED`.

### Posting Safety Result

- Actual admin post reads pre-operation counts before invoking posting.
- If `POSTING` or `NEEDS_REVIEW` rows exist, the operation response includes warnings.
- Warnings do not block posting of other eligible `VALIDATED` imports.
- Existing posting service protections remain unchanged: only `VALIDATED` imports can be atomically claimed for posting.
- No decimal posting support, multi-tender support, finance behavior change, public endpoint, or automatic posting enablement was added.

## Phase 9D Production Safety Guards and Force Confirmation

### Admin Posting Gate

- Added `OfflinePosAdminProperties` bound to `valueinsoft.pos.offline.admin`.
- Added disabled-by-default properties:
  - `valueinsoft.pos.offline.admin.posting-enabled=false`
  - `valueinsoft.pos.offline.admin.max-post-batch-size=50`
- Actual admin posting is rejected unless `posting-enabled=true`.
- Post preview remains available with admin capability even when actual posting is disabled.
- Worker posting remains controlled only by the existing worker flags and was not changed.

### Dangerous Operation Requirements

- Admin post now requires a nonblank `reason`.
- Admin recover-stuck now requires a nonblank `reason`.
- Process, validate, recalculate-summary, and post-preview keep reason optional.
- `OfflineAdminOperationRequest` now supports:
  - `reason`
  - `force`
- Reasons are stored only in audit metadata and are not logged with raw payloads.

### Force and Size Guards

- Actual admin post reads tenant import status counts before execution.
- If `POSTING` or `NEEDS_REVIEW` rows exist, actual post requires `force=true`.
- Without force, the response returns `accepted=false` and posting is not invoked.
- Force does not override existing atomic status claims; only `VALIDATED` rows can still be posted.
- If eligible `VALIDATED` rows exceed `max-post-batch-size`, actual post returns `accepted=false` and posting is not invoked.
- Blocked responses include current batch status, eligible count, failure counters, warnings, and `summaryRecalculated=false`.

### Blocked Audit Events

- Blocked actual posting writes `OFFLINE_ADMIN_POST_BLOCKED` when tenant audit is available.
- Blocked recover-stuck writes `OFFLINE_ADMIN_RECOVER_STUCK_BLOCKED` when tenant audit is available.
- Blocked audit metadata may include `reason` and `blockReason`.
- Audit write failures are caught and logged compactly so a missing tenant audit table does not hide the block response.

### Safety Result

- Admin posting is fail-closed by default.
- Post preview remains safe and read-only.
- No decimal posting support, multi-tender support, finance behavior change, automatic posting enablement, or public endpoint was added.

## Phase 9E Admin Batch Details and Action Readiness API

### Batch Details Endpoint

- Added `GET /api/admin/pos/offline-sync/batches/{batchId}?companyId=&branchId=`.
- The endpoint requires `Principal`.
- The endpoint requires `pos.offline.admin.process`.
- Branch/company access is checked through `AuthorizationService.assertAuthenticatedCapability(...)`.
- Response is read-only and does not include raw offline order payloads.

### Response Shape

- Added `OfflineAdminBatchDetailsResponse`.
- Response includes:
  - company and branch identifiers.
  - batch id and batch status.
  - `createdAt`, `receivedAt`, and `completedAt`.
  - tenant import status counts from `OfflineImportStatusCounts`.
  - legacy batch counters: total, synced, failed, duplicate, and needs review.
  - `eligibleForPostingCount`.
  - warnings.
  - readiness object.
  - recent admin events.
  - compact error summary.

### Readiness

- Added `OfflineAdminReadiness`.
- Readiness includes booleans for recover-stuck, process, validate, post, and recalculate-summary.
- Readiness includes blocked reason lists for recover, process, validate, and post.
- Post blocked reasons include:
  - `ADMIN_POSTING_DISABLED`
  - `NO_VALIDATED_IMPORTS`
  - `POSTING_ROWS_EXIST`
  - `NEEDS_REVIEW_ROWS_EXIST`
  - `MAX_POST_BATCH_SIZE_EXCEEDED`
- `requiresReasonForPost=true`.
- `requiresReasonForRecoverStuck=true`.
- `requiresForceForPost=true` when `POSTING` or `NEEDS_REVIEW` rows exist.
- Readiness is advisory only; mutation endpoints still enforce their own safety gates.

### Recent Admin Events

- Added `SyncAuditLogRepository.findRecentAdminEvents(...)`.
- The query is tenant-scoped and filtered by `sync_batch_id`.
- The query returns only admin audit events matching `OFFLINE_ADMIN_%`.
- Response includes event type, created time, actor parsed from event message when available, reason from audit metadata, and whether the event was blocked.
- Full audit payload JSON and raw offline order payloads are not exposed.

### Error Summary

- Added `OfflineOrderErrorRepository.summarizeErrorsByBatchId(...)`.
- Error summary joins tenant errors to tenant imports by batch id.
- Response includes total errors, latest error timestamp, and top error codes grouped by `error_code`.
- The query does not read raw order payload columns.

## Phase 10B Finance Request ID Capture

### Finance Enqueue Path

- `FinancePostingRequestService.createPostingRequestFromSystem(...)` already returns `FinancePostingRequestItem`.
- `FinanceOperationalPostingService.enqueuePosSale(...)` remains available as the existing void method for online POS callers.
- Added `FinanceOperationalPostingService.enqueuePosSaleAndReturnRequest(...)` for callers that need the created or replayed finance posting request metadata.
- Existing online POS behavior is preserved: finance enqueue still runs after commit, and enqueue exceptions are logged without rolling back the saved POS order.

### Offline Metadata Capture

- `PosSalePostingService.postSale(...)` now has an overload with optional after-commit finance success/failure callbacks.
- Offline posting uses the callback overload to capture finance enqueue metadata after the POS order transaction commits.
- When a finance posting request is available, `pos_offline_order_import.finance_posting_request_id` is updated with the returned request id.
- `finance_enqueue_status` is set to `ENQUEUED`, `UNAVAILABLE`, or `ENQUEUE_FAILED`.
- `finance_enqueue_error` stores only compact failure text when finance enqueue fails.
- Offline imports remain `SYNCED` even when finance request ID capture is unavailable or finance enqueue fails.

### Idempotency Metadata

- Added tenant `pos_idempotency_key.result_metadata`.
- Successful offline posting writes compact idempotency metadata:
  - `postedOrderId`
  - `officialOrderId`
  - `financePostingRequestId` when available
  - `financeStatus`
  - compact `financeError` only when enqueue fails
- Raw offline order payloads are not written to idempotency metadata.

### Migration

- Added `V87__pos_offline_finance_request_capture.sql`.
- V87 upgrades only tenant schemas matching `c_%`.
- V87 adds a status constraint for lightweight finance enqueue states.
- V87 adds a tenant index for lookup by `finance_posting_request_id`.
- No runtime access to `public.pos_*` was added.

### Visibility Result

- Batch-level admin details remain unchanged because they do not expose individual imports.
- Import-level finance visibility should be added through a future compact import details endpoint.
- `finance_journal_entry_id` remains unset in Phase 10B because journal creation happens later in the finance posting lifecycle.

## Phase 10C Admin Import-Level Details Endpoint

### Import Details Endpoint

- Added `GET /api/admin/pos/offline-sync/imports/{offlineOrderImportId}?companyId=&branchId=`.
- The endpoint requires `Principal`.
- The endpoint requires `pos.offline.admin.process`.
- Branch/company access is checked through `AuthorizationService.assertAuthenticatedCapability(...)`.
- The lookup filters by `offlineOrderImportId`, `company_id`, and `branch_id`.
- Missing or cross-branch imports return a safe `OFFLINE_IMPORT_NOT_FOUND` response.

### Response Shape

- Added `OfflineAdminImportDetailsResponse`.
- Response includes compact import status, timestamps, retry count, posting metadata, finance enqueue metadata, device/cashier identifiers, masked idempotency key, payload hash prefix, idempotency status, and sanitized idempotency result metadata.
- Response includes a lightweight online order reference when a posted/official order id exists.
- Response does not include raw offline order payload JSON.
- Long errors and metadata are truncated.

### Error and Audit History

- Added latest 10 tenant error rows for a single import through `OfflineOrderErrorRepository.findRecentImportErrors(...)`.
- Error rows include error id, code, compact message, and created time.
- Added latest 10 tenant audit events directly correlated by `offline_order_import_id` through `SyncAuditLogRepository.findRecentImportEvents(...)`.
- Audit event rows include event type, created time, parsed actor when available, and reason when available.
- Full audit payload JSON and raw offline order payloads are not exposed.

### Tenant Isolation Result

- Import details uses tenant `pos_offline_order_import`, tenant device, tenant idempotency, tenant error, and tenant audit tables.
- The import details query selects only required support-safe columns and does not read `payload_json`.
- No runtime access to `public.pos_*` was added.

## Phase 10D Offline Admin UI Screen

### App Shell Route

- Added frontend view id `OfflineSyncAdmin`.
- Added lazy route rendering in the existing `MainApp` shell.
- Added sidebar/settings navigation entry labeled Offline sync batches.
- Access is gated by the existing shell capability model with `pos.offline.admin.process`.

### Batch Operations UI

- Added manual batch id lookup as the initial fallback before Phase 10E added the safe batch-list endpoint.
- Batch details use `GET /api/admin/pos/offline-sync/batches/{batchId}?companyId=&branchId=`.
- The page shows batch status, timestamps, legacy counters, import status counts, readiness flags, blocked reasons, warnings, error summary, and recent admin events.
- Added action controls for recover stuck, process, validate, post preview, post, and recalculate summary.
- Dangerous actions open confirmation dialogs.
- Recover stuck requires a reason.
- Post calls post-preview before confirmation, requires a reason, and requires force confirmation when backend readiness says force is required.
- `accepted=false` responses are displayed as blocked/no-side-effect outcomes.

### Import Details UI

- Added manual import id lookup using `GET /api/admin/pos/offline-sync/imports/{offlineOrderImportId}?companyId=&branchId=`.
- Import details show compact status, timestamps, posting references, finance enqueue metadata, masked idempotency fields, latest errors, latest import audit events, and online order reference.
- The UI does not display raw offline order payload JSON.
- Long backend-provided messages are displayed in compact table/text areas and rely on backend truncation.

### Safety Result

- No backend posting behavior was added.
- Automatic posting remains disabled-by-default and backend-controlled.
- No decimal quantity/money support or multi-tender operational persistence was added.
- The UI uses only existing safe admin endpoints and does not invent batch discovery or raw payload endpoints.

## Phase 10E Admin Batch List Endpoint and UI Listing

### Batch List Endpoint

- Added `GET /api/admin/pos/offline-sync/batches?companyId=&branchId=&status=&activeOnly=&size=&cursor=`.
- The endpoint requires `Principal`.
- The endpoint requires `pos.offline.admin.process`.
- Branch/company access is checked through `AuthorizationService.assertAuthenticatedCapability(...)`.
- The lookup filters by `company_id` and `branch_id`.
- Optional `status` validates against `PosSyncBatchStatus`.
- Optional `activeOnly=true` filters to `RECEIVED`, `IN_PROGRESS`, `PROCESSING`, and `PARTIALLY_SYNCED` when no explicit status filter is supplied.

### Pagination and Response

- Added `OfflineAdminBatchListResponse`.
- Added `OfflineAdminBatchListItem`.
- Pagination is newest-first by `created_at DESC, id DESC`.
- Cursor is an opaque URL-safe encoded `createdAt|batchId` value.
- Page size is capped at 100.
- Response includes `hasMore` and `nextCursor`.
- Items include compact batch counters and warning count only.
- Raw offline order payloads and import payloads are not selected or returned.

### UI Listing

- Offline Sync Admin now auto-loads a recent batch table for the active company/branch.
- Added status filter, active-only filter, refresh button, and load-more button.
- Clicking a batch row loads the existing batch details panel.
- Manual batch id lookup remains available as fallback.
- Dangerous operation behavior from Phase 10D is unchanged.

### Safety Result

- No backend posting behavior was added.
- No automatic posting behavior was added.
- No decimal quantity/money support or multi-tender operational persistence was added.
- Batch list uses only tenant `c_{companyId}.pos_sync_batch` summary data.

## Phase 10F Admin Import List Endpoint and UI Listing

### Import List Endpoint

- Added `GET /api/admin/pos/offline-sync/batches/{batchId}/imports?companyId=&branchId=&status=&errorCode=&size=&cursor=`.
- The endpoint requires `Principal`.
- The endpoint requires `pos.offline.admin.process`.
- Branch/company access is checked through `AuthorizationService.assertAuthenticatedCapability(...)`.
- The lookup filters by `company_id`, `branch_id`, and `sync_batch_id`.
- Optional `status` validates against `OfflineOrderImportStatus`.
- Optional `errorCode` filters by string matches.

### Pagination and Response

- Added `OfflineAdminImportListResponse`.
- Added `OfflineAdminImportListItem`.
- Pagination is newest-first by `created_at DESC, id DESC`.
- Cursor is an opaque URL-safe encoded `createdAt|importId` value.
- Page size is capped at 100.
- Response includes `hasMore` and `nextCursor`.
- Items include compact import statuses, error codes, finance enqueue status, timestamps, and order identifiers.
- Raw offline order payloads and import payloads are not selected or returned.

### UI Listing

- Offline Sync Admin now loads an imports table nested under the Batch Details panel.
- Added import status filter, error code filter, refresh button, and cursor-based load-more button.
- Clicking an import row seamlessly selects it and loads the existing import details panel automatically.
- Treated `accepted=false` backend responses as blocked/no-side-effect, not success.
- Finance metadata, posting metadata, error code, retry count, and status clearly render in the table list view.
- Manual import id lookup remains available as fallback.

### Safety Result

- No backend posting behavior was added.
- No automatic posting behavior was added.
- No decimal quantity/money support or multi-tender operational persistence was added.
- Batch list uses only tenant `c_{companyId}.pos_offline_order_import` summary data.

## Phase 10G Offline Cashier Frontend Foundation

### Offline Status & Storage
- Added `useOfflineStatus.js` hook listening to `window` `online`/`offline` events.
- Added `dexie` to frontend dependencies for cross-browser IndexedDB abstraction.
- Added `offlinePosDb.js` defining local tables for products, prices, bootstrap state, device state, and the order queue.
- IndexedDB table designs enforce `companyId` and `branchId` to prevent multi-tenant bleed if a single browser handles multiple locations.
- Sensitive auth tokens are consciously excluded from IndexedDB storage.

### Bootstrap Data Synchronization
- Added `offlineSyncApi.js` wrapper around existing POS GET `bootstrap-data` endpoints.
- Added `offlineDataSyncService.js` to coordinate paginated fetch of `PRODUCTS` and `PRICES`.
- The synchronization safely deletes previous branch data, downloads new pages concurrently, and stores them in IndexedDB.
- The service tracks `lastBootstrapAt`, item counts, and status in `offline_bootstrap_state`.

### UI Integration & Fallbacks
- Added `OfflinePosReadinessPanel.js` showing online/offline status, downloaded row counts, pending queue size, and a manual sync button.
- Cleanly integrated the readiness panel into `PosSalesPage.js` above the main POS tabs without disrupting existing workflows.
- Added `searchOfflineProducts` utility to query IndexedDB locally.
- Updated `usePosCatalog.js` to branch `searchProductsByName` and `searchProductsByBarcode` to hit IndexedDB safely when `isOffline === true`.

### Safety Result
- No backend endpoints were modified.
## Phase 10J Backend Status Polling and Local Queue Result Mapping

### Backend Status Mapping
- Added `fetchOfflineBatchStatus` to `offlineSyncApi.js` querying `GET /api/pos/offline-sync/status/{batchId}`.
- Added `pollQueuedOfflineOrderStatuses` to `offlineDataSyncService.js`.
- The poller securely targets records marked as `BACKEND_RECEIVED`, `BACKEND_PROCESSING`, or `BACKEND_VALIDATED` that contain a valid numeric `backendBatchId`.
- Safely mapped the batch-level `backendStatus` into the local IndexedDB entries.
- Adhered strictly to the batch-level restriction—no individual local orders are falsely presumed final, preserving raw batch insight.

### UI Enhancements
- Deployed a "Refresh Sync Status" button inside the `OfflinePosReadinessPanel.js` queue modal.
- Configured dynamic column visibility to explicitly expose `Backend Status` alongside formatted `Poll Time`.
- Added interactive badge variants (`primary` for processing, `success` for completed, `danger` for failed) based on the exact batch DTO enumeration `PosSyncBatchStatus`.
- Clarified limitation: Added footer note informing users that "Individual order completion states are managed by backend batch summaries".

### Safety Focus
- If the endpoint errors or rejects the fetch, the UI fails gracefully. The localized state is preserved securely, logging a compact payload inside `lastSyncError`.
- No raw debug payloads were ever dumped to the DOM.
- Kept the cashier safely partitioned away from protected `admin` capabilities.

## Remaining TODOs

- Later: Add an approved data migration/backfill from `public.pos_*` to tenant `c_{companyId}.pos_*` if existing public data must be retained.
- Later: Resolve `registered_by` from principalName to numeric user id for `pos_device.registered_by`.
- Later: Replace Phase 3 placeholder/default sources for payment methods, POS settings, cashier permissions, taxes, and discounts with final tenant configuration tables once approved.
- Later: Add reconciliation for idempotency claims that remain `RECEIVED` without a visible import due to unexpected storage failures.
- Later: Establish broad End-to-End automatic testing via Testcontainers and Selenium across all offline lifecycles.

## Phase 10K Cashier Per-Order Sync Result Endpoint

### Backend Enhancements
- Added cashier-safe endpoint `GET /api/pos/offline-sync/status/{batchId}/orders`.
- Implemented `OfflineBatchOrderResultsResponse` and `OfflineBatchOrderResultItem` DTOs.
- Secured endpoint with `pos.offline.status` capability check and tenant-scoped validation.
- Added `findBatchOrderResults` to `OfflineOrderImportRepository` using `TenantSqlIdentifiers` for safe multi-tenant access.
- Excluded raw `payload_json` from the response while providing full status, error codes, and official order identifiers.

### Frontend Synchronization Logic
- Added `fetchOfflineOrderResults` to `offlineSyncApi.js`.
- Upgraded `pollQueuedOfflineOrderStatuses` in `offlineDataSyncService.js` to perform deep per-order matching.
- Match logic prioritizes `localOrderId` (`offlineOrderNo`) and falls back to `idempotencyKey`.
- Successfully mapped backend lifecycle statuses (e.g., `SYNCED`, `VALIDATION_FAILED`, `POSTING_FAILED`) to final local states.
- Persisted crucial backend metadata locally, including `backendImportId`, `postedOrderId`, and specific `errorCode` details.

### UI Improvements
- Enhanced the Queue Modal in `OfflinePosReadinessPanel.js` to display granular per-order outcomes.
- Configured dynamic status badges: `success` for `SYNCED`, `danger` for failed states, and `info` for in-progress backend tasks.
- Added detailed "Order Info / Error" column to show official IDs for synced orders and descriptive error messages for failures.

### Safety Result
- Cashier-side UI strictly uses non-admin endpoints.
- No raw order payloads are exposed in the status results.
- Robust matching prevents duplicate local records and ensures accurate status propagation.

## Remaining TODOs

- Later: Add background automatic sync worker (Phase 10L).
- Later: Add an approved data migration/backfill from `public.pos_*` to tenant `c_{companyId}.pos_*` if existing public data must be retained.
- Later: Resolve `registered_by` from principalName to numeric user id for `pos_device.registered_by`.
- Later: Replace Phase 3 placeholder/default sources for payment methods, POS settings, cashier permissions, taxes, and discounts with final tenant configuration tables once approved.
- Later: Add reconciliation for idempotency claims that remain `RECEIVED` without a visible import due to unexpected storage failures.
- Later: Establish broad End-to-End automatic testing via Testcontainers and Selenium across all offline lifecycles.

## Next Recommended Phase

## Phase 10L Persist Offline Order Reference Columns

### Database Migrations
- Added `V88__pos_offline_import_reference_columns.sql`.
- Provisioned `local_order_id`, `device_code`, and `client_created_at` columns in `pos_offline_order_import` for all tenant schemas.
- Relaxed `cashier_id` constraint to allow `NULL` (supporting scenarios where cashier info might be missing from legacy or specific client types).
- Optimized lookups with composite indexes on `(sync_batch_id, local_order_id)` and `(device_code, local_order_id)`.

### Backend Implementation
- **DTOs**: Updated `OfflineOrderRequest` to include `localOrderId`, `deviceCode`, `clientCreatedAt`, and `cashierId`.
- **Repository**: Updated `OfflineOrderImportRepository` to persist these reference columns during `insertImport`.
- **Status Mapping**: Enhanced `findBatchOrderResults` and `OfflineBatchOrderResultItem` to return `localOrderId` directly from the column, eliminating the need to parse `payload_json` for status identification.
- **Admin Visibility**: Updated admin list and detail responses to include new reference fields, improving operational traceability.

### Frontend Synchronization Logic
- Updated `offlineDataSyncService.js` to populate `localOrderId`, `clientCreatedAt`, and `deviceCode` during upload.
- Improved status matching logic to utilize the new `localOrderId` column returned from the backend, with fallback to legacy `offlineOrderNo` and `idempotencyKey` matching.

### Safety & Health
- **Tenant Isolation**: All operations use `TenantSqlIdentifiers` and strictly target `c_{companyId}` schemas.
- **Compatibility**: Maintained support for older imports where reference columns might be `NULL`.
- **Build**: Frontend production build (`npm run build`) passed successfully.

Phase 10M - Offline Device Registration UX

### Frontend Implementation
- **Stable Identity**: Implemented `useOfflineDeviceState` hook to generate and persist a stable `deviceCode` (e.g., `POS-{companyId}-{branchId}-{random}`) in IndexedDB.
- **Registration Flow**: Added "Register Device" functionality to `OfflinePosReadinessPanel` calling `POST /api/pos/device/register`.
- **Heartbeat**: Added manual "Send Heartbeat" action to update `lastHeartbeatAt` via `POST /api/pos/device/heartbeat`.
- **Sync Blocking**: Updated `offlineDataSyncService.js` to block manual sync uploads if the device is not registered, ensuring data integrity on the backend.
- **Idempotency**: Promoted `deviceCode` to a first-class property in the offline order queue, ensuring `idempotencyKey` remains consistent across page reloads.

### Affected Files
- `src/domains/pos-offline/hooks/useOfflineDeviceState.js` (New)
- `src/domains/pos-offline/api/offlineSyncApi.js`
- `src/domains/pos-offline/services/offlineDataSyncService.js`
- `src/domains/pos-offline/components/OfflinePosReadinessPanel.js`
- `src/PointOfSale/Componens/PayStatment.js`

### Manual UI Checks
- [x] Stable `deviceCode` generated once and persists across reloads.
- [x] "Register Device" button successfully calls backend and updates local state with `deviceId`.
- [x] "Send Heartbeat" updates `lastHeartbeatAt` and communicates with backend.
- [x] Sync is blocked with a clear error message if device is unregistered.
- [x] Offline orders are saved with the registered `deviceCode`.
- [x] Online checkout remains unaffected.

### Next Recommended Phase
Phase 10N - Background Automatic Sync and Network Reconnection Handling.

Recommended implementation shape:
- Implement background automatic upload polling upon detecting `window.online` restoration.
- Debounce and limit background sync attempts.
- Add notification hooks for successful background pushes.

