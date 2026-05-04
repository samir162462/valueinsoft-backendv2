# Offline POS Migration Validation

This guide validates tenant offline POS migrations against a real PostgreSQL database. It is intended for local QA or staging before enabling any operational worker.

## Prerequisites

- PostgreSQL database reachable by the backend.
- Backend configured with the target datasource credentials.
- Java 21 available through `JAVA_HOME`.
- A test company id such as `1095`, which maps to tenant schema `c_1095`.

## Fresh Database Validation

1. Start PostgreSQL and create an empty database for migration validation.
2. Point `spring.datasource.url`, `spring.datasource.username`, and `spring.datasource.password` to that database.
3. Run Flyway through the application startup or the existing Maven lifecycle used by the project.
4. Confirm the tenant helper exists:

```sql
SELECT proname
FROM pg_proc
WHERE proname = 'create_offline_sync_tables_for_tenant';
```

5. Create or upgrade a tenant schema:

```sql
CREATE SCHEMA IF NOT EXISTS c_1095;
SELECT public.create_offline_sync_tables_for_tenant('c_1095');
SELECT public.ensure_offline_sync_posting_mvp_for_tenant('c_1095');
SELECT public.ensure_offline_sync_batch_finalization_for_tenant('c_1095');
```

The V77 tenant creation helper already creates the current retry, processing, validation, and idempotency baseline columns/statuses for new tenant schemas. V84 and V85 then add the posting and batch-finalization additions that are exposed as reusable helper functions.

6. Verify tenant offline tables exist:

```sql
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema = 'c_1095'
  AND table_name IN (
    'pos_device',
    'pos_sync_batch',
    'pos_offline_order_import',
    'pos_idempotency_key',
    'pos_offline_order_error',
    'pos_bootstrap_version',
    'pos_device_session',
    'pos_sync_audit_log'
  )
ORDER BY table_name;
```

7. Verify V84 posting columns exist:

```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'c_1095'
  AND table_name = 'pos_offline_order_import'
  AND column_name IN (
    'posting_started_at',
    'posting_completed_at',
    'posted_order_id',
    'finance_posting_request_id',
    'finance_journal_entry_id',
    'posting_error_code',
    'posting_error_message'
  )
ORDER BY column_name;
```

8. Verify V85 batch counter columns exist:

```sql
SELECT column_name
FROM information_schema.columns
WHERE table_schema = 'c_1095'
  AND table_name = 'pos_sync_batch'
  AND column_name IN (
    'pending_orders',
    'pending_retry_orders',
    'processing_orders',
    'ready_for_validation_orders',
    'validating_orders',
    'validated_orders',
    'posting_orders',
    'posting_failed_orders',
    'validation_failed_orders'
  )
ORDER BY column_name;
```

9. Verify status constraints include the current values:

```sql
SELECT conname, pg_get_constraintdef(c.oid)
FROM pg_constraint c
JOIN pg_class t ON t.oid = c.conrelid
JOIN pg_namespace n ON n.oid = t.relnamespace
WHERE n.nspname = 'c_1095'
  AND t.relname IN ('pos_offline_order_import', 'pos_sync_batch', 'pos_idempotency_key')
  AND conname LIKE 'chk_%'
ORDER BY t.relname, conname;
```

Expected import statuses include `PENDING`, `PENDING_RETRY`, `PROCESSING`, `READY_FOR_VALIDATION`, `VALIDATING`, `VALIDATED`, `VALIDATION_FAILED`, `POSTING`, `POSTING_FAILED`, `SYNCED`, `FAILED`, `DUPLICATE`, and `NEEDS_REVIEW`.

Expected batch statuses include `RECEIVED`, `PROCESSING`, `IN_PROGRESS`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, `FAILED`, `PARTIAL`, and `REJECTED`.

10. Verify posting and summary indexes exist:

```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'c_1095'
  AND tablename IN ('pos_offline_order_import', 'pos_sync_batch')
  AND (indexname LIKE '%posting%' OR indexname LIKE '%summary%' OR indexname LIKE '%status%')
ORDER BY tablename, indexname;
```

## Existing Database With V74/V75 Public Tables

Run the same validation on a database that already contains deprecated `public.pos_*` compatibility tables. V77 through V85 must create or upgrade tenant `c_%` tables without copying, deleting, or depending on public offline runtime rows.

Confirm normal runtime verification queries use `c_1095.pos_*` tables. Public `pos_*` tables are compatibility artifacts only.

## Application Verification Commands

Compile:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd compile
```

Targeted offline posting unit tests:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd -Dtest=OfflineOrderPostingProcessorTest test
```

Focused public-table access search:

```powershell
rg -n "public\.pos_|FROM pos_|INTO pos_|UPDATE pos_|DELETE FROM pos_" src/main/java/com/example/valueinsoftbackend/pos/offline src/main/java/com/example/valueinsoftbackend/Service/PosSalePostingService.java
```

Expected result: no runtime Java access to `public.pos_*` or unqualified offline `pos_*` tables.
