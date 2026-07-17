# Open Items Migration Review — V141/V142/V143 Drafts

Reviewer role: senior accounting systems architect / PostgreSQL migration reviewer
Scope: `docs/ar-ap-credit-open-items/migrations/V141–V143` (drafts, not yet in `src/main/resources/db/migration/`)
Date: 2026-07-12 · All findings verified against repository source, not the draft SQL comments.

---

## 1. Confirmed repository facts

Each fact below cites the file where it was verified.

**F1 — `inventory_stock_ledger` is a movement-level table, not a purchase document header.**
Created in `V34__inventory_move_modern_runtime_to_tenant_schema.sql` with `product_id NOT NULL`, `quantity_delta`, `movement_type` (SALE_OUT, PURCHASE_RECEIPT, OPENING_BALANCE, MANUAL_STOCK_IN/OUT, DAMAGED_OUT, BOUNCE_BACK_IN — see `DbSupplier.getSupplierSales`). `V36__inventory_ledger_compatibility_metadata.sql` bolted on `trans_total INTEGER`, `pay_type`, `remaining_amount INTEGER` as *compatibility metadata*. The modern purchase flow (`InventoryProductReceiptService.receiveProduct`) accepts exactly **one product per receipt**, so `PURCHASE_RECEIPT` rows happen to be document-level today — but only for that movement type, and only for purchases made through the modern flow.

**F2 — Purchases are triple-written.** One modern receipt writes (a) an `inventory_stock_ledger` row, (b) a legacy `InventoryTransactions_<branchId>` row (`insertLegacyInventoryTransaction`), and (c) increments `supplier_<branchId>."supplierTotalSales"/"supplierRemainig"` running totals (`updateSupplierPurchaseTotals`). Purchases made before the modern flow exist **only** in the legacy per-branch tables. Any backfill reading only the stock ledger misses legacy-only history; any backfill reading both double-counts modern purchases.

**F3 — Ledger `remaining_amount` is a client-supplied destructive overwrite.** `SupplierReceiptService.addSupplierReceipt` takes `remainingAmount` from the HTTP request body and `DBMSupplierReceipt.updateInventoryRemainingAmount` executes `UPDATE ... SET remaining_amount = ? WHERE stock_ledger_id = ?`. The server never verifies `new_remaining = old_remaining − amountPaid`. Historical `remaining_amount` values are therefore only as accurate as every frontend that ever called this endpoint. `supplierReciepts.transId` references `stock_ledger_id` (modern ledger), and `supplier_<branch>."supplierRemainig"` is decremented independently — three copies of the same balance that can drift.

**F4 — Money types are inconsistent.** `inventory_stock_ledger.trans_total/remaining_amount` are `INTEGER` (V36); `PosOrder_<b>."orderTotal"` is `INTEGER` (`DbBranch.createOrderTable`); `supplier_<b>."supplierRemainig"` is `INTEGER`; `ClientReceipts.amount` and `supplierReciepts."amountPaid"/"remainingAmount"` are `MONEY` (V0 baseline; read back via `::money::numeric`); the service layer computes `NUMERIC(19,4)` and truncates through `moneyToInt` when writing legacy rows. Fractional amounts are lost on the INTEGER paths.

**F5 — Web POS sends exactly one `orderType` value: `'Dirict'`.** (`PayStatment.js` lines 933, 1102 — the only `orderType:` literals in the frontend.) The offline POS bootstrap additionally offers payment method `'CREDIT'` ("Credit / Receivable", `BootstrapDataRepository` line 156). `FinancePosPostingAdapter.normalizePaymentMethod` recognizes `dirict/direct→cash`, `credit→receivable`, `card/visa/mastercard→card`, `instapay/wallet→wallet`, and passes anything else through. The daily-cash-closing classifier (`FinanceDailyCashClosingReportService` line 413–424) accepts a wider substring set (`credit/later/debt/receivable`, `مباشر`). The column is `VARCHAR(10)`, so long values would be truncated at write time. **The actual historical value domain is data-dependent and cannot be proven from the repository.**

