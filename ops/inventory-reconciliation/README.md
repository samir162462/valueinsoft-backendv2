# Inventory reconciliation and legacy-writer canary runbook

This Phase 0 control is read-only. It must never update a balance, ledger row, movement, serialized unit, supplier total, or finance entry.

## Snapshot endpoint

```http
GET /api/inventory/reconciliation/{companyId}/{branchId}?limit=200
```

Required capability: `inventory.item.read` in the requested company and branch. `limit` is bounded to `1..1000` and only discrepancy rows are returned.

Possible statuses:

- [ ] `MATCHED`: all observed product keys match the current comparisons.
- [ ] `DRIFT`: one or more comparisons differ; preserve the response as evidence and investigate the originating commands.
- [ ] `SCHEMA_DRIFT`: required tenant inventory objects are absent; repair tenant schema through Flyway before running product-level reconciliation.

The snapshot compares:

- `balanceQuantity` against the sum of the legacy stock ledger.
- `balanceQuantity` against the sum of the modern stock movement table.
- `balanceQuantity` against serialized units in `AVAILABLE` or `RESERVED` state when serialized units exist for the product.

Legacy tenants may legitimately have incomplete modern movement history. A reported difference quantifies that gap; it is not authorization to overwrite balances or delete history.

## Evidence capture checklist

- [ ] Record deployment version, Flyway version, company, branch, UTC capture time, and authenticated operator.
- [ ] Save the complete JSON response in the controlled incident/change record.
- [ ] Confirm `truncated=false`; otherwise recapture with a higher limit or page through an approved operational export.
- [ ] Compare the same branch before and after the canary window.
- [ ] Attribute every accepted difference to a source command/document and an owner.
- [ ] Do not enable a repair until finance/accounting and inventory owners approve the invariant and expected values.

## Legacy writer controls

All writers remain enabled by default. Each writer supports a global switch and exact canary scopes formatted as comma-separated `companyId:branchId` pairs.

| Writer | Global environment variable | Canary disabled scopes |
|---|---|---|
| Generic transaction | `VLS_INVENTORY_LEGACY_GENERIC_TRANSACTION_ENABLED` | `VLS_INVENTORY_LEGACY_GENERIC_TRANSACTION_DISABLED_SCOPES` |
| Standalone serialized stock-in | `VLS_INVENTORY_LEGACY_SERIALIZED_STOCK_IN_ENABLED` | `VLS_INVENTORY_LEGACY_SERIALIZED_STOCK_IN_DISABLED_SCOPES` |
| Damage hard delete | `VLS_INVENTORY_LEGACY_DAMAGE_HARD_DELETE_ENABLED` | `VLS_INVENTORY_LEGACY_DAMAGE_HARD_DELETE_DISABLED_SCOPES` |

Example canary configuration:

```properties
VLS_INVENTORY_LEGACY_GENERIC_TRANSACTION_DISABLED_SCOPES=10:20,10:21
VLS_INVENTORY_LEGACY_SERIALIZED_STOCK_IN_DISABLED_SCOPES=10:20
VLS_INVENTORY_LEGACY_DAMAGE_HARD_DELETE_DISABLED_SCOPES=10:20
```

Disabled calls return `410 Gone`, do not invoke the writer service, and increment the legacy writer metric with `outcome="blocked"`.

## Canary exit checklist

- [ ] Capture a clean pre-canary reconciliation snapshot.
- [ ] Confirm a supported replacement workflow exists for every disabled operation.
- [ ] Confirm legacy-writer request rate is zero for the agreed observation window before disabling.
- [ ] Disable one approved branch scope, not the global switch.
- [ ] Monitor `valueinsoft_inventory_legacy_writer_requests_total` by `endpoint` and `outcome`.
- [ ] Exercise receive, sale, return, damage settlement/reversal, and inventory audit for the canary branch.
- [ ] Capture the post-canary snapshot and compare it with the pre-canary evidence.
- [ ] Roll back by removing the scope if blocked traffic appears or reconciliation worsens.
- [ ] Use the global `..._ENABLED=false` switch only after every production scope has passed and legacy traffic remains zero.

## Stop conditions

- [ ] Any cross-tenant or cross-branch result.
- [ ] New negative or unexplained balance difference.
- [ ] Serialized unit difference without an owned explanation.
- [ ] Supported workflow unavailable while the legacy writer is blocked.
- [ ] Finance or supplier/control-ledger difference introduced during the canary.

On a stop condition, restore the affected writer flag, preserve evidence, and open an incident. Do not manually edit inventory tables.
