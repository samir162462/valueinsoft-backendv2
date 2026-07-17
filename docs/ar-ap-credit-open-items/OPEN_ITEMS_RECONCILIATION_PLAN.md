# Open Items ↔ GL Reconciliation Plan

Companion to `OPEN_ITEMS_MIGRATION_REVIEW.md`. Defines what feeds the control accounts today, why raw equality cannot hold, the reconciliation queries, and the acceptance gate that any backfill must pass.

---

## 1. What actually posts to the control accounts today

### 1100 Accounts Receivable (`pos.receivable` mapping)

| Source | Adapter | Direction | Has customerId? |
|---|---|---|---|
| POS credit sale (payment method normalizes to `receivable`) | `FinancePosPostingAdapter` | debit 1100 | yes (when order has clientId; walk-in credit possible) |
| Client receipt (`customer_receipt`, amount > 0 only) | `FinancePaymentPostingAdapter.postCustomerReceipt` | credit 1100 | yes |
| Billing balance settlement | `FinancePaymentPostingAdapter.postBillingBalanceSettlement` | credit 1100 | **no** |
| Billing refund reopening a receivable | `FinancePaymentPostingAdapter` (~line 322) | debit 1100 | **no** |
| Reconciliation source `customer` | `FinanceReconciliationService` line 774 | n/a (reads) | — |

Not posted at all: negative `supportExChange` client receipts (`enqueueClientReceipt` skips `amount <= 0`), and any receipt created before finance posting was wired up (posting requests are matched by `source_id = 'client-receipt-<crId>'` — absence is detectable).

### 2100 Accounts Payable (`purchase.payable` mapping)

| Source | Adapter | Direction |
|---|---|---|
| Purchase receipt with remaining amount | `FinancePurchasePostingAdapter` (via `enqueuePurchaseInventoryTransaction`) | credit 2100 |
| Supplier payment (`supplier_payment`, amountPaid > 0 only) | `FinancePaymentPostingAdapter` | debit 2100 |
| Supplier return (`supplier_return`) | purchase module | debit 2100 (when unpaid) |

Note 2110 `Client Trade-In Payables` is a separate control account with its own subledger (V133) — it is already reconcilable and out of scope here.

## 2. Why `SUM(open items) = control account` cannot hold without segregation

1. Billing settlements post to 1100 with no customer (F9) — they belong to a different economic relationship (platform billing) and must be excluded via journal-line source filters.
2. Negative client receipts exist in `ClientReceipts` but not in the GL (F8).
3. Pre-finance-module history (journals begin at finance go-live per tenant, V49–V57) — documents older than the first fiscal period have no GL counterpart.
4. Integer/MONEY truncation (F4) introduces sub-unit differences between service-computed values and stored legacy values.
5. Bounce-back refunds post as cash reversals even for credit orders (F6), so 1100 may retain receivable that operationally no longer exists.

Consequently the reconciliation target is: **explained equality** — `subledger total = control balance − documented exclusions ± approved variance`, never raw equality.

## 3. Reconciliation queries (per tenant schema `c_<companyId>`)

These are read-only staging queries. Table names for journal storage follow the V50 finance core (`finance_journal_entry`, `finance_journal_line`, `finance_account` — verify exact names against `DbFinanceJournal` before running; they are tenant-scoped via the same identifier helper).

### 3.1 AR control balance, segregated by source

```sql
-- 1100 balance split into client-attributable vs billing-platform postings
SELECT
    CASE
        WHEN je.source_module = 'payment' AND je.source_type IN ('billing_balance_settlement', 'billing_refund')
            THEN 'platform_billing'
        ELSE 'client_ar'
    END AS bucket,
    SUM(jl.debit_amount - jl.credit_amount) AS balance
FROM c_<id>.finance_journal_line jl
JOIN c_<id>.finance_journal_entry je ON je.journal_entry_id = jl.journal_entry_id
JOIN c_<id>.finance_account a ON a.account_id = jl.account_id
WHERE a.account_code = '1100'
  AND je.status = 'posted'
GROUP BY 1;
```

### 3.2 Proposed AR subledger totals (staging view, no inserts)

```sql
-- Per-client staging balance from operational data, with quality flags
WITH credit_orders AS (
    -- one UNION ALL branch per PosOrder_<branchId> table
    SELECT "clientId" AS client_id, <branchId> AS branch_id,
           COALESCE("orderTotal",0)::numeric AS order_total,
           COALESCE("orderBouncedBack",0) AS bounced_back,
           lower(COALESCE("orderType",'')) AS order_type
    FROM c_<id>."PosOrder_<branchId>"
    WHERE lower(COALESCE("orderType",'')) IN ('credit', 'later', 'debt', 'receivable')
       OR lower(COALESCE("orderType",'')) LIKE 'credit%'
),
receipts AS (
    SELECT "clientId" AS client_id,
           SUM(amount::numeric) FILTER (WHERE amount::numeric > 0) AS paid_in,
           SUM(amount::numeric) FILTER (WHERE amount::numeric < 0) AS paid_out,
           COUNT(*) AS receipt_count
    FROM c_<id>."ClientReceipts"
    GROUP BY 1
)
SELECT
    co.client_id,
    SUM(co.order_total)                          AS credit_order_total,
    COALESCE(r.paid_in, 0)                       AS receipts_in,
    COALESCE(r.paid_out, 0)                      AS receipts_out,      -- negative; NOT in GL
    SUM(co.order_total) - COALESCE(r.paid_in, 0) AS staged_balance,
    BOOL_OR(co.bounced_back <> 0)                AS has_bounce_backs,  -- totals mutated
    COUNT(*) FILTER (WHERE co.client_id IS NULL) AS unattributed_orders
FROM credit_orders co
LEFT JOIN receipts r ON r.client_id = co.client_id
GROUP BY co.client_id, r.paid_in, r.paid_out
ORDER BY staged_balance DESC;
```

