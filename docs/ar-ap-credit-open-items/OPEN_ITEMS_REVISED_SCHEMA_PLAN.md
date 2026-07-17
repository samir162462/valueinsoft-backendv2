# Open Items — Revised Schema Plan

Supersedes the schema in draft V141/V142 (which stay untouched in `migrations/` as review artifacts). This document is the specification for the rewritten migrations; no SQL here is executable-as-is until promoted to new draft files after sign-off.

---

## 1. Design corrections vs the drafts

| Draft decision | Revised decision | Blocker |
|---|---|---|
| `ON DELETE CASCADE` on allocations | `ON DELETE RESTRICT`; reversal rows instead of deletes | B1 |
| `CREDIT_NOTE` as negative open item | Separate offset-document tables that allocate against open items | B10 |
| No amount sign constraints | `total/settled/remaining >= 0` CHECKs everywhere | B4 |
| No status↔balance rule | CHECK tying status to balances (below) | B4 |
| No over-allocation guard | DB triggers + service `FOR UPDATE` protocol | B5 |
| No allocation idempotency/reversal | `idempotency_key`, `status`, `reversal_of_allocation_id` | B6 |
| No party/branch coherence checks | Denormalized party columns + validation trigger | B7 |
| No currency | `currency_code` NOT NULL, same-currency allocation CHECK | B11 |
| Advances undefined | Explicit `on_account` open-item type (negative exposure held separately) | B12 |
| `MIN(branch_id)` opening balances | Opening balances carry the party's home branch (AP) or an explicit `branch_id` chosen at import approval (AR); never derived | B2 |

## 2. Revised tables (per tenant schema)

### 2.1 `ar_open_item` / `ap_open_item` (symmetric; AR shown)

```sql
CREATE TABLE ar_open_item (
    open_item_id      BIGSERIAL PRIMARY KEY,
    company_id        INTEGER NOT NULL REFERENCES public."Company"(id),
    branch_id         INTEGER NOT NULL REFERENCES public."Branch"("branchId"),
    client_id         INTEGER NOT NULL REFERENCES "Client"(c_id),
    source_type       VARCHAR(30) NOT NULL,   -- POS_ORDER | OPENING_BALANCE | ADJUSTMENT
    source_id         BIGINT,
    document_ref      VARCHAR(64),
    document_date     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date          TIMESTAMP,
    currency_code     VARCHAR(5) NOT NULL,    -- from Company.currency at creation
    total_amount      NUMERIC(19,4) NOT NULL,
    settled_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
    remaining_amount  NUMERIC(19,4) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    posting_request_id UUID,                  -- finance_posting_request linkage
    journal_entry_id  UUID,                   -- resolved posted journal (nullable until posted)
    idempotency_key   VARCHAR(160),
    reversal_of_open_item_id BIGINT REFERENCES ar_open_item(open_item_id),
    notes             VARCHAR(255),
    created_by        VARCHAR(120),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(120),
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ar_oi_source_type_ck  CHECK (source_type IN
        ('POS_ORDER','OPENING_BALANCE','ADJUSTMENT')),
    CONSTRAINT ar_oi_status_ck       CHECK (status IN
        ('OPEN','PARTIALLY_SETTLED','SETTLED','REVERSED')),
    CONSTRAINT ar_oi_amounts_nonneg_ck CHECK
        (total_amount >= 0 AND settled_amount >= 0 AND remaining_amount >= 0),
    CONSTRAINT ar_oi_amounts_sum_ck  CHECK (settled_amount + remaining_amount = total_amount),
    CONSTRAINT ar_oi_status_balance_ck CHECK (
        (status = 'OPEN'              AND settled_amount = 0 AND remaining_amount = total_amount) OR
        (status = 'PARTIALLY_SETTLED' AND settled_amount > 0 AND remaining_amount > 0) OR
        (status = 'SETTLED'           AND remaining_amount = 0) OR
        (status = 'REVERSED'          AND remaining_amount = 0)
    )
);
```

