# Offline POS Implementation Checklist

## Current Phase Status

Phase 7.5 - Verification, Migration Validation, and Integration Audit: completed on 2026-05-03.

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
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/PosIdempotencyRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/OfflineOrderErrorRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/BootstrapVersionRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/SyncAuditLogRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/config/OfflinePosProperties.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/controller/PosOfflineSyncController.java`
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
- `src/main/java/com/example/valueinsoftbackend/pos/offline/enums/OfflineOrderImportStatus.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/enums/PosIdempotencyStatus.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/model/OfflineValidationProductSnapshot.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/repository/OfflineOrderValidationRepository.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/IdempotencyClaimResult.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/OfflineSingleOrderProcessor.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/OfflineOrderImportValidationService.java`
- `src/main/java/com/example/valueinsoftbackend/pos/offline/service/OfflineOrderValidationProcessor.java`
- `src/main/resources/application.properties`
- `src/main/resources/db/migration/V77__create_pos_offline_sync_tenant_tables.sql`
- `src/main/resources/db/migration/V78__pos_offline_sync_capabilities.sql`
- `src/main/resources/db/migration/V79__pos_offline_bootstrap_indexes.sql`
- `src/main/resources/db/migration/V80__pos_offline_retry_baseline.sql`
- `src/main/resources/db/migration/V81__pos_offline_idempotency_hardening.sql`
- `src/main/resources/db/migration/V82__pos_offline_processing_skeleton.sql`
- `src/main/resources/db/migration/V83__pos_offline_order_validation.sql`
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

## Remaining TODOs

- Later: Add an approved data migration/backfill from `public.pos_*` to tenant `c_{companyId}.pos_*` if existing public data must be retained.
- Later: Resolve `registered_by` from principalName to numeric user id for `pos_device.registered_by`.
- Later: Replace Phase 3 placeholder/default sources for payment methods, POS settings, cashier permissions, taxes, and discounts with final tenant configuration tables once approved.
- Later: Add reconciliation for idempotency claims that remain `RECEIVED` without a visible import due to unexpected storage failures.
- Later: Add a scheduler/admin trigger for processing if approved.
- Later: Add a scheduler/admin trigger for validation if approved.
- Later phases: Posting and batch finalization.

## Next Recommended Phase

Phase 8 - Posting Design/Implementation, only after explicit approval.

Recommended implementation shape:
- Design the posting boundary from `VALIDATED` imports.
- Keep every order in its own transaction boundary.
- Decide exact integration points for invoice, payment, inventory, and finance services.
- Do not start posting until explicitly approved.
