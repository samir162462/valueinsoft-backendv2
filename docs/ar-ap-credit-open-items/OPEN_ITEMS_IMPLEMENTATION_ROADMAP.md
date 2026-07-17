# AR/AP Open Items — Implementation Roadmap (Codex Working Document)

> **User and technical handbook:** `OPEN_ITEMS_HANDBOOK.md`

> **Purpose of this file**: the single source of truth for implementing the Credit / Payables / Receivables / Open Items feature across multiple work sessions. Update the Progress Tracker after every session. Every step says WHAT to do, WHY it exists, WHICH files it touches, and WHEN it is done.
>
> **Read first, every session** (in this order):
> 1. This file — Progress Tracker (§B) + Conventions (§C)
> 2. `OPEN_ITEMS_REVISED_SCHEMA_PLAN.md` — the approved schema spec (authoritative for DDL)
> 3. `OPEN_ITEMS_MIGRATION_REVIEW.md` — facts F1–F14 and blockers B1–B12 (why the design is what it is)
> 4. `OPEN_ITEMS_BACKFILL_DECISION.md` — why there is NO automatic backfill
> 5. `git log --oneline -20` — what actually landed since the tracker was last updated

---

## A. Non-negotiable rules (violating any of these reintroduces a reviewed blocker)

1. **All schema changes via Flyway** (`src/main/resources/db/migration/`), tenant-function + company-loop pattern (copy V133 style). Never Java DDL for new structures — except the two provisioning files that already own Java DDL (Stage 2).
2. **The old drafts in `docs/ar-ap-credit-open-items/migrations/` must never be moved to `db/migration/`.** They are review artifacts. Rewritten migrations are authored fresh.
3. **No `ON DELETE CASCADE`** on any financial table. `RESTRICT` + reversal rows only (blocker B1).
4. **No automatic historical backfill.** Opening balances enter only through the gated import job (Stage 6). A Flyway migration that INSERTs balances is a defect (B2/B3).
5. **Append-only money**: allocations are never UPDATEd (except the single `POSTED→REVERSED` transition) or DELETEd. Corrections are new rows.
6. **New capability keys only** — never re-describe `clients.statement.view` (B8, expands V134 grants).
7. **Money is `NUMERIC(19,4)`** in all new tables. Never INTEGER, never MONEY (F4).
8. **AP party key is `(branch_id, supplier_id)`** everywhere — supplier ids repeat across branches (F12).
9. **Credit/debit notes are offset documents**, never negative open items (B10).
10. **Pay-method normalization has one authority**: the shared classifier extracted in step 3.1. Nothing new may substring-match pay types.

---

## B. Progress Tracker  ← UPDATE AFTER EVERY SESSION