Notes: `CREDIT_NOTE` removed from `source_type` (see §4). `ap_open_item` differs only in: `supplier_id INTEGER NOT NULL` with **no FK** (per-branch supplier masters, F12) plus a service-layer existence check against `supplier_<branch_id>`; `source_type IN ('PURCHASE','OPENING_BALANCE','ADJUSTMENT')`; uniqueness `(company_id, branch_id, source_type, source_id)` — `branch_id` is mandatory in the AP key because supplier ids repeat across branches (reconciliation plan §5).

Unique partial indexes as in the drafts (source and idempotency), plus `(client_id, status, due_date)` and `(branch_id, document_date DESC)`.

### 2.2 `ar_receipt_allocation` / `ap_payment_allocation` (symmetric; AR shown)

```sql
CREATE TABLE ar_receipt_allocation (
    allocation_id     BIGSERIAL PRIMARY KEY,
    company_id        INTEGER NOT NULL REFERENCES public."Company"(id),
    branch_id         INTEGER NOT NULL,             -- receipt's branch, denormalized
    client_id         INTEGER NOT NULL,             -- denormalized for coherence checks
    receipt_id        INTEGER NOT NULL REFERENCES "ClientReceipts"("crId") ON DELETE RESTRICT,
    open_item_id      BIGINT  NOT NULL REFERENCES ar_open_item(open_item_id) ON DELETE RESTRICT,
    amount            NUMERIC(19,4) NOT NULL,
    currency_code     VARCHAR(5) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'POSTED',   -- POSTED | REVERSED
    reversal_of_allocation_id BIGINT REFERENCES ar_receipt_allocation(allocation_id),
    idempotency_key   VARCHAR(160) NOT NULL,
    posting_request_id UUID,
    created_by        VARCHAR(120),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ar_alloc_amount_ck   CHECK (amount > 0),
    CONSTRAINT ar_alloc_status_ck   CHECK (status IN ('POSTED','REVERSED')),
    CONSTRAINT ar_alloc_reversal_ck CHECK
        (status <> 'REVERSED' OR reversal_of_allocation_id IS NOT NULL
         OR allocation_id IS NOT NULL),  -- reversal rows always reference their target
    CONSTRAINT ar_alloc_idem_uq     UNIQUE (company_id, idempotency_key)
);
-- one ACTIVE allocation per (receipt, open item); reversals repeat the pair
CREATE UNIQUE INDEX ux_ar_alloc_active
    ON ar_receipt_allocation (receipt_id, open_item_id)
    WHERE status = 'POSTED' AND reversal_of_allocation_id IS NULL;
```

Rows are **append-only**: no UPDATE except setting `status='REVERSED'` on the target when a reversal row is inserted (one transition, enforced by trigger); no DELETE (revoke via `CREATE RULE`/trigger raising exception, matching the V139 immutability approach for posted journals).

### 2.3 Client credit columns — unchanged from draft V141 (limits, terms, status + CHECKs), with one addition: `credit_exposure_includes_unposted BOOLEAN NOT NULL DEFAULT true` is *not* added; exposure definition lives in the service (see §6).

### 2.4 Receipt hardening (prerequisite, missing from drafts)

`ClientReceipts` and `supplierReciepts` gain: `status VARCHAR(20) NOT NULL DEFAULT 'POSTED'` (`POSTED|REVERSED`), `reversal_of_id INTEGER NULL`, `idempotency_key VARCHAR(160)` with a partial unique index, and a `payment_method VARCHAR(30)` column on `ClientReceipts` (today `type` conflates document kind and method — `'ReceiveVMoney'`/`'supportExChange'`, F7). Existing negative `supportExChange` rows are grandfathered; new payouts are written as `status='POSTED'` rows of a new `type='ClientPayout'` with **positive** amount and their own GL posting (closing the F8 gap for new data). No destructive change to existing rows.

## 3. Reversal model

