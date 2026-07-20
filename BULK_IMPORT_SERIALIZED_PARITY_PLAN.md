# Bulk Product Import — Serialized (IMEI/SERIAL) Parity Plan

**Status:** IMPLEMENTED (v1) — 2026-07-20. Decisions: grouping = name+category+tracking; payment = FULL/LATER batch-level; serialized rows require supplier (rejected at validation); QUANTITY rows keep current path.

## Implementation summary

| Change | File |
|---|---|
| Flyway: allow `RECEIVE` row action | `db/migration/V162__product_import_serialized_receive_action.sql` |
| CSV columns `imei`, `serial_number`, `unit_cost` | `ProductImportTemplateService` |
| Derived context (`_tracking_type`, `_unit_identifier`, `_group_key`, `_unit_cost`) persisted in `normalized_data` | `ParsedProductImportRow`, `ProductImportRepository` |
| IMEI Luhn check, barcode→IMEI fallback (warning), supplier required, group-aware sku/barcode dup check, in-file + in-DB identifier dup checks, group conflict warnings, RECEIVE action assignment | `ProductImportValidationService` |
| Active-unit identifier lookup | `ProductImportRepository.activeSerializedIdentifiers` |
| Confirm: groups serialized rows, creates/reuses product, calls `InventoryProductReceiptService.receiveProduct` (units + stock + ledger + supplier totals + AP + finance) with idempotency key `bulk-import:{batchId}:{hash}` | `ProductImportConfirmService` |
| Confirm payment body (FULL/LATER + method) | `ProductImportConfirmRequest`, `InventoryProductImportController` |
| Frontend: payment section in confirm dialog + API pass-through + AR/EN i18n | `BulkProductImportPage.js`, `productImportApi.js`, `messageOverrides.js` |

Notes: no schema change to staging data (rides in `normalized_data` JSONB). Old validated batches confirm with the old behavior (no computed keys). IMEIs must pass the 15-digit Luhn check — same rule as single Add Product — so randomly generated test IMEIs will mostly be rejected as `IMEI_INVALID`.

**Date:** 2026-07-20
**Problem:** Bulk-imported serialized products (e.g. 1000 rows, repeated names, one IMEI per row) do not appear in inventory as units under one product. Bulk import also skips all stock/financial side-effects that single "Add Product" performs.

---

## 1. What happens today (root-cause trace)

### Single add product (the correct flow)
`InventoryProductReceiptService.receiveProduct(...)` does, in one transaction:

1. Idempotency lock + payload hash check.
2. Validate template, tracking type, attributes, **serializedUnits** (IMEI Luhn 15-digit, count == quantity, duplicates).
3. Resolve product: `RECEIVE_EXISTING_PRODUCT` (find + lock) or `CREATE` (`DbPosProductCommandRepository.addProduct`).
4. `increaseBranchStockBalance` (+qty).
5. Insert receipt ledger row (`insertReceiptLedger`) with cost/payment/remaining.
6. **Insert one `inventory_product_unit` row per IMEI/serial** (`insertSerializedUnitsIfRequired`) + one `STOCK_IN` stock movement per unit.
7. Supplier purchase totals update / AP open item for remaining amount / client trade-in receipt.
8. Finance posting enqueue + legacy inventory transaction.

### Bulk import confirm (the broken flow)
`ProductImportConfirmService.confirm(...)` per eligible row calls only:

```
productCommandRepository.addProduct(toProduct(row), branchId, companyId)
```

`addProduct` (DbPosProductCommandRepository:35) for serialized tracking:
- forces `stockQuantity = 0` (line 65) → **no stock balance**
- **never creates `inventory_product_unit` rows** → nothing under "serialized products" in inventory
- no stock movements, no receipt ledger, no supplier totals, no AP open item, no finance posting

### Repeated names
- CSV has **no `imei` / `serial_number` column** (`ProductImportTemplateService.COLUMNS`). Users put the IMEI in `barcode`.
- Validation dedupes by `sku`/`barcode` only; repeated `product_name` rows each become independent `INSERT` actions.
- Result: 1000 rows → 1000 separate catalog products, each with qty 0 and zero units, instead of **1 product per unique name with 1000 units**.

---

## 2. Target behavior (parity with single add)

> One bulk import of N serialized rows must be equivalent to N single "Add Product" receipt calls, with same-name rows collapsing into one product + N serialized units.

| Concern | Single add (today) | Bulk (today) | Bulk (target) |
|---|---|---|---|
| Product row | ✔ | ✔ (one per CSV row) | ✔ one per **unique product**, rows grouped |
| Serial units (`inventory_product_unit`) | ✔ | ✘ | ✔ one per row (IMEI/serial) |
| Stock balance | ✔ | ✘ (serialized) | ✔ = unit count |
| Stock movements (STOCK_IN per unit) | ✔ | ✘ | ✔ |
| Receipt ledger | ✔ | ✘ | ✔ per product group |
| Supplier totals / AP open item | ✔ | ✘ | ✔ |
| Finance posting + legacy txn | ✔ | ✘ | ✔ |
| IMEI validation (Luhn, dup check) | ✔ | ✘ | ✔ at validate stage |
| Idempotency | ✔ | batch lock only | ✔ per group via batchId-derived key |
| Payment (FULL/PARTIAL/LATER) | ✔ | ✘ | ✔ batch-level payment settings ("bulk payment") |