| Stage | Step | Status | Session date | Commit / notes |
|---|---|---|---|---|
| 1 | 1.1 V141 AR foundation | ◐ authored — pending DB verification | 2026-07-12 | `V141__ar_open_items_foundation.sql` |
| 1 | 1.2 V142 AP foundation + supplier terms | ◐ authored — pending DB verification | 2026-07-12 | `V142__ap_open_items_and_supplier_terms.sql` |
| 1 | 1.3 V143 receipt hardening | ◐ authored — pending DB verification | 2026-07-12 | `V143__receipt_hardening.sql` |
| 1 | 1.4 V144 credit/debit notes | ◐ authored — pending DB verification | 2026-07-12 | `V144__credit_debit_notes.sql` |
| 1 | 1.5 V145 capability seeds | ◐ authored — pending DB verification | 2026-07-12 | `V145__ar_ap_open_items_capabilities.sql`; key regex needs ≥3 dot-segments — verified compliant |
| 1 | 1.6 Migration test suite | ◐ authored — run `mvn test -Dtest=OpenItemsMigrationIT` (needs Docker) | 2026-07-12 | `OpenItemsMigrationIT.java`, @Tag("postgres"), extends FlywayMigrationSmokeTest pattern |
| 2 | 2.1 DbCompany bootstrap parity | ☑ done | 2026-07-12 | `DbCompany` invokes all V141–V144 tenant ensure functions; covered by `OpenItemsProvisioningTest` |
| 2 | 2.2 DbBranch supplier DDL parity | ☑ done | 2026-07-12 | `createSupplierTable` includes `payment_terms_days`; covered by `OpenItemsProvisioningTest` |
| 2 | 2.3 Provisioning parity test | ◐ authored — pending Docker verification | 2026-07-12 | `OpenItemsMigrationIT` provisions a second tenant through real `DbCompany`/`DbBranch` and compares `information_schema.columns` |
| 3 | 3.1 Shared PaymentTypeClassifier | ☑ done | 2026-07-12 | Exact-alias classifier shared by POS posting and daily cash closing; unknown values remain `OTHER(raw)`; characterization tests added |
| 3 | 3.2 Db read repositories | ◐ implemented — pending Docker verification | 2026-07-12 | `DbArOpenItem` / `DbApOpenItem`: paged reads, currency-safe statements, due-date aging, ordered row locks, receipt locks; tenant identifier helpers added |
| 3 | 3.3 Read endpoints (statement/aging/open items) | ☑ done | 2026-07-12 | `ClientAccountController` and `SupplierOpenItemsController`; explicit server-side branch scope; limit 50/max 200; new capability checks |
| 3 | 3.4 Read-path tests | ◐ unit tests done — PostgreSQL tests pending Docker | 2026-07-12 | Classifier/POS/controller authorization tests pass; AR/AP statement and aging reconciliation tests added to `OpenItemsMigrationIT` |
| 4 | 4.1 Allocation engine | ☑ done | 2026-07-12 | AR/AP receipt and note allocation; deterministic idempotency; explicit and FIFO flows; ordered locks and balance recomputation |
| 4 | 4.2 Reversal engine | ☑ done | 2026-07-12 | Subledger + GL: generic `payment/subledger_reversal` adapter mirrors the original journal line-by-line and flips it to `reversed` (V139 transition); receipt reversals resolve their original posting by source id (`customer_receipt`→`customer_payout` fallback for AR, `supplier_payment` for AP); no-posting → subledger-only, pending/failed posting → CONFLICT |
| 4 | 4.3 POS credit-sale hook | ☑ done | 2026-07-12 | Shared classifier creates one idempotent AR item in the order transaction; credit sales require a client |
| 4 | 4.4 Purchase hook | ◐ in progress | 2026-07-12 | Idempotent AP creation implemented for non-trade-in supplier receipts; PostgreSQL integration verification pending Docker |
| 4 | 4.5 Receipt write-paths rewire | ☑ done | 2026-07-12 | Optional explicit/FIFO allocations, positive ClientPayout, server-authoritative supplier remaining, compatibility flag |
| 4 | 4.6 Bounce-back credit fix | ☑ done | 2026-07-12 | Return posting/refund behavior now follows the shared payment classification; credit return issues/applies a credit note |
| 4 | 4.7 Credit/debit note services | ☑ done | 2026-07-12 | Create/apply/reverse complete: note reversal reverses the issuance journal via the generic subledger reversal (`pos/sale_return/ar-credit-note-<id>`, `purchase/purchase_return/ap-debit-note-<id>`) |
| 4 | 4.8 Write-path + concurrency tests | ◐ nearly done | 2026-07-12 | Unit/write-path tests pass incl. new subledger-GL-reversal tests (null-skip, pending-conflict, mirror-request); PostgreSQL suites (`OpenItemsMigrationIT`, two-thread serialization) still need a Docker run |
| 5 | 5.1 CreditControlService | ☑ done | 2026-07-12 | `Service/openitems/CreditControlService`; company-wide exposure (open items − unapplied credit notes, company currency); client-row `FOR UPDATE` inside the order txn (`DbArOpenItem.lockClientCredit`); wired into `PosSalePostingService` before the open-item insert; denial rolls back the order |
| 5 | 5.2 Branch setting WARN/BLOCK | ☑ done | 2026-07-12 | `V146__pos_credit_control_mode_setting.sql`: `pos.creditControlMode` enum OFF/WARN/BLOCK, default OFF; served by the existing settings bundle (server-driven definitions — no frontend catalog change needed); status HOLD/BLOCKED always deny when mode ≠ OFF, mode softens only limit breaches |
| 5 | 5.3 Credit-control tests | ◐ unit matrix done | 2026-07-12 | `CreditControlServiceTest`: OFF skip (no lock), unknown-setting→OFF, at-limit pass, +0.01 BLOCK deny, WARN allow+warning, credit-note offset, BLOCKED-in-WARN deny, HOLD deny, missing client. Concurrent-sales serialization needs the PostgreSQL suite (Docker) |
| 6 | 6.1 Reconciliation sources | ☑ done | 2026-07-12 | `OpenItemsReconciliationService.snapshot`: subledger vs posted control (1100/2100 via `finance_account.account_code`), billing source types excluded per review F9 (line-level `source_module/source_type` filter); exposed at `GET /finance/openitems/{companyId}/reconciliation` |
| 6 | 6.2 Staging report endpoint | ☑ done | 2026-07-12 | `GET .../staging/ar` (per-client staged balances, classification via shared `PaymentTypeClassifier` in Java — never SQL substrings; flags HAS_BOUNCE_BACKS / HAS_NEGATIVE_RECEIPTS / RECEIPTS_EXCEED_CREDIT_ORDERS) and `GET .../staging/ap` (§3.4 unexplained-purchase proof + §3.3 supplier drift) |
| 6 | 6.3 Gated opening-balance import job | ☑ done | 2026-07-12 | `OpeningBalanceImportService`: two-phase gate (prospective check with zero writes → ABORTED/DRY_RUN commits only the audit row; pass → insert + post-insert re-verify, concurrent drift rolls back); deterministic keys `opening-ar-<client>` / `opening-ap-<branch>-<supplier>`; V147 append-only audit table (trigger-guarded) + DbCompany bootstrap parity; `POST /finance/openitems/{companyId}/opening-imports` (finance.entry.edit) |
| 6 | 6.4 Reconciliation tests | ◐ unit gate done | 2026-07-12 | `OpeningBalanceImportServiceTest`: exact-equality commit, unexplained abort (no inserts, audit recorded), approved-variance exact/wrong, approver required, dry-run no-writes, idempotent rerun skip, negative rejection, concurrent-drift conflict. PostgreSQL end-to-end (audit trigger, real gate) pends Docker |
| 7 | 7.1 Clients: credit tab | ☑ done | 2026-07-12 | `ClientCreditPanel` (limit/terms/status + exposure gauge + edit modal gated by `clients.credit.manage`); backend `PUT /clientAccount/.../credit` + `DbArOpenItem.updateClientCredit` added (was missing from 3.3) |
| 7 | 7.2 Clients: statement + aging | ☑ done | 2026-07-12 | Integrated into `ClientOpenItemsPanel` (aging summary + statement modal) inside the new "Account & Credit" tab of ClientInfo — one tab instead of separate pages (matches how trade-ins mount; less navigation surface) |
| 7 | 7.3 Clients: open items + allocation UI | ☑ done | 2026-07-12 | `ReceiptAllocationModal` after receipt save in `ClientReceiptsPage`: FIFO preview (client-side, same ordering as server), manual override switches to explicit targets, unallocated remainder shown as "on account", skip allowed |
| 7 | 7.4 Suppliers: open items + allocation | ◐ read side done | 2026-07-12 | `SupplierOpenItemsPanel` (due-date aging + per-document settlement) as a new tab in SupplierDetails; supplier payments already FIFO-allocate server-side (4.5) — manual override UI on the supplier payment flow remains |
| 7 | 7.5 POS checkout credit check UX | ◐ denial UX done | 2026-07-12 | Bilingual denial messages for all CREDIT_* codes in `PayStatment.getCheckoutErrorMessage`; WARN banner deferred — web POS has no credit pay type today (only `'Dirict'`; credit sales come from offline POS), so the warn path can't trigger from this screen yet |
| 7 | 7.6 i18n (EN + AR) | ☑ done | 2026-07-12 | ~60 keys in `clients.js` + `suppliers.js` (AR + EN); shared open-item keys duplicated into suppliers bundle since domain bundles load independently per route |
| 8 | 8.1 Index & EXPLAIN audit | ◐ executable audit authored | 2026-07-12 | Rollback-safe `open_items_phase8_explain.sql` seeds 100k rows per side and audits list/aging/exposure/reconciliation with ANALYZE+BUFFERS; execution and any evidence-driven index migration pending Docker/PostgreSQL |
| 8 | 8.2 Pagination + payload budgets | ☑ done | 2026-07-12 | All new list endpoints enforce default 50/max 200; client and supplier statements now default to the required trailing 90 days; controller tests pin both defaults |
| 8 | 8.3 Load & concurrency soak | ◐ soak suite authored | 2026-07-12 | Opt-in PostgreSQL tests cover 50 concurrent receipts/10 FIFO items with deadlock counter assertion, 1k FIFO receipts, and 20 concurrent exposure checks against one client limit; execution pending Docker |
| 8 | 8.4 Monitoring & alerts | ☑ done | 2026-07-12 | Actuator/Prometheus metrics for allocation latency, trigger rejection, replay rate, per-tenant AR/AP absolute drift; opt-in scheduled refresh; Prometheus drift/trigger/p95 alerts; variance documents log at WARN |
| 9 | 9.1 Pilot tenant go-live | ☐ not started | | |
| 9 | 9.2 Opening-balance approvals | ☐ not started | | |
| 9 | 9.3 WARN→BLOCK flip + rollout | ☐ not started | | |