**F6 — There is no cancelled/void/draft order state; returns mutate order totals in place.** Returns are per-line "bounce backs" (`OrderService.bounceBackProduct`): mark `PosOrderDetail.bouncedBack`, insert `BOUNCE_BACK_IN` inventory rows, record a `CASH_REFUND` shift movement (refund is assumed cash regardless of the original order's pay type), post a finance reversal, and call `updateOrderBounceBackTotals` which **rewrites the order's totals**. Original credit-order totals are not recoverable from `PosOrder_<b>` after a bounce back.

**F7 — `ClientReceipts` facts** (V0 baseline + `DBMClientReceipt` + `ClientReceiptsPage.js`):
amount `MONEY`; no status column; no reversal mechanism; no idempotency; no link to any order; `branchId` present; `type` is free text with two live values — `'ReceiveVMoney'` and `'supportExChange'`, the latter stored as a **negative amount** (a payout to the client). No DELETE statements exist in the repository for this table (append-only by convention only, nothing enforces it). **Not all receipts settle debt**: negative payouts and on-account payments from clients with no open orders both exist in the model.

**F8 — Negative client receipts never reach the GL.** `FinanceOperationalPostingService.enqueueClientReceipt` returns silently when `amount <= 0`. The 1100 control account therefore does not see `supportExChange` payouts, while any receipt-sum-based subledger would. The same guard exists for supplier payments (`enqueueSupplierPayment`).

**F9 — Tenant 1100 is polluted by platform-billing postings.** `FinancePaymentPostingAdapter.postBillingBalanceSettlement` credits the tenant's `pos.receivable` mapping (1100) for billing-balance settlements, with **no customerId**, and `postBillingRefund`-style flows re-debit it. AR open items per client can never sum to raw 1100 balance; reconciliation must segregate by journal source.

**F10 — `clients.statement.view` already exists and is already granted broadly.** Seeded by `V134__client_tradein_capabilities.sql` for *trade-in seller statements* and granted to **Owner, BranchManager, and Accountant**. Re-using this key for full AR statements silently widens what those existing grants expose.

**F11 — Branch scoping is membership-based and controller-dependent.** `AuthorizationService.hasAuthenticatedCapability` enforces tenant/branch *membership* when the controller passes a `branchId`; several read endpoints pass `null` (e.g. `SupplierReceiptController.supplierReceipts`), making them company-wide reads. All relevant capabilities are seeded with `scope_type='company'`. Branch-level data isolation for BranchManager is therefore **not** guaranteed by the capability layer; it depends on each endpoint's SQL filters.

**F12 — Provisioning is split between Flyway and Java DDL.** New tenants: `DbCompany` runs `ensure_*_for_tenant` functions (e.g. line 226 calls the V133 function). New **branches**: `DbBranch.createSupplierTable/createOrderTable/...` create per-branch tables from hard-coded Java DDL that does **not** include any column added later by migration loops. V142's `payment_terms_days` would silently be missing from every supplier table created after the migration runs, and `DbCompany` would not create the new AR/AP tables for new tenants unless updated.

**F13 — The V133 trade-in subledger is the only existing allocation precedent** and it already demonstrates the desired invariants (`paid + remaining = total` CHECK, `POSTED/REVERSED` status, idempotency key, `posting_request_id`), but also uses `ON DELETE CASCADE` on its allocation table — a precedent the new design should *not* copy for posted financial documents.

**F14 — Row-lock concurrency precedent exists**: `findClientForUpdate` / `findProductForUpdate` / `findIdempotencyForUpdate` in `DbInventoryProductReceiptRepository` use `SELECT ... FOR UPDATE` inside `@Transactional` service methods.

---

## 2. Risky assumptions in the drafts (disproven or unprovable)

| # | Draft assumption | Reality |
|---|---|---|
| A1 | V143 comment: "remaining_amount is authoritative" | Client-supplied destructive overwrite (F3); three divergent copies (F2) |
| A2 | Stock ledger rows with `remaining > 0` are purchase documents | True only for modern `PURCHASE_RECEIPT` rows; the draft filters on `supplier_id > 0` only, not `movement_type`; legacy-only purchases are missed entirely (F1, F2) |
| A3 | Credit orders are identifiable via `orderType LIKE %credit/later/debt%` | Web POS writes only `'Dirict'`; offline writes `'CREDIT'`; historical domain unproven; `VARCHAR(10)` truncation possible (F5) |
| A4 | `SUM(credit orders) − SUM(receipts)` reconstructs a client balance | Order totals mutate on returns (F6); receipts include negative payouts (F7); receipts also settle nothing (advances); walk-in credit orders lack `clientId` (F5/F6) |
| A5 | Open items will reconcile to 1100/2100 | 1100 includes billing settlements without customers (F9) and excludes negative receipts (F8); 2100 excludes zero-paid legacy adjustments; equality cannot hold without journal-source segregation |
| A6 | Re-describing `clients.statement.view` is harmless | It broadens data already granted to BranchManager/Accountant (F10) |
| A7 | Migration loop is sufficient for tenant/branch coverage | New branches and new tenants get tables from Java DDL / `DbCompany`, which the drafts do not (and cannot) update (F12) |
| A8 | `CREDIT_NOTE` as a negative open item is sound | Negative open items corrupt aging (negative buckets), dunning, and the `remaining >= 0` invariant; see revised model in `OPEN_ITEMS_REVISED_SCHEMA_PLAN.md` |

---

## 3. Migration blockers (must fix before any file moves to `db/migration/`)

**B1 — `ON DELETE CASCADE` on both allocation tables (V141, V142).** Deleting a receipt would silently delete posted financial allocations, breaking the audit trail and desynchronizing `settled_amount`. Posted financial links must never cascade. Replace with `ON DELETE RESTRICT` + reversal records.

**B2 — Automatic AR backfill from free-text classification (V143).** Violates F5/F6/F7: unprovable classification, mutated totals, negative unposted receipts. Must be removed and replaced by the approved-opening-balance process (see `OPEN_ITEMS_BACKFILL_DECISION.md`). The `MIN(branch_id)` assignment is additionally arbitrary and misattributes company-level exposure to one branch.

**B3 — Automatic AP backfill trusting `remaining_amount` (V143).** Violates F2/F3: unverified client-maintained values, missing legacy purchases, no `movement_type` filter. Must be gated behind the staging reconciliation report.

**B4 — Missing non-negativity and status-consistency constraints (V141, V142).** `total_amount/settled_amount/remaining_amount` have no `>= 0` checks and nothing ties `status` to the balances (a row can be `SETTLED` with `remaining > 0`).

**B5 — No allocation over-settlement protection and no concurrency mechanism.** Nothing prevents `SUM(allocations)` from exceeding the receipt amount or the open item total under concurrent writes. Constraints/triggers plus `FOR UPDATE` locking are required (design in revised schema plan).

**B6 — No allocation idempotency and no reversal model.** Allocation tables carry no idempotency key and no reversal linkage; receipts (`ClientReceipts`, `supplierReciepts`) themselves have no status/reversal support to build on.

**B7 — Party/branch/company mismatch is unchecked.** An allocation can link a receipt of client A to an open item of client B, or across branches/companies, with no constraint.

**B8 — Capability key reuse (V143).** `clients.statement.view` re-description expands existing V134 grants (F10). A new key is required.

**B9 — Provisioning gap (V141, V142).** `DbCompany` (new tenants) and `DbBranch` (new branch supplier tables) must be updated in the same release, otherwise new tenants/branches lack the new structures. This is an application-code prerequisite that the migrations alone cannot satisfy.

**B10 — `CREDIT_NOTE`/`DEBIT_NOTE` modeled as negative open items.** Breaks aging and the non-negativity invariant; must be remodeled as offset documents that allocate against open items (investigation 10 conclusion, see revised schema plan §4).

**B11 — Currency is undefined.** Documents carry no currency; `Company.currency` exists (V0) and USD purchase paths exist (`V104__inventory_usd_purchase_rate.sql`). MVP must pin `currency_code` per document and restrict allocation to matching currencies.

**B12 — Unallocated receipts and advances are undefined.** Existing receipts are on-account by design (no order link, F7). The schema must define where an unallocated remainder lives (receipt-level `unallocated_amount` view or advance documents), or FIFO auto-allocation will silently misstate per-document settlement.

---

## 4. What the drafts got right

Tenant-function + company-loop pattern matches V133 exactly; idempotent DDL; `NUMERIC(19,4)`; deterministic backfill idempotency keys; partial unique indexes on `(company_id, source_type, source_id)` and `idempotency_key`; keeping GL posting untouched; per-company schema and company-level tables with `branch_id` as a dimension. The foundation direction is sound — the corrections are about financial-integrity hardening and abandoning the automatic backfill.

---

## 5. Decision

Blockers B1–B12 are all addressable by the revised schema (`OPEN_ITEMS_REVISED_SCHEMA_PLAN.md`) and the staged backfill decision (`OPEN_ITEMS_BACKFILL_DECISION.md`). None invalidates the overall architecture; two of them (B2, B3) remove functionality from V143 rather than fix it in place.

```text
SAFE AFTER REQUIRED CHANGES
```

The drafts as written must NOT be moved to `src/main/resources/db/migration/`. See the companion documents for the revised schema, the reconciliation gate, and the recommended go-live strategy.