Rows with `has_bounce_backs = true`, any negative `staged_balance`, or clients whose receipts exceed credit orders (advances) go to the manual-review queue, never auto-import.

### 3.3 AP control vs staged supplier totals

```sql
-- 2100 balance
SELECT SUM(jl.credit_amount - jl.debit_amount) AS ap_control_balance
FROM c_<id>.finance_journal_line jl
JOIN c_<id>.finance_journal_entry je ON je.journal_entry_id = jl.journal_entry_id
JOIN c_<id>.finance_account a ON a.account_id = jl.account_id
WHERE a.account_code = '2100' AND je.status = 'posted';

-- Staged AP from the three operational copies, exposing drift between them
SELECT
    l.supplier_id,
    l.branch_id,
    SUM(l.remaining_amount)::numeric            AS ledger_remaining,      -- modern rows
    s."supplierRemainig"::numeric               AS supplier_running_total, -- mutable copy
    lt.legacy_remaining                          AS legacy_remaining,      -- pre-modern rows
    SUM(l.remaining_amount)::numeric
      - s."supplierRemainig"::numeric           AS drift_ledger_vs_supplier
FROM c_<id>.inventory_stock_ledger l
JOIN c_<id>.supplier_<branchId> s ON s."supplierId" = l.supplier_id
LEFT JOIN LATERAL (
    SELECT SUM("RemainingAmount")::numeric AS legacy_remaining
    FROM c_<id>."InventoryTransactions_<branchId>" t
    WHERE t."supplierId" = l.supplier_id
      AND t."RemainingAmount" > 0
      AND NOT EXISTS (              -- exclude modern dual-written rows
          SELECT 1 FROM c_<id>.inventory_stock_ledger l2
          WHERE l2.supplier_id = t."supplierId"
            AND l2.movement_type = 'PURCHASE_RECEIPT'
            AND l2.created_at = t."time")
) lt ON TRUE
WHERE l.movement_type = 'PURCHASE_RECEIPT'
  AND l.supplier_id > 0
GROUP BY l.supplier_id, l.branch_id, s."supplierRemainig", lt.legacy_remaining;
```

The dual-write exclusion above is heuristic (timestamp join); it exists to *measure* the overlap, and its imprecision is itself a reason the AP backfill cannot be automatic.

### 3.4 Receipt-to-ledger allocation proof (AP)

```sql
-- Do supplier receipts fully explain the drop in remaining on each ledger row?
SELECT l.stock_ledger_id,
       l.trans_total::numeric,
       l.remaining_amount::numeric,
       COALESCE(SUM(r."amountPaid"::numeric), 0) AS receipts_total,
       l.trans_total::numeric - l.remaining_amount::numeric
         - COALESCE(SUM(r."amountPaid"::numeric), 0) AS unexplained_delta
FROM c_<id>.inventory_stock_ledger l
LEFT JOIN c_<id>."supplierReciepts" r ON r."transId" = l.stock_ledger_id
WHERE l.movement_type = 'PURCHASE_RECEIPT' AND l.supplier_id > 0
GROUP BY l.stock_ledger_id, l.trans_total, l.remaining_amount
HAVING l.trans_total::numeric - l.remaining_amount::numeric
       - COALESCE(SUM(r."amountPaid"::numeric), 0) <> 0;
```

Rows returned here have unverifiable payment history (F3) and are import-blocked pending manual review.

### 3.5 Ongoing reconciliation (post go-live, scheduled)

```sql
-- Subledger vs control, both segregated: must be zero after go-live
SELECT
    (SELECT COALESCE(SUM(remaining_amount),0) FROM c_<id>.ar_open_item
      WHERE status IN ('OPEN','PARTIALLY_SETTLED')) AS ar_subledger,
    (SELECT COALESCE(SUM(jl.debit_amount - jl.credit_amount),0)
       FROM c_<id>.finance_journal_line jl
       JOIN c_<id>.finance_journal_entry je USING (journal_entry_id)
       JOIN c_<id>.finance_account a ON a.account_id = jl.account_id
      WHERE a.account_code = '1100' AND je.status='posted'
        AND je.posting_date >= :golive
        AND NOT (je.source_module='payment'
                 AND je.source_type IN ('billing_balance_settlement','billing_refund'))
    ) + :approved_opening_ar AS ar_control_attributable;
```

Wire this into the existing `FinanceReconciliationService` source-item machinery (V54/V56) as new sources `ar_open_items` / `ap_open_items` so failures surface in the existing reconciliation UI rather than a new tool.

## 4. Acceptance gate

No opening balance is inserted for a party unless one of:

```text
AR open items (party) = AR control attributable (party-level not available → tenant-level gate)
AP open items = AP control attributable
```

holds at tenant level after exclusions, **or** the residual difference is written to a signed-off variance record (`document_ref = 'OPENING-VARIANCE-<tenant>-<date>'`, notes carrying the approver) before import. The staging report (3.2/3.3/3.4) is the artifact the approver signs. Details of the strategy comparison live in `OPEN_ITEMS_BACKFILL_DECISION.md`.

## 5. Branch and tenant rules for reconciliation

Reconciliation always runs per tenant schema (never cross-schema). Within a tenant, AR is reconciled at company level (clients are company-scoped; branch is a reporting dimension), AP at branch level (supplier masters are per-branch tables, so `supplier_id` is only unique per branch — every AP query must carry `(branch_id, supplier_id)`). The 3.3 query must therefore never join supplier ids across branches, and `ap_open_item` uniqueness must include `branch_id` for exactly this reason.
