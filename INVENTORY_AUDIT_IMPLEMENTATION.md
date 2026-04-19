# Inventory Audit Implementation

## Scope

This document captures the implemented first pass of the inventory audit feature across the real Spring Boot backend and the React frontend.

The implementation adds:

- paginated inventory audit search
- Excel export from backend
- PDF export from backend
- audit UI under the existing `InventoryHistory` screen

## Backend

Real backend root:

- `C:\Web\Backend VLS\valueinsoft-backendv2`

### Added Endpoints

- `POST /api/inventory/audit/search`
- `POST /api/inventory/audit/export/excel`
- `POST /api/inventory/audit/export/pdf`

### Added Files

- `src/main/java/com/example/valueinsoftbackend/Controller/InventoryAuditController.java`
- `src/main/java/com/example/valueinsoftbackend/Service/InventoryAuditService.java`
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/InventoryAudit/DbInventoryAuditReadModels.java`
- `src/main/java/com/example/valueinsoftbackend/Model/Request/InventoryAudit/InventoryAuditSearchRequest.java`
- `src/main/java/com/example/valueinsoftbackend/Model/InventoryAudit/InventoryAuditRow.java`
- `src/main/java/com/example/valueinsoftbackend/Model/InventoryAudit/InventoryAuditSummary.java`
- `src/main/java/com/example/valueinsoftbackend/Model/InventoryAudit/InventoryAuditGroupSummary.java`
- `src/main/java/com/example/valueinsoftbackend/Model/InventoryAudit/InventoryAuditPageResponse.java`

### Dependency Changes

Added to `pom.xml`:

- `org.apache.poi:poi-ooxml`
- `com.openhtmltopdf:openhtmltopdf-pdfbox`

### Query Model

The backend uses tenant-aware SQL through:

- `TenantSqlIdentifiers.inventoryProductTable(companyId)`
- `TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId)`
- `TenantSqlIdentifiers.inventoryStockLedgerTable(companyId)`
- `TenantSqlIdentifiers.inventoryProductTemplateTable(companyId)`

All audit queries are filtered by:

- `companyId`
- `branchId`

Capability enforcement currently uses:

- `inventory.item.read`

### Search Payload

```json
{
  "companyId": 1,
  "branchId": 2,
  "fromDate": "2026-04-01",
  "toDate": "2026-04-19",
  "query": "iphone",
  "productId": null,
  "category": null,
  "major": null,
  "businessLineKey": null,
  "templateKey": null,
  "supplierId": null,
  "lowStockThreshold": 5,
  "lowStockOnly": false,
  "groupBy": "NONE",
  "page": 1,
  "size": 25,
  "sortField": "lastMovementDate",
  "sortDirection": "desc"
}
```

### Search Response

```json
{
  "rows": [],
  "page": 1,
  "size": 25,
  "totalItems": 0,
  "totalPages": 0,
  "summary": {
    "totalRows": 0,
    "totalOpeningQty": 0,
    "totalInQty": 0,
    "totalOutQty": 0,
    "totalClosingQty": 0,
    "totalStockValue": 0,
    "lowStockCount": 0
  },
  "grouping": []
}
```

### Audit Row Fields

Current row output includes:

- `productId`
- `productName`
- `category`
- `branch`
- `openingQty`
- `inQty`
- `outQty`
- `closingQty`
- `unitPrice`
- `totalValue`
- `lastMovementDate`

### Current Behavior Notes

- `category` is currently derived from template display name, then `major`, then legacy `product_type`.
- `openingQty`, `inQty`, `outQty`, `closingQty` are calculated from stock ledger deltas.
- `totalValue` is `closingQty * retail_price`.
- grouping currently supports:
  - `NONE`
  - `CATEGORY`
  - `BRANCH`

### Export Notes

Excel export:

- generated fully on backend
- uses streaming `SXSSFWorkbook`
- includes summary section
- includes filter metadata
- includes formatted header row

PDF export:

- generated fully on backend
- uses HTML string rendered through `OpenHTMLtoPDF`
- includes summary and row table
- hard-limited by `inventory.audit.pdf.max-rows`, default `5000`

## Frontend

Frontend root:

- `C:\Web\ValueINSoft Web\VLS\Valueinsoft`

### Added Files

- `src/domains/inventory/audit/api/inventoryAuditApi.js`
- `src/domains/inventory/audit/hooks/useInventoryAudit.js`
- `src/domains/inventory/audit/components/InventoryAuditToolbar.js`
- `src/domains/inventory/audit/components/InventoryAuditSummaryCards.js`
- `src/domains/inventory/audit/components/InventoryAuditTable.js`
- `src/domains/inventory/audit/InventoryAuditPage.js`

### Updated Files

- `src/domains/inventory/history/InventoryHistoryPage.js`

### UI Placement

The new audit UI is mounted as an `Audit` tab inside the existing `InventoryHistory` page.

This was chosen to reduce rollout risk and avoid new navigation changes.

### Frontend Features

- filter toolbar
- explicit search action
- paginated audit table
- summary cards
- Excel export button
- PDF export button
- grouping badges

## Verification

Completed:

- backend compile passed with JDK 21
- frontend `npm run build` passed

Known unrelated issue still present:

- `src/PointOfSale/Componens/ShiftSalesPos.js:757`

## Remaining Gaps

This is a strong first implementation, but not the final enterprise shape yet.

Remaining improvements:

- move audit permission from generic `inventory.item.read` to dedicated audit/export capabilities
- add async export jobs for very large reports
- add true server-side movement breakdown output if required per row
- add company logo and richer PDF branding/template support
- add branch name resolution fallback if branch metadata changes
- add DB indexes for extra search columns if audit volume grows
- consider cursor-based or keyset pagination for very large audit datasets
- add automated tests for:
  - date validation
  - summary math
  - grouping
  - export generation
  - permission enforcement

## Restart Requirement

The backend must be restarted from:

- `C:\Web\Backend VLS\valueinsoft-backendv2`

so the new `/api/inventory/audit/*` endpoints become available.