---

## 3. Design

### 3.1 CSV template changes
- Add optional column **`imei`** and **`serial_number`**. Rules:
  - `unit_code=IMEI` (or tracking IMEI): `imei` required per row; `barcode` stays a product-level barcode (same for all rows of a group).
  - `unit_code=SERIAL`: `serial_number` required per row.
  - Backward compatibility: if `imei` column absent and tracking is IMEI, fall back to treating `barcode` as the IMEI (current user behavior) with a WARNING.
- Add optional payment columns per row: `unit_cost` (defaults to `purchase_price`), — payment option/method are batch-level (see 3.4), not per row.

### 3.2 Row grouping (validate stage)
- Group serialized rows by normalized `product_name` + `category` + `unit_code`/tracking (discussion: name-only? or name+brand?).
- First row of a group defines product master data; conflicting prices/fields in the same group → WARNING (use first) or INVALID (discussion).
- Per-group checks: IMEI Luhn + 15-digit, duplicate IMEI within file → DUPLICATE row, IMEI already active in `inventory_product_unit` → INVALID (reuse `findByCompanyScanCode` logic).
- Duplicate-barcode-in-file check must be **relaxed for serialized groups** (same product barcode legitimately repeats across rows of a group).
- Non-serialized (QUANTITY) rows: unchanged behavior.

### 3.3 Confirm stage — reuse the receipt pipeline
Refactor so bulk and single share one code path:

- Extract the core of `InventoryProductReceiptService.receiveProduct` into an internal method callable without the HTTP idempotency envelope, e.g. `receiveProductInternal(actor, ReceiptCommand)` (product resolve → stock balance → ledger → units → movements → supplier/AP → finance).
- `ProductImportConfirmService.confirm`:
  - For each **product group** (own transaction, as today per row):
    1. Resolve product: existing by barcode → `RECEIVE_EXISTING_PRODUCT`; else create.
    2. Build `serializedUnits` from group rows; `quantity = units.size()` (or `opening_stock_quantity` for QUANTITY products).
    3. Call the shared receipt pipeline with idempotency key `bulk-import:{batchId}:{groupKey}` → safe re-confirm after partial failure.
    4. Mark all rows of the group IMPORTED with the productId; failure marks the whole group FAILED.
- QUANTITY products keep opening-stock semantics but go through the same pipeline so ledger/finance are consistent (discussion: or keep current lighter path for QUANTITY?).

### 3.4 Bulk payment
- Extend confirm request (frontend "التحقق من الملف"/confirm dialog) with batch-level payment settings: `paymentOption` (FULL/PARTIAL/LATER, default LATER), `paymentMethod`, `paidAmount` allocation strategy (discussion: proportional per supplier vs FULL/LATER only for v1 — recommend **FULL or LATER only** in v1, no partial allocation math).
- Supplier resolved per row (`supplier_name`); groups without supplier → acquisition allowed only with LATER? (discussion — single flow requires supplierId > 0 for supplier receipts).
- Remaining amounts create AP open items exactly like single flow.

### 3.5 DB (Flyway only)
- `V###__product_import_serialized_units.sql`:
  - `inventory_product_import_row`: add `group_key TEXT`, `imei TEXT`, `serial_number TEXT`, `unit_cost NUMERIC` (staging-only, nullable).
  - `inventory_product_import_batch`: add `payment_option TEXT`, `payment_method TEXT`.
  - Indexes on `(batch_id, group_key)`.
- No changes to final inventory tables — they already support everything (units, movements, ledger).

### 3.6 Frontend (Zag branch — BulkProductImport page)
- Template download includes new columns + updated help text.
- Preview table: group indicator (e.g. "iPhone 15 Pro — 40 units"), IMEI column, group-level errors.
- Confirm dialog: payment section (option/method) mirroring single Add Product payment UI.
- Result summary: products created vs units received.

### 3.7 Mobile (react-native POS)
- No change expected; it consumes products/units via existing endpoints. Verify serialized units appear in POS after bulk import (test only).

---

## 4. Rollout / test plan
1. Unit tests: grouping, IMEI validation, fallback barcode→imei, idempotent re-confirm.
2. Integration test: CSV 10 rows, 2 names, IMEI tracking → expect 2 products, 10 units AVAILABLE, stock balance 5+5, 10 STOCK_IN movements, 2 ledger rows, supplier totals updated, AP open item when LATER.
3. Re-run the user's real file `products_import_1000_imei_repeated_names_fresh.csv` on a test branch.
4. Regression: QUANTITY-only CSV must behave as before (plus ledger consistency if 3.3 applies).

---

## 5. Open questions (need your call)

1. **Grouping key**: `product_name` only, or `product_name + category + brand`? What if same name has different prices across rows?
2. **QUANTITY rows**: also route through receipt pipeline (ledger + finance parity) or keep current opening-balance path?
3. **Bulk payment v1 scope**: FULL / LATER only, or support PARTIAL with per-supplier paid-amount allocation?
4. **No-supplier rows**: reject serialized rows without `supplier_name`, or allow with a default "bulk import" source and no AP?
5. **Backward compat**: keep the `barcode-as-IMEI` fallback permanently or warn-then-remove after migration period?
6. **Existing bad data**: the already-imported 1000 zero-stock products — write a cleanup/backfill script, or delete-and-reimport?
