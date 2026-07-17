-- Phase 8.1 PostgreSQL plan audit. Run with psql on a disposable migrated database.
-- Required variables, for example:
--   psql "$DATABASE_URL" -v tenant_schema=c_7 -v company_id=7 -v branch_id=3 \
--     -v client_id=11 -v supplier_id=4 -f open_items_phase8_explain.sql
-- The selected client and supplier must exist. All seeded rows are rolled back.

\set ON_ERROR_STOP on
BEGIN;

-- Keep the audited party selective (1% of the 100k rows), otherwise PostgreSQL
-- correctly prefers a sequential scan even when the target index exists.
INSERT INTO :"tenant_schema"."Client" ("clientName", "branchId", "registeredTime")
VALUES ('__PHASE8_PLAN_AUDIT_OTHER__', :branch_id, CURRENT_TIMESTAMP)
RETURNING c_id AS other_client_id \gset

INSERT INTO :"tenant_schema".ar_open_item
    (company_id, branch_id, client_id, source_type, document_ref, document_date,
     due_date, currency_code, total_amount, settled_amount, remaining_amount, status, idempotency_key)
SELECT :company_id, :branch_id,
       CASE WHEN g % 100 = 0 THEN :client_id ELSE :other_client_id END,
       'ADJUSTMENT', 'P8-AR-' || g,
       CURRENT_TIMESTAMP - ((g % 365) || ' days')::interval,
       CURRENT_TIMESTAMP - ((g % 180) || ' days')::interval,
       'EGP', 10, 0, 10, 'OPEN', 'phase8-ar-' || g
FROM generate_series(1, 100000) g;

INSERT INTO :"tenant_schema".ap_open_item
    (company_id, branch_id, supplier_id, source_type, document_ref, document_date,
     due_date, currency_code, total_amount, settled_amount, remaining_amount, status, idempotency_key)
SELECT :company_id, :branch_id,
       CASE WHEN g % 100 = 0 THEN :supplier_id ELSE :supplier_id + 100000000 END,
       'ADJUSTMENT', 'P8-AP-' || g,
       CURRENT_TIMESTAMP - ((g % 365) || ' days')::interval,
       CURRENT_TIMESTAMP - ((g % 180) || ' days')::interval,
       'EGP', 10, 0, 10, 'OPEN', 'phase8-ap-' || g
FROM generate_series(1, 100000) g;

ANALYZE :"tenant_schema".ar_open_item;
ANALYZE :"tenant_schema".ap_open_item;

-- Open-item list. Expected: bounded index-assisted scan, never an unbounded result.
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM :"tenant_schema".ar_open_item
 WHERE company_id=:company_id AND branch_id=:branch_id AND client_id=:client_id
 ORDER BY document_date DESC, open_item_id DESC LIMIT 200;

-- AR due-date aging. Expected index: idx_*_ar_oi_client_status_due.
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT currency_code, SUM(remaining_amount)
  FROM :"tenant_schema".ar_open_item
 WHERE company_id=:company_id AND branch_id=:branch_id AND client_id=:client_id
   AND status IN ('OPEN','PARTIALLY_SETTLED') AND due_date <= CURRENT_DATE
 GROUP BY currency_code;

-- AP due-date aging. Expected index: idx_*_ap_oi_supplier_status_due.
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT currency_code, SUM(remaining_amount)
  FROM :"tenant_schema".ap_open_item
 WHERE company_id=:company_id AND branch_id=:branch_id AND supplier_id=:supplier_id
   AND status IN ('OPEN','PARTIALLY_SETTLED') AND due_date <= CURRENT_DATE
 GROUP BY currency_code;

-- Credit exposure. This must not use a sequential scan at 100k rows.
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT COALESCE(SUM(remaining_amount),0)
  FROM :"tenant_schema".ar_open_item
 WHERE company_id=:company_id AND client_id=:client_id
   AND status IN ('OPEN','PARTIALLY_SETTLED') AND currency_code='EGP';

-- Reconciliation subledger sides.
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT currency_code, SUM(remaining_amount)
  FROM :"tenant_schema".ar_open_item
 WHERE company_id=:company_id AND status IN ('OPEN','PARTIALLY_SETTLED')
 GROUP BY currency_code;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT currency_code, SUM(remaining_amount)
  FROM :"tenant_schema".ap_open_item
 WHERE company_id=:company_id AND status IN ('OPEN','PARTIALLY_SETTLED')
 GROUP BY currency_code;

ROLLBACK;
