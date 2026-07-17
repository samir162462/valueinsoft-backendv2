# Credit, Payables, Receivables & Open Items — Discovery and Architecture Plan

Date: 2026-07-11 · Repo: valueinsoft-backendv2 (backend) + Valueinsoft (React frontend) · DB: PostgreSQL, Flyway (latest applied migration: V140)

---

## Part 1 — Discovery: what exists today

### 1.1 Platform architecture

The system is multi-tenant with one PostgreSQL schema per company (`c_<companyId>`), resolved through `TenantSqlIdentifiers`. Shared entities (`Company`, `Branch`, capabilities, role grants, billing) live in `public`. Several operational tables are additionally split per branch inside the tenant schema: `PosOrder_<branchId>`, `PosOrderDetail_<branchId>`, `InventoryTransactions_<branchId>`, `supplier_<branchId>`. Client-facing tables (`Client`, `ClientReceipts`) and modern module tables (payroll, trade-in, loyalty) are company-level within the tenant schema.

All schema changes go through Flyway (`src/main/resources/db/migration`, V0–V140). The established pattern for tenant-schema changes (see V133) is a `public.ensure_<feature>_for_tenant(target_schema, company_id)` plpgsql function that is idempotent, executed in a `DO` block looping over `public."Company"`, and reused by `DbCompany` during new-tenant bootstrap.

### 1.2 General ledger (already strong)

A full double-entry finance module exists (V49–V57, V130, V139): chart of accounts seeded per tenant by `FinanceDefaultAccountsService`, including control accounts `1100 Accounts Receivable` and `2100 Accounts Payable` with source mappings `pos.receivable → 1100` and `purchase.payable → 2100`. Posting flows through `FinanceOperationalPostingService` with posting-request orchestration (UUID `posting_request_id`, idempotency), and posted journals are protected by immutability triggers (V139). Adapters already post POS sales, client receipts (`FinancePaymentPostingAdapter`, source `client_receipt`), purchases, expenses, inventory, and payroll. Reconciliation, period close, trial balance and reporting exist, and `FinanceReconciliationService` already maps the `customer` source to `pos.receivable`.

Conclusion: the GL side of AR/AP is done. What is missing is the **subledger**: party-level open items, allocations, credit control, and client statements.

### 1.3 Payables (suppliers) — partially built

Purchases are rows in `InventoryTransactions_<branchId>` carrying `payType` and `RemainingAmount` (modern ledger rows carry `pay_type`, `remaining_amount`, `idempotency_key`). Supplier payments live in `"supplierReciepts"` (PK `srId`) and are linked to a single purchase via `transId`; `RemainingAmount` is updated in place. `SupplierService`/`DbSupplier` already provide a running-balance statement (opening balance, debit/credit lines, running balance, posting status per line) and aging buckets (`SupplierAgingResponse`), plus a delete guard based on open balance. Frontend has `SupplierStatement.js`, `SupplierAging.js`, `SupplierDetails.js` and a suppliers API domain. Capabilities follow the V66 pattern (`suppliers.statement.view`, `suppliers.payment.create`, …).

Gaps: one payment can settle only one transaction (`transId`), there is no allocation table for partial/multi-document settlement, `remaining_amount` is mutated in place with no immutable trail, aging is computed from transaction date because there are no due dates or payment terms, and there are no formal debit/credit notes.

### 1.4 Receivables (clients) — thin

`Client` (PK `c_id`) has no credit fields at all (V133 added lifecycle/audit columns only). Credit sales are POS orders whose `orderType` is a free-text payment string — `FinanceDailyCashClosingReportService` classifies types by substring matching (`cash/direct/dirict/مباشر`, `card/visa/master`, `wallet/…`, `credit/later/debt/receivable`), which shows the values are not normalized. `PosOrder_<branchId>` has **no remaining-amount column**; the order total is the implicit receivable. Client payments are `"ClientReceipts"` rows (PK `crId`: type, amount, time, user, clientId, branchId) with **no link to any order** — pure on-account payments. They do post to the GL (source `client_receipt` credits `1100`), so the control account is correct, but no per-client subledger exists: no client statement endpoint, no client aging, no open items, no credit limit or terms, and nothing on the frontend beyond `ClientReceiptScreen.js` (record/list receipts).

### 1.5 Existing precedent to reuse

