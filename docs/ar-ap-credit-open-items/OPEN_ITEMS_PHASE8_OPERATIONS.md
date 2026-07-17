# Open Items Phase 8 Operations

## Query-plan audit

Run `src/test/resources/db/verification/open_items_phase8_explain.sql` with `psql` against a disposable, fully migrated PostgreSQL database. It inserts 100,000 AR and 100,000 AP rows inside a transaction, runs `ANALYZE`, prints `EXPLAIN (ANALYZE, BUFFERS)` for list, aging, exposure, and reconciliation queries, then rolls everything back.

Acceptance criteria:

- aging uses `idx_*_ar_oi_client_status_due` / `idx_*_ap_oi_supplier_status_due`;
- exposure does not use `Seq Scan`;
- list responses remain bounded to 200 rows;
- record execution time and shared-buffer reads before adding any covering index.

No Phase 8 index migration is included until this audit proves an index is needed. This avoids slowing the allocation write path based only on speculation.

## Concurrency and throughput soak

The Docker-tagged `OpenItemsMigrationIT` contains the opt-in 50-writer/10-item deadlock soak and the 1,000-receipt FIFO soak. Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd --% -Dtest=OpenItemsMigrationIT -Dsurefire.excludedGroups= -Dgroups=postgres -Dopenitems.soak.enabled=true test
```

Acceptance criteria: all allocations succeed, final settlement totals reconcile, and `pg_stat_database.deadlocks` does not increase.

## Metrics and alerts

Enable periodic tenant drift refresh with `VLS_OPENITEMS_MONITORING_ENABLED=true`. Metrics are exposed at `/actuator/prometheus` and must be restricted at the ingress/network layer.

- `valueinsoft_openitems_allocation_latency_seconds`
- `valueinsoft_openitems_trigger_rejections_total`
- `valueinsoft_openitems_idempotency_replays_total`
- `valueinsoft_openitems_reconciliation_drift{company_id,side}`

Import `ops/monitoring/open-items-alerts.yml` into Prometheus. Any trigger rejection is actionable; any reconciliation drift persisting for five minutes blocks rollout.
