# Open Items Backfill — Decision Record

Question: how should historical AR/AP balances enter the new open-item subledger?
Context: draft V143 auto-backfilled both sides; the review (`OPEN_ITEMS_MIGRATION_REVIEW.md` B2/B3) found the source data unfit for automatic import. Facts referenced below (F1–F14) are defined in the review document.

---

## 1. Option comparison

### Option 1 — Open items only from go-live date

New documents create open items; history stays in the old statements. No data-quality risk and zero migration effort, but party balances are wrong on day one (a client owing money from before go-live shows zero exposure), credit control is meaningless for months, and statements show a discontinuity. Rejected as sole strategy; correct as the *document-level* baseline.

### Option 2 — Import approved opening balances per party

One `OPENING_BALANCE` open item per party, amount taken from a reviewed and signed-off figure, not from automatic classification. Deterministic, auditable (approver recorded on the document), aging imperfect for the opening item (single due date) but honest. Requires human effort proportional to party count — bounded by using the staging report to pre-fill figures. This is the industry-standard cutover for subledger introductions.

### Option 3 — Staging reconciliation report requiring manual approval

Not an import at all: run the queries in `OPEN_ITEMS_RECONCILIATION_PLAN.md` §3 into a staging area (or report), expose per-party balances with quality flags (`has_bounce_backs`, negative balances, unexplained payment deltas, ledger-vs-supplier drift), and require explicit approval per tenant. By itself it imports nothing — it is the *gate* that makes Option 2 safe.

### Option 4 — Reconstruct invoice-level history where source data is reliable

Only one source qualifies even partially: modern `PURCHASE_RECEIPT` stock-ledger rows whose payment history is fully explained by linked `supplierReciepts` (`unexplained_delta = 0` in reconciliation query 3.4) — those are genuine single-line documents (F1) with verifiable settlement. AR does not qualify at all: order totals mutate on returns (F6), pay types are unproven free text (F5), receipts are unlinked, can be negative, and are partially absent from GL (F7/F8). Legacy AP rows fail on client-maintained `remaining_amount` (F3) and dual-write ambiguity (F2).

## 2. Recommendation

**Options 3 + 2 combined, with Option 4 as an opt-in refinement for provably clean AP rows, and Option 1 as the baseline for all new documents.** Concretely:

1. Go-live date `G` per tenant. From `G`, every credit sale / unpaid purchase creates a document-level open item (Option 1).
2. Before `G`, generate the staging reconciliation report per tenant (Option 3). No SQL inserts anything in this step.
3. An authorized reviewer (Owner or Accountant of the tenant, plus platform-side sign-off) approves per-party figures — editing where the flags show unreliable data (Option 2).
4. Approved AR figures import as `OPENING_BALANCE` items with the reviewer recorded in `created_by`/`notes` and an explicit reviewer-chosen `branch_id` (never `MIN(branch_id)`).
5. AP rows passing the 3.4 proof (`unexplained_delta = 0`, modern rows only, `movement_type='PURCHASE_RECEIPT'`, deduplicated against legacy dual-writes) may import at document level (Option 4); everything else rolls into the party's approved opening balance.
6. Advances and unapplied credits found during staging (clients whose receipts exceed credit orders, suppliers overpaid) are imported as unapplied credit documents (revised schema §4), not negative open items.

## 3. Gating rule (hard requirement)

No historical balance is inserted automatically unless, per tenant and after the source segregation defined in `OPEN_ITEMS_RECONCILIATION_PLAN.md` §3.1/§3.5:

```text
AR open items = AR control account (client-attributable portion)
AP open items = AP control account
```

or the difference is recorded as an explicit, signed-off variance document (`OPENING-VARIANCE-<tenant>-<date>`, approver in `notes`) *before* the import transaction commits. The import job must compute both sides inside the same transaction as the insert and abort on unexplained variance. Tenants whose GL finance module was enabled late (journals younger than operational history) will *always* route through the variance path — this is expected and documented, not an error.

## 4. Consequences for the draft migrations

`V143` as drafted is withdrawn: its AP backfill becomes the staging queries + Option 4 import job (application-managed, transaction-gated — not a fire-and-forget Flyway migration), its AR backfill is replaced entirely by the approval workflow, and its capability seeds move to a rewritten seed migration using the new keys (review B8). Flyway remains the vehicle for *structures* (per the project rule that all DB changes go through Flyway); *opening balances* are data operations executed by the gated import job so they can run per tenant at each tenant's go-live date rather than at deploy time for all tenants simultaneously.

## 5. Decision

```text
SAFE AFTER REQUIRED CHANGES
```

Structures (revised V141/V142 per `OPEN_ITEMS_REVISED_SCHEMA_PLAN.md`) may proceed once rewritten; historical balances enter only through the staged, approved, reconciliation-gated process above. The current draft V143 must never run.