V133 (client trade-in) already implements exactly the target pattern on the payable-to-client side: a subledger document table (`client_tradein_receipt` with `total_amount / paid_amount / remaining_amount`, `payment_status`, `status POSTED/REVERSED`, idempotency, audit columns, version), a payment table with `posting_request_id`, and an **allocation table** (`client_tradein_payment_allocation`, unique per payment+document). The new AR/AP open-item design below deliberately mirrors it so services and reviews stay consistent.

### 1.6 Key discovery risks

Order payment types are free text, so identifying historical credit sales reliably per document is not possible; the AR backfill therefore uses client-level opening balances rather than per-order reconstruction. Some credit orders may lack `clientId` (walk-in credit is unattributable and stays out of the subledger; the GL remains the source of truth for the total). Supplier master data is per-branch (`supplier_<branchId>`), so `ap_open_item` cannot carry a real FK to the supplier row; referential integrity is enforced in the service layer, consistent with `"supplierReciepts"` today. Posted journals are immutable (V139), so subledger corrections must be new adjustment/reversal documents, never edits.

---

## Part 2 — Target architecture

### 2.1 Design principles

The GL keeps posting exactly as today; the new subledger sits beside it and must reconcile to `1100`/`2100` (a reconciliation check compares `SUM(remaining_amount)` per side against the control-account balance). Open items are append-oriented: `remaining_amount` on an open item changes only through allocations or reversal documents. Everything is per tenant schema, company-level tables with `branch_id` columns (clients and their exposure are company-wide; branch remains a reporting dimension). All money is `NUMERIC(19,4)`. All new writes are idempotent (`idempotency_key`) and versioned, matching V133.

### 2.2 Data model (new tables, per tenant schema)

**`ar_open_item`** — one row per receivable document. Columns: `open_item_id BIGSERIAL PK`, `company_id`, `branch_id`, `client_id → "Client"(c_id)`, `source_type` (`POS_ORDER`, `OPENING_BALANCE`, `ADJUSTMENT`, `CREDIT_NOTE`), `source_id BIGINT` (e.g. orderId; branch disambiguates the per-branch order table), `document_ref`, `document_date`, `due_date` (document_date + client terms), `total_amount`, `settled_amount`, `remaining_amount` (check: settled + remaining = total), `status` (`OPEN`, `PARTIALLY_SETTLED`, `SETTLED`, `REVERSED`), `posting_request_id UUID`, `idempotency_key`, audit + `version`. Unique on `(company_id, branch_id, source_type, source_id)` where source_id is not null. `CREDIT_NOTE` rows carry negative `total_amount` and reduce exposure.

**`ar_receipt_allocation`** — links `"ClientReceipts"(crId)` to `ar_open_item`, `amount > 0`, unique `(receipt_id, open_item_id)`, `created_by/at`. Allocation writes update the open item's `settled_amount/remaining_amount/status` in the same transaction.

**Client credit columns** on `"Client"`: `credit_limit NUMERIC(19,4) NOT NULL DEFAULT 0` (0 = no credit allowed unless status overrides), `credit_terms_days INT NOT NULL DEFAULT 0`, `credit_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL'` (`NORMAL`, `HOLD`, `BLOCKED`), `credit_notes VARCHAR(255)`.

**`ap_open_item`** — mirror for suppliers: `supplier_id` + `branch_id` (no FK, per-branch supplier tables), `source_type` (`PURCHASE`, `OPENING_BALANCE`, `ADJUSTMENT`, `DEBIT_NOTE`), `source_id` (transaction id in `InventoryTransactions_<branchId>` / modern ledger id), same amount/status/idempotency/audit structure, `due_date` from new supplier terms.

**`ap_payment_allocation`** — links `"supplierReciepts"(srId)` to `ap_open_item`, same shape as the AR allocation table. Existing `transId` single-link stays for backward compatibility; new payments write allocations and may span documents.

**Supplier terms**: `payment_terms_days INT NOT NULL DEFAULT 0` added to each `supplier_<branchId>` table (loop over branches inside the tenant function, following the V133 conditional-table approach).

### 2.3 Backend services and endpoints