Documents: reversal inserts a new open item with `reversal_of_open_item_id` set and posts the offsetting journal via the existing posting-request pipeline; the original transitions to `REVERSED` with `remaining_amount` forced to 0 through a compensating allocation-like adjustment entry, never an in-place total rewrite (contrast F6). Allocations: reversal inserts a mirror row (`reversal_of_allocation_id`), the original flips to `REVERSED`, and the open item's `settled/remaining/status` are recomputed in the same transaction. Receipts: reversal inserts an offsetting receipt row referencing the original; the original flips to `REVERSED`; all its active allocations must be reversed first (enforced in service, verified by trigger counting active allocations). Nothing financial is ever deleted; `updated_at/updated_by/version` provide the audit trail on the mutable summary columns, and allocation history is fully append-only.

## 4. Credit notes / debit notes (investigation 10 resolution)

They are **offset documents and allocation sources, not open items**. New tables `ar_credit_note` / `ap_debit_note`: same money/status/idempotency/posting shape as open items (`total/applied/unapplied >= 0`, `applied + unapplied = total`). Application happens through the allocation tables via a nullable `credit_note_id` alternative source on the allocation row (`CHECK ((receipt_id IS NULL) <> (credit_note_id IS NULL))`). Consequences: aging buckets never see negative amounts; an unapplied credit note is visible as "unapplied credit" on statements rather than distorting overdue totals; dunning excludes clients whose unapplied credits cover their open items. GL: credit note posts debit 4100-contra/credit 1100 at issuance (via a new posting adapter source), application itself posts nothing (it re-labels existing subledger exposure).

## 5. Allocation concurrency and integrity protocol

Service transaction order (uses the existing `FOR UPDATE` precedent, F14):

1. `SELECT ... FOR UPDATE` the receipt (or credit note) row.
2. `SELECT ... FOR UPDATE` all target open items `ORDER BY open_item_id` (deterministic order prevents deadlocks).
3. Validate: same `company_id`, same `client_id`/`(branch_id, supplier_id)`, same `currency_code`; receipt `status='POSTED'`; `SUM(existing active allocations for receipt) + new amounts <= receipt.amount`; per item `new amount <= remaining_amount`.
4. Insert allocation rows (idempotency key = caller key or deterministic hash).
5. Update each open item's `settled_amount/remaining_amount/status` and bump `version`.

Defense in depth at the DB layer (constraint triggers, since CHECKs cannot span rows):
`trg_ar_alloc_coherence` — on INSERT, verify allocation `company_id/client_id/currency_code` match both the receipt row and the open item row, and that `amount <= open item remaining_amount` (the row is already locked by step 2, making the read stable);
`trg_ar_alloc_receipt_cap` — verify `SUM(active allocations) <= receipt amount`;
`trg_ar_alloc_no_delete` / `trg_ar_alloc_no_update` — raise on DELETE and on any UPDATE other than the single `POSTED→REVERSED` transition (V139 style).

## 6. Credit control definition

Exposure(client) = `SUM(ar_open_item.remaining_amount WHERE status IN ('OPEN','PARTIALLY_SETTLED'))` − `SUM(ar_credit_note.unapplied_amount)`. Checked at POS order creation when the pay method normalizes to `receivable` (`FinancePosPostingAdapter.normalizePaymentMethod` is the single normalization authority — the daily-cash-closing substring classifier is *not* reused; it gets refactored to call the same helper). `credit_status='BLOCKED'` denies regardless of amount; `HOLD` denies new credit but allows settlement; behavior WARN vs BLOCK per branch setting (V37 foundation). Walk-in credit (no clientId) is denied when credit control is enabled for the branch.

## 7. Branch and tenant rules

All tables tenant-schema-local; nothing crosses schemas. AR: client and exposure are company-level; `branch_id` on documents is the originating branch (reporting dimension only). AP: `(branch_id, supplier_id)` is the party key everywhere — uniqueness, statements, aging, allocation coherence. BranchManager access: because capability scope is company-wide today (F11), the new read endpoints accept an optional `branchId` filter and the service enforces it *server-side* when the caller's effective role assignment is branch-scoped; this must be covered by tests, not assumed from the capability layer. New capability keys (no reuse, B8): `clients.account.statement.view`, `clients.credit.view/manage`, `clients.openitems.view/allocate`, `clients.creditnote.create/reverse`, `suppliers.openitems.view/allocate`, `suppliers.debitnote.create/reverse`. V134's `clients.statement.view` keeps its trade-in meaning untouched.