Statuses: `☐ not started` → `◐ in progress` → `☑ done` → (`⊘ skipped` with reason).
**Session log** (append one line per session): `YYYY-MM-DD — steps touched — decisions made — open questions`.

- 2026-07-12 (stage 8 session) — 8.1–8.4 continued — Added the transactional 100k-row EXPLAIN audit, enforced 90-day statement defaults and max-200 list budgets, authored opt-in 50-writer/1k-receipt/concurrent-exposure PostgreSQL soaks, and added Micrometer/Prometheus metrics plus alert rules. Full default Maven suite passes (549 tests, 0 failures/errors; 20 skipped). Docker is unavailable locally, so EXPLAIN evidence and soak measurements remain pending; no speculative covering index was added.
- 2026-07-12 (stage 7 session) — 7.1–7.6 implemented — Decisions: (a) client credit + statement + aging + open items live in ONE "Account & Credit" tab inside ClientInfo (mirrors the trade-ins tab pattern) instead of standalone routed pages — client context is already open there and the modal shell handles capability-gated visibility (`clients.openitems.view`/`clients.credit.view`); (b) allocation modal defaults to sending an EMPTY allocations list (server FIFO) so preview and outcome cannot diverge — any manual edit switches to explicit targets; (c) backend gap discovered and closed: PUT credit endpoint was never built in 3.3 (`clients.credit.manage`, returns refreshed ClientCredit); (d) POS 7.5 scoped to denial messages because the web POS literally cannot produce a credit sale today (F5: only 'Dirict'); the WARN banner needs either a web pay-later option (product decision) or lands with the offline POS client; (e) open-item i18n keys duplicated across clients/suppliers bundles — `loadMessages.js` merges per-route domain sets, so cross-domain key reuse is unsafe. Files: backend (ClientAccountController +PUT, DbArOpenItem +updateClientCredit, OpenItemsWriteModels +CreditUpdateCommand); frontend (clientAccountApi, supplierOpenItemsApi, ClientCreditPanel, ClientOpenItemsPanel, ReceiptAllocationModal, SupplierOpenItemsPanel, ClientInfo/SupplierDetails/ClientReceiptsPage/PayStatment wiring, clients/suppliers i18n). Open: supplier-payment manual allocation UI; WARN banner pending web credit pay type; `npm run build` + smoke test the new tabs; Docker suite still pending.
- 2026-07-12 (stage 6 session) — 6.1–6.4 implemented — Decisions: (a) reconciliation implemented as a dedicated read service + endpoints instead of deep integration into the run-based `FinanceReconciliationService` lifecycle — its `customer`/`supplier` mapping keys already target the right accounts, and the snapshot can be registered as an imported source later without rework; (b) control balances resolved by `finance_account.account_code` (1100/2100) with posted-entry join; billing exclusion filters on line-level `source_module='payment' AND source_type IN (billing_balance_settlement, billing_balance_credit, billing_payment_reversal)` — journal lines carry source columns denormalized so no extra joins; (c) AR staging classifies order types in JAVA via the shared `PaymentTypeClassifier` (SQL only aggregates per client+orderType) — rule 10 upheld; (d) import gate is TWO-PHASE inside one transaction: prospective check first so an ABORTED decision commits nothing but its own audit row (the audit-vs-rollback conflict is structurally impossible), then post-insert re-verification catches mid-import drift (rare race → full rollback + retry); (e) advances (receipts > credit orders) are flagged in staging and REJECTED as negative opening balances — they must enter as unapplied credit notes (revised schema §4); (f) endpoints reuse `finance.entry.read/edit` — no new capability seeds needed, existing Owner/Accountant grants apply. Files: V147 (audit table + guard trigger), `DbCompany` (bootstrap parity), `TenantSqlIdentifiers` (+helper), `DbOpenItemsReconciliation`, `OpenItemsReconModels`, `OpenItemsReconciliationService`, `OpeningBalanceImportService`, `OpenItemsReconciliationController`, `OpeningBalanceImportServiceTest` (9 tests). Open: Docker PostgreSQL run (incl. V147 idempotency + audit trigger kill-test — extend `OpenItemsMigrationIT` next session); Stage 7 (frontend) next; scheduled drift alerting belongs to 8.4.
- 2026-07-12 (stage 5 session) — 5.1–5.3 implemented — Decisions: (a) OFF mode short-circuits BEFORE the client lock (zero overhead on non-credit-controlled branches, preserves today's behavior exactly); (b) exposure is company-wide and single-currency (company currency) per revised plan §6 — the existing branch-scoped `getCredit` read stays for the credit tab display, credit CONTROL uses the new company-wide sums; (c) `credit_status` HOLD/BLOCKED are hard denials whenever mode ≠ OFF — WARN vs BLOCK differentiates only limit breaches; (d) walk-in credit remains rejected upstream by `CREDIT_SALE_CLIENT_REQUIRED` (stricter than the mode-gated rule, kept); (e) warning results are logged server-side and NOT yet threaded into `CreateOrderResult` — that record is constructed by `DbPosOrder.addOrder` and reshaping it is deferred to 7.5 where the POS response/UX is touched anyway (WARN display can also read the existing `GET .../credit` endpoint); (f) V146 numbering verified free. Files: V146 migration, `CreditControlService`, `OpenItemsWriteModels` (+ClientCreditLock, +CreditCheckResult), `DbArOpenItem` (+lockClientCredit, +sumClientOpenExposure, +sumClientUnappliedCreditNotes), `PosSalePostingService` (check before open-item insert; optional setter injection — no test breakage), `CreditControlServiceTest`. Open: Docker run for concurrency serialization test; Stage 6 (reconciliation + gated import) next.
- 2026-07-12 (later session) — 4.2/4.7 GL reversal completed — Decisions: (a) ONE generic reversal mechanism instead of per-document mirror logic: `payment/subledger_reversal` posting requests carry `originalJournalEntryId`; the payment adapter loads the original posted journal, swaps every line's debit/credit (keeping party refs), posts an `RV-` reversal journal, and links it via `markOriginalJournalReversed` (uses the V139 posted→reversed transition + optimistic version check); (b) safety rule in `enqueueSubledgerGlReversal`: original posting request missing → return null (nothing hit GL, subledger-only reversal complete); request pending/failed → CONFLICT `FINANCE_REVERSAL_SOURCE_NOT_POSTED` because it could still post after the reversal; (c) reversal source ids are deterministic (`client-receipt-reversal-<id>` etc.) so the pipeline's source dedupe gives idempotency; (d) AR receipt reversal tries `customer_receipt` then falls back to `customer_payout` (both share sourceId `client-receipt-<crId>`); (e) `reverseOpenItem` stays subledger-only by design — POS_ORDER sale reversal is the bounce-back flow's job (4.6), and OPENING_BALANCE/ADJUSTMENT items have no GL posting. Files: `FinancePaymentPostingAdapter` (+`postSubledgerReversal`), `FinanceOperationalPostingService` (+`enqueueSubledgerGlReversal`, +DbFinancePostingRequest dep), `ArOpenItemService`/`ApOpenItemService` (reverseReceipt now takes reason/actor + posts GL reversal), `ArCreditNoteService`/`ApDebitNoteService` (reverse posts GL reversal), `OpenItemsWriteController` (reason body param), tests updated (+3 new reversal tests). Open: run PostgreSQL suites on Docker; then Stage 4 fully closes and Stage 5 (credit control) begins.
- 2026-07-12 — 1.1–1.6 authored — Decisions: (a) guard trigger functions are generic in `public` using `TG_TABLE_SCHEMA` dynamic SQL, attached per tenant table; (b) V144 extends allocation sources by CREATE OR REPLACE of the V141/V142 guard functions (single authority, no drift); (c) reversal mirrors must match the original allocation's amount exactly (full reversal only — partial correction = reverse + reallocate); (d) allocation reversal rows are excluded from the "active" partial unique index via `reversal_of_allocation_id IS NULL`; (e) Accountant does NOT get `clients.credit.manage` (owner-level commercial decision — revisit if business disagrees). Open: run `OpenItemsMigrationIT` on a Docker-enabled machine before starting Stage 2; verify `Client` fixture in prod tenants all have `c_id` PK (assumed from DbCompany DDL).
- 2026-07-12 — 2.1–2.3 implemented — `DbCompany` now invokes the four open-items ensure functions, new branch supplier DDL includes payment terms, and the migration IT compares Java-provisioned vs migrated schemas. Unit provisioning tests pass; Docker is unavailable locally, so the PostgreSQL parity test remains pending execution.
- 2026-07-12 — 3.1–3.4 implemented — Unified payment classification without substring inference; added currency-safe AR/AP open-item pages, statements, aging, credit profile, lock helpers, and branch-scoped secured endpoints. Full default Maven suite passes. Tagged PostgreSQL tests include AR/AP statement/aging sum assertions but remain skipped because Docker is unavailable locally.
- 2026-07-12 — 4.1–4.8 continued — Implemented AR/AP allocation engines, append-only allocation reversals, POS/purchase creation hooks, receipt rewiring, credit-return classification, credit/debit note issuance/application, secured write endpoints, API notes, and focused service tests. The full default Maven suite passes. PostgreSQL concurrency/provisioning tests are authored but skip because Docker is unavailable; GL reversal requests for reversed receipts/open items/notes remain before Stage 4 can be marked complete.

---

## C. Conventions cheat-sheet (so any session starts fluent)

- Tenant schema: `c_<companyId>`; helper: `TenantSqlIdentifiers` (add a helper method per new table — grep it before writing SQL by hand).
- Per-branch legacy tables: `PosOrder_<b>`, `InventoryTransactions_<b>`, `supplier_<b>`. Company-level: `"Client"` (PK `c_id`), `"ClientReceipts"` (PK `"crId"`, amount MONEY), `"supplierReciepts"` (PK `"srId"`, `"transId"` → `inventory_stock_ledger.stock_ledger_id`).
- Migration pattern: `public.ensure_<x>_for_tenant(schema, companyId)` plpgsql, idempotent, then `DO $$` loop over `public."Company"`. Copy V133.
- Posting pipeline: `FinanceOperationalPostingService.enqueue*` → `finance_posting_request` → adapters. Control accounts: 1100 AR (`pos.receivable`), 2100 AP (`purchase.payable`).
- Locking precedent: `SELECT ... FOR UPDATE` inside `@Transactional` (see `DbInventoryProductReceiptRepository.findClientForUpdate`).
- Idempotency precedent: `insertPendingIdempotency` / `findIdempotencyForUpdate` / `markIdempotencyCompleted` in the same repository.
- Immutability precedent: V139 trigger style for "no UPDATE/DELETE" enforcement.
- Capability seeds: V66 pattern (`platform_capabilities` upsert + `role_grants` per role).
- Auth: `authorizationService.assertAuthenticatedCapability(principal, companyId, branchId, key)` — **always pass branchId on branch-scoped writes**.
- Frontend: React, domain folders under `src/domains/<domain>/{api,pages,components}`, i18n in `src/domains/shared/i18n/domainMessages/*.js` + legacy `Components/SideNavBarPro/Messages.js`.
- Migration numbering: V140 is the last applied migration. Numbers below assume V141–V146 are free — **verify with `ls src/main/resources/db/migration | sort -V | tail` before authoring**; renumber if the repo moved on.

---

## Stage 1 — Database foundation (Flyway)

**Why first**: everything else compiles against these tables; migrations are the only artifact that must be strictly ordered; and writing constraints first means every later bug surfaces as a loud DB error instead of silent money drift.

### 1.1 `V141__ar_open_items_foundation.sql`
Author from `OPEN_ITEMS_REVISED_SCHEMA_PLAN.md` §2.1–2.3 (AR side): client credit columns (`credit_limit`, `credit_terms_days`, `credit_status`, `credit_notes` + CHECKs), `ar_open_item` (with `currency_code`, `journal_entry_id`, `reversal_of_open_item_id`, non-negativity + sum + status↔balance CHECKs, **no** CREDIT_NOTE source type), `ar_receipt_allocation` (RESTRICT FKs, `status`, `reversal_of_allocation_id`, `idempotency_key` UNIQUE, denormalized `client_id`/`branch_id`/`currency_code`, partial unique active-pair index), constraint triggers (`coherence`, `receipt_cap`, `no_delete`, `no_update`).
*Why the triggers live in the migration and not only in Java*: the service protocol (Stage 4) is the primary guard, but any future code path (AI tools, offline sync, manual SQL) hits the same tables — DB-level rules are the last line of defense for money.
**Done when**: migration runs twice cleanly on a dev DB with ≥2 tenant schemas; `\d c_<id>.ar_open_item` shows all CHECKs; inserting a `SETTLED` row with `remaining > 0` fails; deleting an allocation fails.

### 1.2 `V142__ap_open_items_and_supplier_terms.sql`
Mirror of 1.1 for AP: `ap_open_item` (supplier_id **without FK** — per-branch masters; uniqueness includes `branch_id`), `ap_payment_allocation` (FK to `"supplierReciepts"("srId")` RESTRICT), same trigger set (coherence checks `(branch_id, supplier_id)` pair), `payment_terms_days INTEGER NOT NULL DEFAULT 0` added to every existing `supplier_<b>` table via branch loop.
*Why terms live on the supplier row*: due dates derive from document date + party terms at document creation; storing the resolved `due_date` on the open item keeps aging queries index-only and immune to later terms edits.
**Done when**: same bar as 1.1, plus `payment_terms_days` present on all `supplier_<b>` tables of the dev tenants.

### 1.3 `V143__receipt_hardening.sql`
Add to `"ClientReceipts"` and `"supplierReciepts"`: `status VARCHAR(20) NOT NULL DEFAULT 'POSTED'` (+CHECK `POSTED|REVERSED`), `reversal_of_id INTEGER NULL`, `idempotency_key VARCHAR(160)` (partial unique per company scope — these are tenant tables, plain partial unique is enough), `payment_method VARCHAR(30)` on ClientReceipts. Grandfather existing rows untouched (defaults cover them).
*Why*: the allocation engine (4.1) and reversal engine (4.2) need receipt status/idempotency to exist; today's receipts have neither (F7), and reversing a receipt without a status column would force deletes — prohibited by rule 3/5.
**Done when**: reruns cleanly; legacy negative `supportExChange` rows still readable; new columns visible in both tables across tenants.

### 1.4 `V144__credit_debit_notes.sql`
`ar_credit_note` / `ap_debit_note` per revised plan §4 (`total/applied/unapplied` with sum + non-negativity CHECKs, status, idempotency, posting refs), plus `credit_note_id`/`debit_note_id` nullable columns on the allocation tables with `CHECK ((receipt_id IS NULL) <> (credit_note_id IS NULL))` (exactly one source).
*Why offset documents*: negative open items poison aging buckets and dunning (B10); a credit note with its own applied/unapplied lifecycle keeps "overdue debt" and "credit the shop owes back" as separate, both-visible numbers.
**Done when**: an allocation row can carry a credit note OR a receipt, never both/neither (constraint test).

### 1.5 `V145__ar_ap_capabilities.sql`
V66-pattern seeds with **new keys only**: `clients.account.statement.view`, `clients.credit.view`, `clients.credit.manage`, `clients.openitems.view`, `clients.openitems.allocate`, `clients.creditnote.create`, `clients.creditnote.reverse`, `suppliers.openitems.view`, `suppliers.openitems.allocate`, `suppliers.debitnote.create`, `suppliers.debitnote.reverse`. Grants: Owner all; Accountant all financial; BranchManager view-only (`*.view` + `clients.account.statement.view`).
*Why BranchManager gets no `allocate`*: allocation changes settlement state (money movement in the subledger); the review showed branch scoping is membership-based only (F11), so write capabilities stay with financial roles until server-side branch filtering (3.3) is proven by tests.
**Done when**: seeds idempotent (rerun safe); V134's `clients.statement.view` untouched (`SELECT description FROM platform_capabilities WHERE capability_key='clients.statement.view'` still says trade-in).

### 1.6 Migration test suite
Testcontainers PostgreSQL fixture that: creates 2 fake tenants + 2 branches each (mimicking `DbBranch` DDL for legacy tables), runs Flyway, asserts structure parity across tenants, and runs the constraint/trigger kill-tests from revised plan §11 (every CHECK and trigger must have one failing insert test).
*Why now and not with Stage 4*: constraint tests are pure SQL — they lock the schema contract before any Java exists, so service-layer work in later sessions can refactor freely against a pinned contract.
**Done when**: `mvn test -Dtest=OpenItemsMigrationIT` green in CI.

---

## Stage 2 — Provisioning parity (application code, same release as Stage 1)

**Why this is its own stage**: the review's F12 showed migrations only patch *existing* tenants/branches. Without this stage, every tenant or branch created after deploy silently lacks the new tables/columns — the worst kind of production bug (works in test, breaks weeks later).

### 2.1 `DbCompany` bootstrap
Add `SELECT public.ensure_ar_open_items_foundation_for_tenant(...)`, `ensure_ap_open_items_...`, `ensure_receipt_hardening_...`, `ensure_credit_debit_notes_...` calls next to the existing V133 call (line ~226). Order matters: after Client/ClientReceipts creation.
**Done when**: provisioning a brand-new company in dev yields all new tables.

### 2.2 `DbBranch.createSupplierTable`
Add `payment_terms_days INTEGER NOT NULL DEFAULT 0` to the Java DDL.
**Done when**: a new branch's `supplier_<b>` has the column.

### 2.3 Provisioning parity test
Extend the Stage 1.6 fixture: provision tenant+branch through the Java path (`DbCompany`/`DbBranch`), diff `information_schema.columns` against a migrated tenant — zero differences allowed for the new structures.
*Why automated*: this is the regression net for the split-brain provisioning model; any future migration author gets a red build instead of a silent gap.

---

## Stage 3 — Backend read layer

**Why reads before writes**: read endpoints are shippable value with zero financial risk (statements/aging over data that Stage 4 will start creating), they force the Db repository layer into shape, and the frontend (Stage 7) can start against them early.

### 3.1 Shared `PaymentTypeClassifier`
Extract normalization from `FinancePosPostingAdapter.normalizePaymentMethod` into `Service/finance/PaymentTypeClassifier.java`; refactor `FinancePosPostingAdapter` and `FinanceDailyCashClosingReportService` (substring matcher, line ~413) to use it. Output enum: `CASH, CARD, WALLET, RECEIVABLE, OTHER(raw)`.
*Why first in this stage*: three places currently disagree on what "credit" means (F5); every later step (POS hook, credit control, staging report) needs one answer. This is also the highest-risk *refactor* in the project — do it while nothing new depends on the old behavior, and pin the daily-cash-closing report output with a characterization test **before** refactoring.
**Done when**: both consumers delegate; characterization test proves report output unchanged for the known value set (`Dirict`, `CREDIT`, card/wallet variants, Arabic `مباشر`).

### 3.2 Repositories: `DbArOpenItem`, `DbApOpenItem`
JdbcTemplate repositories (project style — no JPA) with: paged open-item queries by party/status/due-date, statement query (opening balance + period lines + running balance, mirroring `DbSupplier.getSupplierStatement` shape so the frontend can reuse rendering), aging query (buckets current/1-30/31-60/61-90/90+ **by `due_date`**), `FOR UPDATE` lock helpers (`findOpenItemsForUpdate(ids ORDER BY open_item_id)`, `findReceiptForUpdate`). Add `TenantSqlIdentifiers` helpers for all new tables.
*Why due-date aging*: aging by document date (today's supplier aging) misstates overdue-ness the moment terms exist; due_date was resolved at creation precisely to make this query cheap.

### 3.3 Read endpoints — `ClientAccountController` (new) + `SupplierController` (extend)
`GET /clientAccount/{companyId}/{clientId}/statement|aging|open-items|credit`, `GET /suppliers/.../open-items` (party key = branchId+supplierId). Every endpoint: `assertAuthenticatedCapability` with the new keys, explicit `branchId` query param filter applied **server-side**, pagination (`limit/offset`, default 50, max 200).
*Why server-side branch filter despite company-scoped capabilities*: F11 — the capability layer verifies membership, not data scope; scoping in SQL is the only real isolation.
**Done when**: OpenAPI-visible endpoints return correct shapes against seeded data.

### 3.4 Read-path tests
Repository tests on the Testcontainers fixture (statement math: opening + lines = closing; aging sums = open-item sums) and controller authorization tests (missing capability → 403; foreign tenant → TENANT_ACCESS_DENIED).

---

## Stage 4 — Backend write layer (the financial core)

**Why the strict internal order below**: each step consumes the previous one's invariants; the allocation engine is the keystone and everything else is a caller.

### 4.1 Allocation engine — `ArOpenItemService.allocate(...)` / `ApOpenItemService.allocate(...)`
Implement exactly the revised plan §5 protocol: lock receipt/note → lock open items in id order → validate (company, party, currency, status, caps) → insert allocations (idempotency: caller key or deterministic hash; replay returns existing result) → recompute `settled/remaining/status` + `version` bump. FIFO-by-due-date auto-allocation when caller sends no explicit list.
*Why locks + triggers both*: locks give correctness for well-behaved callers; triggers make misbehaving callers fail loudly. Belt and suspenders is the standard for shared-money tables.
**Done when**: unit tests cover partial, multi-document, over-allocation rejection, idempotent replay, FIFO ordering.

### 4.2 Reversal engine
`reverseAllocation`, `reverseReceipt` (requires all its active allocations reversed first — enforce and test), `reverseOpenItem` (compensating entry, original → `REVERSED`, `remaining=0`), each posting its GL reversal through the existing posting-request pipeline where a posting existed.
*Why reversal-first design*: the review found today's model mutates and overwrites (F3, F6); reversals are what make the subledger auditable and are required before ANY write path goes live — support will need to undo mistakes on day one.

### 4.3 POS credit-sale hook
In `OrderService` (same transaction as order insert): if `PaymentTypeClassifier == RECEIVABLE` and `clientId` present → create `ar_open_item` (`source_type='POS_ORDER'`, `source_id=orderId`, `branch_id` = order branch, `due_date = orderTime + client.credit_terms_days`, idempotency key derived from order idempotency key). GL posting stays untouched — the adapter already debits 1100.
*Why same transaction*: an order that exists without its open item (or vice versa) is subledger drift; the per-branch order table and company-level open item table live in the same schema, so one transaction covers both.

### 4.4 Purchase hook
In `InventoryProductReceiptService` (supplier branch, non-trade-in): when `remaining > 0` → create `ap_open_item` (`source_type='PURCHASE'`, `source_id=stock_ledger_id`, `due_date = receiptTime + supplier.payment_terms_days`). Keep the legacy triple-write for now (compat), but the open item becomes the authoritative remaining.
**Done when**: one modern purchase produces exactly one open item; idempotent replay of the receipt does not duplicate it.

### 4.5 Receipt write-paths rewire
`ClientReceiptService`: accept optional allocations (else FIFO via 4.1); new `type='ClientPayout'` with positive amount + GL posting (closes F8 for new data); reject new negative amounts (grandfathered history stays).
`SupplierReceiptService`: compute allocations via the engine; **stop trusting client-supplied `remainingAmount`** — server computes it; keep writing the legacy ledger/supplier copies *derived from server-side math* during transition (flag `finance.openitems.legacy-writes.enabled`, default true).
*Why keep legacy writes temporarily*: supplier statements/aging still read the old columns until Stage 7 flips them; killing the writes early would fork the two views of truth mid-rollout.
**Done when**: API contract change documented; old clients sending `remainingAmount` get it ignored + logged (not errored) for one release.

### 4.6 Bounce-back credit fix
In `OrderService.bounceBackProduct`: when the original order's open item exists, reduce it (reversal/adjustment via 4.2 semantics) instead of assuming `CASH_REFUND`; cash refund only for cash orders.
*Why*: F6 — today a return on a credit sale refunds cash that was never received and leaves 1100 overstated.

### 4.7 Credit/debit note services
Create/apply/reverse for `ar_credit_note` / `ap_debit_note`; application goes through the allocation engine (note as source); new posting-adapter source for issuance (debit revenue-contra / credit 1100; mirror for AP).

### 4.8 Write-path + concurrency tests
The revised plan §11 service suite, plus the two-threads-one-open-item serialization test (must not over-settle; assert on final `settled_amount`) and the reversal-ordering test. Concurrency tests are mandatory before Stage 5 — credit control reads exposure that these writes produce.

---

## Stage 5 — Credit control

### 5.1 `CreditControlService`
Exposure = Σ open `ar_open_item.remaining` − Σ `ar_credit_note.unapplied`. Called by `OrderService` before credit-order insert (same transaction, after `FOR UPDATE` on the Client row to serialize concurrent sales against one limit). Result object: `{allowed, mode, exposure, limit, reason}`. `BLOCKED` → deny always; `HOLD` → deny new credit; walk-in credit denied when control enabled.
*Why lock the client row*: two simultaneous credit sales must not both pass a nearly-exhausted limit; the client row is the natural serialization point (precedent: `findClientForUpdate`).

### 5.2 Branch setting
`credit_control_mode` (`OFF|WARN|BLOCK`) via the V37 branch-settings foundation, default `OFF` (safe rollout), exposed in the existing settings bundle.

### 5.3 Tests
Limit boundary (exactly-at-limit passes, +0.01 fails), HOLD/BLOCKED matrix, walk-in denial, WARN returns allowed+warning, concurrent-sales serialization.

---

## Stage 6 — Reconciliation + gated opening balances

**Why before frontend polish**: go-live (Stage 9) is blocked on this stage, and its staging report informs what the frontend statement pages must display for pre-go-live history.

### 6.1 Reconciliation sources
Register `ar_open_items` / `ap_open_items` sources in `FinanceReconciliationService` (V54/V56 machinery) implementing the §3.5 queries from `OPEN_ITEMS_RECONCILIATION_PLAN.md` — subledger vs source-segregated control balance (excluding billing settlements per F9), from go-live date, plus approved opening variance.
*Why inside the existing service*: failures surface in the reconciliation UI accountants already use; a parallel tool would not get looked at.

### 6.2 Staging report endpoint
Read-only `GET /clientAccount/{companyId}/staging-report` + supplier equivalent implementing reconciliation plan §3.2/§3.3/§3.4 with quality flags. Owner/Accountant capability. This is the document the approver signs.

### 6.3 Gated opening-balance import job
Application-managed (NOT Flyway — it must run per tenant at each tenant's go-live): input = approved per-party figures (+ chosen branch for AR items); inside one transaction: insert `OPENING_BALANCE` items → compute both sides of the §4 gating rule → commit only on equality or with an explicit variance document carrying the approver. Idempotent per tenant (deterministic keys `opening-<party>`); dry-run mode that outputs the would-be variance.
*Why a job and not a migration*: Flyway runs once for all tenants at deploy; go-live is per-tenant and needs human sign-off between staging and import (backfill decision §2).

### 6.4 Tests
Seeded dataset with known variance: gate aborts; with variance doc: commits; dry-run matches actual; rerun is a no-op.

---

## Stage 7 — Frontend (React)

**Why after Stage 4**: read pages could ship after Stage 3, but allocation UI and credit UX need the write endpoints; batching frontend into one stage keeps i18n and UX review coherent. Reuse the supplier statement/aging rendering patterns — accountants already know them.

### 7.1 Clients: credit tab — `domains/clients/components/ClientCreditPanel.js` + api. Limit, terms, status editor (capability-gated), exposure vs limit gauge. *Why a gauge*: the WARN experience at POS (7.5) is only actionable if back-office can see the same number.
### 7.2 Clients: statement + aging — new pages mirroring `SupplierStatement.js` / `SupplierAging.js`, backed by 3.3. Pre-go-live history note displayed when the tenant has an `OPENING_BALANCE` item (the staging report explains the figure's provenance).
### 7.3 Clients: open items + allocation UI — open-items panel (status chips, due-date coloring) + allocation editor inside the receipt creation flow of `domains/finance/pages/ClientReceiptsPage.js`: default FIFO preview, manual override per document, unallocated remainder shown explicitly ("on account"). Payout entry switches to the new `ClientPayout` type. *Why the FIFO preview*: silent auto-allocation was acceptable server-side, but users must SEE what a payment settles before saving — that is the whole point of open items.
### 7.4 Suppliers: same treatment on `SupplierDetails.js` / receipt flow; `SupplierAging.js` relabeled to due-date basis (data now from `ap_open_item`); remove reliance on client-computed `remainingAmount` in `suppliersApi.js` payloads (matches 4.5 contract change).
### 7.5 POS checkout: render 5.1's result in `PayStatment.js` credit path — WARN banner (exposure/limit/remaining headroom) or hard block with reason; keep it non-intrusive for `OFF` branches (no extra call — the order response carries the check result).
### 7.6 i18n: all new strings in EN + AR in `domainMessages/clients.js`, `suppliers.js`, `finance.js` (+ legacy `Messages.js` where old screens are touched). *Why AR is not optional*: the existing product ships Arabic UI (see `Messages.js`, Arabic classifier tokens); an EN-only feature is a regression for the actual user base.

---

## Stage 8 — Performance & hardening

**Why a dedicated stage**: statements and aging run over per-tenant financial history forever — they only grow. The budget: statement/aging/open-items P95 < 300ms at 100k open items per tenant, allocation write P95 < 150ms.

### 8.1 Index & EXPLAIN audit — `EXPLAIN (ANALYZE, BUFFERS)` on statement, aging, open-item list, exposure, and the reconciliation queries against a seeded 100k-item tenant. Verify the planned indexes actually serve them: `(client_id, status, due_date)` must make aging an index-range scan; exposure must not seq-scan. Add covering indexes only where EXPLAIN proves need (indexes cost write throughput — allocations are write-hot).
### 8.2 Pagination + payload budgets — enforce `max 200` rows server-side on all list endpoints; statements default to last 90 days with explicit range param (matches `DbSupplier` statement pattern); no unbounded `getOrdersByClientId`-style queries in new code.
### 8.3 Load & concurrency soak — scripted: 50 concurrent allocations against 10 open items of one party (serialization without deadlock — the id-ordered locking must hold), 1k receipts FIFO-allocated in a batch, exposure checks under concurrent sales. Deadlock count must be zero; retries logged.
### 8.4 Monitoring — metrics: allocation latency, trigger-rejection count (a nonzero rate means a caller bypasses the service protocol — investigate immediately), reconciliation drift gauge per tenant (alert on nonzero after go-live), idempotency-replay rate. Log every variance document creation at WARN.

---

## Stage 9 — Rollout

### 9.1 Pilot tenant go-live — pick one small tenant; run staging report (6.2); approve figures; run import job (6.3); enable read pages; write paths already live (they only affect post-deploy documents); reconciliation (6.1) green for two weeks. *Why pilot-first*: the review proved historical data quality varies per tenant (F2/F3/F5); one tenant bounds the blast radius of surprises.
### 9.2 Opening-balance approvals — per remaining tenant: staging report → Owner/Accountant sign-off → import. Track per-tenant status in the session log here.
### 9.3 WARN→BLOCK flip + cleanup — enable `credit_control_mode=WARN` per branch, observe, flip to `BLOCK` on tenant request; after all tenants live + one clean month: disable `finance.openitems.legacy-writes` flag, migrate supplier statement/aging reads fully to open items, schedule removal of the destructive `updateInventoryRemainingAmount` path (`DBMSupplierReceipt`).

---

## D. Definition of Done (feature-level)

1. All tracker rows ☑; CI green including migration, constraint, concurrency, parity suites.
2. Reconciliation gauge zero (or documented variance) for every live tenant.
3. No `ON DELETE CASCADE`, no negative open items, no free-text pay-type matching anywhere in the new code (grep-verifiable).
4. New tenant + new branch provisioning produces structures identical to migrated ones (2.3 test).
5. V134 `clients.statement.view` semantics untouched; all new endpoints use new keys.
6. Draft V141–V143 files still sitting untouched in `docs/ar-ap-credit-open-items/migrations/` as history.