`ArOpenItemService` + `DbArOpenItem`, `ApOpenItemService` + `DbApOpenItem` own document creation, allocation (FIFO by due date by default, explicit allocation list optional), reversal, statements and aging. Hooks: when a POS order posts with a credit-classified pay type and a `clientId`, create an `ar_open_item` in the same transaction as the order (and reuse the existing normalization logic, extracted from `FinanceDailyCashClosingReportService` into a shared `PaymentTypeClassifier`); when a purchase posts with `remaining_amount > 0`, create an `ap_open_item`; when a client receipt or supplier receipt is created, auto-allocate FIFO unless explicit allocations are supplied. GL posting calls remain untouched.

`CreditControlService`: on POS order creation with credit pay type, compute exposure = `SUM(ar_open_item.remaining_amount)` for the client + new order total; deny when `credit_status = 'BLOCKED'` or exposure exceeds `credit_limit` (behavior `WARN` vs `BLOCK` via a branch setting, using the existing branch-settings foundation from V37). Returns a structured result the POS frontend can render.

New endpoints (naming after existing controllers): `ClientAccountController` — `GET /clients/{companyId}/{clientId}/statement`, `/aging`, `/open-items`, `GET/PUT /credit`, `POST /open-items/{id}/allocations`, `POST /credit-notes`; `SupplierController` extended with `/open-items`, allocation endpoints and debit notes; `ClientReceiptController.create` accepts an optional allocation list. Aging for both sides switches to due-date basis with buckets current / 1–30 / 31–60 / 61–90 / 90+.

Capabilities follow V66: `clients.credit.view/manage`, `clients.statement.view` (already seeded by V134 for trade-in statements; V143 re-seeds it with a broader description), `clients.openitems.view/allocate`, `clients.creditnote.create/reverse`, `suppliers.openitems.view/allocate`, `suppliers.debitnote.create/reverse`, granted to Owner (all), Accountant (financial subset), BranchManager (view + statement).

### 2.4 Frontend (React)

Clients domain: a credit tab on the client profile (limit, terms, status, exposure gauge), client statement and aging screens mirroring `SupplierStatement.js`/`SupplierAging.js`, an open-items panel with allocation UI inside `ClientReceiptScreen.js`. Suppliers domain: open-items panel and multi-document allocation on the supplier receipt flow; aging relabeled to due-date basis. POS: credit-limit check response rendered at checkout (warning banner or hard block with the reason and current exposure). All strings added to the existing i18n domain message files (`clients.js`, `suppliers.js`, `finance.js`).

### 2.5 Reconciliation and data quality

A `finance` reconciliation extension compares subledger totals to control accounts per period (reusing `FinanceReconciliationService` source-item machinery from V54/V56). A follow-up normalization migration adds `pay_type_normalized` to order/purchase writes going forward (enum `CASH/CARD/WALLET/CREDIT/OTHER`), populated by the shared classifier, ending the substring matching.

---

## Part 3 — Migration drafts and rollout

Draft Flyway files are in `docs/ar-ap-credit-open-items/migrations/` and follow the V133 tenant-function pattern (idempotent, bootstrap-reusable). **They are drafts: after review, move them to `src/main/resources/db/migration/` unchanged in name and order.**

| File | Contents |
|---|---|
| `V141__ar_credit_and_open_items_foundation.sql` | Client credit columns, `ar_open_item`, `ar_receipt_allocation`, indexes, tenant loop |
| `V142__ap_open_items_and_supplier_terms.sql` | `ap_open_item`, `ap_payment_allocation`, `payment_terms_days` on `supplier_<branchId>`, tenant loop |
| `V143__ar_ap_open_items_backfill_and_capabilities.sql` | AP backfill: one `PURCHASE` open item per transaction with `remaining_amount > 0` (allocating linked receipts); AR backfill: one `OPENING_BALANCE` item per client from credit-order totals minus receipts where positive; capability + role-grant seeds |

Rollout phases: **P1** apply V141–V143, ship read-only statements/aging/open-items endpoints and screens, verify subledger-to-GL reconciliation on real tenants. **P2** enable write paths — allocations on new receipts, open-item creation hooks on POS/purchase posting. **P3** enable credit control at POS (start in WARN, flip to BLOCK per branch). **P4** credit/debit notes, dunning via the WhatsApp module, pay-type normalization migration.

Verification per phase: reconciliation totals (subledger vs 1100/2100), aging sums equal open-item sums, allocation sums never exceed receipt amounts (DB checks), and the existing finance posting tests extended with subledger assertions.