## 8. Currency behavior

`currency_code` NOT NULL on every document, note, and allocation, defaulted from `public."Company".currency` at creation time. MVP rule: allocation requires `receipt.currency_code = open_item.currency_code` (trigger-enforced). USD purchase support (V102/V104) means AP documents may later carry USD; until multi-currency settlement is designed, USD purchases produce USD open items settled only by USD payments. No FX revaluation in MVP; the schema leaves room (rate/base-amount columns can be added additively).

## 9. Provisioning requirements (application code, same release)

`DbCompany` tenant bootstrap must call the new `ensure_ar_open_items_*` / `ensure_ap_open_items_*` functions (pattern: existing line 226 call to the V133 function). `DbBranch.createSupplierTable` must include `payment_terms_days INTEGER NOT NULL DEFAULT 0` in its DDL (F12) — without this, the migration's branch loop is a one-time patch that new branches silently miss.

## 10. Exact files that would eventually change

**New migrations** (rewritten, replacing the current drafts): AR foundation; AP foundation + supplier terms; receipt hardening (status/reversal/idempotency/payout type); credit/debit note tables; capability seeds (new keys only); reconciliation source registration.
**Backend — new**: `Service/ar/ArOpenItemService.java`, `Service/ap/ApOpenItemService.java`, `Service/ar/CreditControlService.java`, `DatabaseRequests/DbArOpenItem.java`, `DatabaseRequests/DbApOpenItem.java`, `Controller/ClientAccountController.java`, request/response models under `Model/Ar/` and `Model/Ap/`.
**Backend — modified**: `DbCompany.java` (bootstrap), `DbBranch.java` (supplier DDL), `SupplierReceiptService.java` (allocation write path replaces client-supplied remaining overwrite), `DBMSupplierReceipt.java` (retire `updateInventoryRemainingAmount` destructive write behind the new path), `ClientReceiptController/Service` (optional allocations, payout type), `OrderService.java` (open-item hook + bounce-back settlement of credit orders), `FinanceOperationalPostingService.java` (credit-note source, payout posting), `FinancePosPostingAdapter.java` (expose shared pay-method normalizer), `FinanceDailyCashClosingReportService.java` (consume shared normalizer), `FinanceReconciliationService.java` (new sources), `SupplierService.java`/`DbSupplier.java` (statement/aging move to due-date basis over open items), `TenantSqlIdentifiers.java` (new table helpers).
**Frontend**: `domains/clients/*` (credit tab, statement, aging, open items), `domains/finance/pages/ClientReceiptsPage.js` (allocation UI, payout type), `domains/suppliers/*` + `PointOfSale/Inventory/Suppliers/*` (open-items panel, allocation on payment, due-date aging), `domains/pos/sales/*` + `PayStatment.js` (credit-limit check UX), i18n domain message files.

## 11. Tests required before implementation

DB-level (Testcontainers PostgreSQL, per-tenant schema fixture): constraint suite for every CHECK above (status↔balance, non-negativity, sum rule); trigger suite (over-allocation rejected; cross-client/cross-currency/cross-company allocation rejected; DELETE and illegal UPDATE on allocations rejected; reversal transition allowed exactly once); concurrency test — two parallel transactions allocating the same receipt/open item must serialize without over-settlement (repeatable with `FOR UPDATE` protocol); idempotency replay returns the original allocation without duplication.
Service-level: FIFO allocation ordering by due date; partial/multi-document settlement; reversal cascade ordering (allocations before receipt); credit exposure computation including unapplied credit notes; POS block/warn paths including walk-in denial; bounce-back on a credit order reduces the open item rather than assuming cash refund.
Migration-level: idempotent re-run of every migration; bootstrap parity — a freshly provisioned tenant and branch have identical structures to a migrated one (this is the regression test for F12); V139-style immutability verified against the new triggers.
Reconciliation: staged totals vs segregated control balances on a seeded dataset with known variance; scheduled reconciliation flags an artificially injected drift.
