# ValueInSoft POS Module Knowledge (RAG)

Audience: AI assistant, support agent, backend developer.

Purpose: RAG-ready knowledge for the Point of Sale (POS) domain of ValueInSoft. It teaches the assistant what the POS module does, its REST endpoints, required capabilities, request/response models, the sale workflow and its side effects, the shift lifecycle, and the underlying database tables. Chunk this document by `##` sections.

Scope note: ValueInSoft is a multi-tenant ERP/POS platform. A tenant is a company (`companyId`), and a company owns one or more branches (`branchId`). POS is the front-line selling module; it connects to Inventory, Finance, Loyalty, and Shift/Cash management. This document focuses on POS. For raw SQL/schema and migration rules see `VALUEINSOFT_SQL_RAG_KNOWLEDGE.md`.

## POS Module Overview

The POS module lets a cashier ring up sales, manage the product catalog and categories, run promotional offers, open and close cash shifts, handle damaged/returned stock, and adjust inventory. Every POS action is scoped to a `companyId` and a `branchId`, authenticated by a JWT `Principal`, and authorized by a fine-grained capability check before any work happens.

Core POS concepts:

- Order (sale): a receipt with header totals and one or more line items. Selling decrements stock and, for cash sales, records a shift cash movement and queues a finance posting.
- Product: a catalog item. Stock quantity and product identity are tracked separately. High-value items (phones, devices) can be serialized and tracked by IMEI/serial.
- Category: hierarchical grouping of products, stored per branch as JSON, seeded from the assigned business package.
- Offer: a promotional discount/bundle configured per branch.
- Shift: a cashier's cash session with an opening float, cash movements, and a reconciled close.
- Damaged item / bounce-back: adjustments for broken stock or returned sale lines.

## Authorization And Capabilities

Every POS controller method calls `authorizationService.assertAuthenticatedCapability(username, companyId, branchId, capabilityKey)` before doing work. A missing capability or wrong scope is rejected. Capabilities are data-driven (seeded via Flyway into `platform_capabilities` / `role_grants`), never hardcoded only in Java or the frontend.

POS capability keys in use:

- `pos.sale.create` — create/save an order (ring up a sale).
- `pos.sale.read` — read orders, order details, offers, shift orders.
- `pos.sale.edit` — edit sales, bounce back a product, save/delete offers.
- `pos.shift.create` — open/start a shift.
- `pos.shift.read` — read active shift, reconciliation, events, branch shifts.
- `pos.shift.edit` — record cash movement, legacy end-shift.
- `pos.shift.close` — close a shift with reconciliation.
- `pos.shift.force_close` — force-close a shift (manager action).

Related inventory capabilities used by POS-adjacent endpoints:

- `inventory.item.read` — read products, categories, product names, templates, serialized units.
- `inventory.item.create` — create a product or category.
- `inventory.item.edit` — edit a product, change tracking type, edit serialized unit identifiers.
- `inventory.adjustment.create` — add a damaged item, add an inventory transaction, serialized stock-in/transfer.
- `inventory.adjustment.edit` — settle/delete a damaged item.

Scope rule: branch-scoped capabilities require a valid `branchId`; company-wide reads (e.g. main categories, business package) pass `branchId = null`.

## Orders API (`/Order`)

Base path `/Order`. Handled by `OrderController` → `OrderService` / `DbPosOrder`.

- `POST /Order/{companyId}/saveOrder` — create an order. Capability `pos.sale.create`. Body `CreateOrderRequest`. Returns `201` with the new `orderId` (integer). Legacy/simple response shape.
- `POST /Order/v2/pos/{companyId}/orders` — create an order (v2). Capability `pos.sale.create`. Body `CreateOrderRequest`. Returns full `CreateOrderResult`; status `201` on new insert, `200` when an idempotency key matched an existing order (idempotency hit).
- `POST /Order/getOrders` and `POST /Order/{companyId}/getOrders` — list orders for a period. Capability `pos.sale.read`. Body `OrderPeriodRequest` (includes `branchId`). `companyId` may come from path or `?companyId=` query.
- `GET /Order/getOrdersByClientId/{companyId}/{branchId}/{clientId}` — orders for one client. Capability `pos.sale.read`.
- `GET /Order/{companyId}/search/{branchId}?q={receiptNumber}` — find one order by receipt number. Capability `pos.sale.read`. Returns `404` if not found.
- `GET /Order/getOrdersDetailsByOrderId/{companyId}/{branchId}/{orderId}` — line items of an order. Capability `pos.sale.read`.
- `POST /Order/{companyId}/bounceBackProduct` — return/bounce back a sold product. Capability `pos.sale.edit`. Body `BounceBackOrderRequest`.

### CreateOrderRequest fields

`CreateOrderRequest` is a validated record:

- `orderId` (int, ≥0) — 0 for a new order.
- `orderTime` (string) — client timestamp; parsed server-side.
- `clientName` (string) — walk-in name or linked client name.
- `orderType` (string, required) — sale type. Standard cash sale values: `Dirict`, `Direct`, or Arabic `مباشر`. Other types (e.g. credit/client account) skip the cash-movement step.
- `orderDiscount` (int, ≥0) — order-level discount.
- `orderTotal` (int, ≥0) — final total charged.
- `salesUser` (string, required) — cashier username.
- `branchId` (int, positive) — selling branch.
- `clientId` (int, ≥0) — 0 for walk-in; >0 links to a `Client`.
- `orderIncome` (int, ≥0) — profit/margin figure.
- `orderDetails` (list of `OrderItemRequest`, non-empty) — the line items.
- `loyaltyRedemptionId`, `loyaltyPointsRedeemed`, `loyaltyPointsEarned`, `loyaltyDiscountAmount`, `loyaltyNetAmount` — optional loyalty fields.
- `idempotencyKey` (string ≤255, optional) — safe-retry token; UUID recommended. If the same key is replayed, the server returns the original order instead of creating a duplicate.

The `Order` model additionally carries `receiptNumber`, `requestedShiftId`, `totalBouncedBack`, and an `orderDetails` list of `OrderDetails`.

## Sale Workflow And Side Effects

`OrderService.createOrder` runs in a single `@Transactional` unit. Sequence:

1. Validate `companyId` and map the request to an `Order`.
2. Post the sale via `PosSalePostingService.postSale` (the authoritative path), which persists the order header + details and decrements stock. Result is a `CreateOrderResult` with `orderId`, `receiptNumber`, `shiftId`, and `idempotencyHit`.
3. If `idempotencyHit` is true, downstream posting is skipped and the original result is returned (no duplicate stock/finance/cash effects).
4. Loyalty: confirm any redemption (`loyaltyRedemptionId > 0`) and record points earned.
5. Resolve the active `shiftId` (from the result, else look up the branch's active shift).
6. Cash movement: for a standard cash sale (`orderType` in `Dirict`/`Direct`/`مباشر`) with `orderTotal > 0` and an active shift, insert a `CASH_SALE` cash movement referencing the order.
7. Finance: enqueue a POS-sale finance posting *after commit* (so accounting entries only post if the transaction succeeds).

Key invariants:

- Idempotency: never create a second official order for the same `idempotencyKey`. Retries must not duplicate stock, cash, or finance effects.
- Stock and identity are separate: a sale decrements stock balance/units, it does not mutate product master identity.
- Serialized items (IMEI/serial) are consumed as units, not just a quantity decrement.
- Finance postings are enqueued after commit and must keep debits = credits at posting boundaries.

## Products API (`/products`)

Base path `/products`. Handled by `ProductController` → `ProductService` / serialized + template services. Products are the shared catalog behind POS selling and inventory.

- `GET /products/search/{searchType}/{companyId}/{branchId}/{text}/{selectedPageNumber}` — search catalog. Capability `inventory.item.read`. `searchType`: `dir` (free-text words), `comName` (company/product name), `Barcode` (exact barcode). Page size 10.
- `POST /products/search/{searchType}/{companyId}/{branchId}/{text}/filter/{pageNumber}` — filtered search with `ProductFilter` body. `searchType`: `dir`, `comName`, or `allData` (full range).
- `GET /products/{companyId}/{branchId}/{productId}` — get one product. Capability `inventory.item.read`.
- `POST /products/{companyId}/{branchId}/saveProduct` — create a product. Capability `inventory.item.create`. Body `Product`. Returns `201` with `ProductOperationResponse`.
- `PUT /products/{companyId}/{branchId}/editProduct` — edit a product. Capability `inventory.item.edit`.
- `PUT /products/{companyId}/{branchId}/{productId}/tracking` — change tracking type (e.g. quantity ↔ serialized). Capability `inventory.item.edit`. Body `ProductTrackingTypeChangeRequest`.
- `GET /products/{companyId}/{branchId}/{productId}/tracking` — read tracking metadata. Capability `inventory.item.read`.
- `GET /products/PN/{companyId}/{branchId}/{text}` — product name autocomplete. Capability `inventory.item.read`.
- `GET /products/{companyId}/{branchId}/templates` — product templates for the company. Capability `inventory.item.read`.
- `POST /products/export/excel` and `POST /products/export/pdf` — stream a catalog export (`ProductCatalogExportRequest`); filename `inventory-catalog-branch-{branchId}.{ext}`.

Legacy product price fields seen on `PosProduct`: `rPrice` (retail), `lPrice` (last/wholesale), `bPrice` (buy/cost), `serial`, `quantity`, `pState`, `branchId`.

## Categories API (`/Categories`)

Base path `/Categories`. Handled by `CategoryController` → `CategoryService`. Categories group products per branch and are stored as JSON, seeded from the tenant's assigned business package.

- `POST /Categories/{companyId}/{branchId}/saveCategory` — create/update category. Capability `inventory.item.create`. Body `SaveCategoryRequest`.
- `GET /Categories/getCategoryJson/{companyId}/{branchId}` — categories as a list of `CustomPair`. Capability `inventory.item.read`.
- `GET /Categories/getCategoryJsonFlat/{companyId}/{branchId}` — flat JSON string of categories.
- `GET /Categories/getMainMajors/{companyId}` — company-wide main business categories (`MainMajor`). `branchId = null`.
- `GET /Categories/business-package/{companyId}` — the business package assigned to the tenant (`BusinessPackageConfig`), which seeds default categories.

## Offers API (`/Offer`)

Base path `/Offer`. Handled by `OfferController` → `DbPosOffer`. Offers are per-branch promotional discounts/bundles applied at sale time.

- `GET /Offer/{companyId}/{branchId}/offers` — list offers. Capability `pos.sale.read`.
- `POST /Offer/{companyId}/saveOffer` — create/update an offer. Capability `pos.sale.edit`. Body `Offer` (carries `branchId`). Returns `201` with offer id.
- `DELETE /Offer/{companyId}/{branchId}/deleteOffer/{offerId}` — delete an offer. Capability `pos.sale.edit`.

## Damaged Items API (`/DamagedItem`)

Base path `/DamagedItem`. Handled by `DamagedItemController` → `DamagedItemService`. Tracks broken/unsellable stock and its settlement. Note the first path variable is named `companyName` but is the numeric `companyId`.

- `GET /DamagedItem/{companyId}/{branchId}/all` — list damaged items. Capability `inventory.item.read`.
- `POST /DamagedItem/{companyId}/{branchId}/add` — record a damaged item. Capability `inventory.adjustment.create`. Body `CreateDamagedItemRequest`. Returns `202`.
- `PUT /DamagedItem/{companyId}/{branchId}/settle/{DId}` — settle a damaged item. Capability `inventory.adjustment.edit`. Returns `{ "settled": true|false }`.
- `DELETE /DamagedItem/{companyId}/{branchId}/delete/{DId}` — delete a damaged item record. Capability `inventory.adjustment.edit`. Returns `{ "deleted": true|false }`.

## Inventory Transactions & Serialized Units API (`/invTrans`)

Base path `/invTrans`. Handled by `InventoryTransactionController` → `InventoryTransactionService` / `SerializedInventoryService`. Handles stock adjustments and IMEI/serial-tracked units used at POS.

- `POST /invTrans/AddTransaction` — add a stock/inventory transaction. Capability `inventory.adjustment.create`. Body `CreateInventoryTransactionRequest`. Returns `201`.
- `POST /invTrans/transactions` — query transactions. Capability `inventory.item.read`. Body `InventoryTransactionQueryRequest`.
- `POST /invTrans/AddSerializedStockIn` — receive serialized units into stock. Capability `inventory.adjustment.create`. Body `SerializedUnitStockInRequest`. Returns `201`.
- `POST /invTrans/TransferSerializedUnits` — move serialized units between branches. Capability `inventory.adjustment.create` (scoped to source branch). Body `SerializedUnitTransferRequest`.
- `GET /invTrans/SerializedScan/{companyId}/{branchId}/{scanCode}` — look up a serialized unit by scanned IMEI/serial/barcode. Capability `inventory.item.read`.
- `GET /invTrans/SerializedUnits/{companyId}/{branchId}/{productId}?status=` — list a product's serialized units, optionally filtered by `ProductUnitStatus`. Capability `inventory.item.read`.
- `PUT /invTrans/SerializedUnits/{companyId}/{branchId}/{productId}/{productUnitId}/identifier` — update a unit's IMEI/serial/condition. Capability `inventory.item.edit`.
- `GET /invTrans/SerializedAvailability/{companyId}/{branchId}/{productId}` — count available serialized units. Capability `inventory.item.read`.
- `GET /invTrans/StockMovements/{companyId}/{branchId}/{productId}?limit=50` — product movement history. Capability `inventory.item.read`.
- `GET /invTrans/SerializedUnitMovements/{companyId}/{branchId}/{productUnitId}?limit=50` — one unit's movement history. Capability `inventory.item.read`.

## Shift And Cash Management API (`/shiftPeriod`)

Base path `/shiftPeriod`. Handled by `ShiftController` → `ShiftService`. A shift is a cashier's cash session; sales during the session are attributed to it and cash is reconciled at close.

Modern lifecycle endpoints:

- `POST /shiftPeriod/{companyId}/open` — open a shift with an opening float and cashier. Capability `pos.shift.create`. Body `OpenShiftRequest`. Idempotent: returns the existing open shift if one is already active. Returns `201`.
- `GET /shiftPeriod/{companyId}/{branchId}/active` — the currently active shift, or `204` if none. Capability `pos.shift.read`.
- `GET /shiftPeriod/{companyId}/shift/{shiftId}` — one shift enriched with orders/totals. Capability `pos.shift.read`.
- `GET /shiftPeriod/{companyId}/{branchId}/shifts` — all shifts for a branch. Capability `pos.shift.read`.
- `GET /shiftPeriod/{companyId}/shift/{shiftId}/reconciliation` — reconciliation data for the close flow. Capability `pos.shift.read`.
- `GET /shiftPeriod/{companyId}/shift/{shiftId}/events` — audit event log for the shift. Capability `pos.shift.read`.
- `POST /shiftPeriod/{companyId}/shift/{shiftId}/cash-movement` — record a cash in/out during the shift. Capability `pos.shift.edit`. Body `CashMovementRequest`.
- `POST /shiftPeriod/{companyId}/shift/{shiftId}/close` — close with server-side cash reconciliation. Capability `pos.shift.close`. Body `CloseShiftRequest`.
- `POST /shiftPeriod/{companyId}/shift/{shiftId}/force-close` — manager force-close with a `reason`. Capability `pos.shift.force_close`.

Deprecated legacy endpoints (kept for compatibility, avoid for new work): `POST /{companyId}/{branchId}/startShift`, `POST /{companyId}/{spId}/endShift`, `POST /{companyId}/currentShift`, `POST /{companyId}/ShiftOrdersById`, `GET /{companyId}/{branchId}/branchShifts`.

Cash movement types include `CASH_SALE` (auto-recorded on standard cash sales) plus manual cash-in/cash-out entries. Closing a shift writes reconciliation totals and shift events; the audit trail must be preserved.

## POS Offline Sync

POS clients can operate offline and later sync orders to the server. This path is idempotent and audited. Relevant backend package: `com.example.valueinsoftbackend.pos.offline`.

Concepts: a `pos_device` is a registered offline-capable terminal; a `pos_sync_batch` groups offline orders being uploaded; each offline order is staged in `pos_offline_order_import` and validated, with failures in `pos_offline_order_error`; `pos_idempotency_key` maps an offline order to its official server order so replays never double-post; `pos_sync_audit_log` records sync events; `pos_bootstrap_version` tracks the version/checksum of bootstrap data (settings, catalog) pushed to devices.

Rules: offline order posting must check `pos_idempotency_key` before inserting an official order; retries must not duplicate order, stock, or finance effects.

## POS Database Tables

Legacy shared POS tables (quoted mixed-case names in `public`):

- `public."PosOrder"` — order header: `orderId`, `orderTime`, `clientName`, `orderType`, `orderDiscount`, `orderTotal`, `salesUser`.
- `public."PosOrderDetail"` — order line: `orderDetailsId`, `itemId`, `itemName`, `quantity`, `price`, `total`, `orderId`.
- `public."PosProduct"` — legacy product: `productId`, `productName`, `rPrice`, `lPrice`, `bPrice`, `serial`, `quantity`, `pState`, `branchId`.
- `public."PosShiftPeriod"` — shift header: `PosSOID`, `ShiftStartTime`, `ShiftEndTime`, `branchId`.
- `public."PosCateJson"` — per-branch category JSON.
- `public."MainMajor"` — main business categories.
- `public."InventoryTransactions"` — inventory movement rows.

Tenant schemas are named `c_<companyId>` (e.g. `c_1095`) and may hold branch-suffixed legacy tables such as `c_1095."PosOrder_1074"`, `PosOrderDetail_1074`, `PosProduct_1074`, `InventoryTransactions_1074`. Branch id is encoded in the table name suffix. Do not create new branch-suffixed tables for modern features; prefer a `branch_id` column.

Modern shift tables (per tenant schema): `shift_event` (shift lifecycle events), `shift_cash_movement` (cash in/out and adjustments).

Modern inventory tables backing POS products: `inventory_product` (catalog), `inventory_product_unit` (serialized/IMEI units), `inventory_branch_stock_balance` (branch stock), `inventory_stock_ledger` / `inventory_stock_movement` (audit trail), `inventory_legacy_product_mapping` (bridge from legacy `PosProduct`).

Offline sync tables (in `public` / tenant): `pos_device`, `pos_device_session`, `pos_sync_batch`, `pos_offline_order_import`, `pos_offline_order_error`, `pos_idempotency_key`, `pos_sync_audit_log`, `pos_bootstrap_version`.

Database rules (see SQL RAG doc for full detail): all schema changes go through Flyway migrations in `src/main/resources/db/migration`; never edit an applied migration; new money columns use `NUMERIC`; tenant runtime changes must loop over every `c_<companyId>` schema; quote legacy mixed-case identifiers exactly.

## Common POS Questions (Q&A)

Q: How do I create a sale from the API?
A: `POST /Order/v2/pos/{companyId}/orders` with a `CreateOrderRequest` and the `pos.sale.create` capability. Include an `idempotencyKey` (UUID) so retries are safe. A `201` means a new order; `200` means the key already posted an order.

Q: Why didn't a sale record cash in the shift?
A: Cash is only auto-recorded for standard cash sales (`orderType` = `Dirict`/`Direct`/`مباشر`) with `orderTotal > 0` while a shift is active. Non-cash types (credit/client account) skip the `CASH_SALE` movement.

Q: How is a duplicate order prevented?
A: The `idempotencyKey` on the request. On replay the server returns the original `CreateOrderResult` with `idempotencyHit = true` and skips stock, cash, and finance side effects. Offline orders use `pos_idempotency_key` for the same guarantee.

Q: How do phones/IMEI items work at POS?
A: They are serialized products tracked in `inventory_product_unit`. Receive them via `POST /invTrans/AddSerializedStockIn`, scan by IMEI/serial via `GET /invTrans/SerializedScan/...`, and a sale consumes a specific unit rather than only decrementing a quantity.

Q: How do I close a cash shift?
A: `POST /shiftPeriod/{companyId}/shift/{shiftId}/close` with a `CloseShiftRequest` and the `pos.shift.close` capability. The server reconciles counted cash against expected cash and writes reconciliation totals plus shift events. A manager can `force-close` with `pos.shift.force_close`.

Q: Where do sale accounting entries come from?
A: A POS-sale finance posting is enqueued after the order transaction commits (`FinanceOperationalPostingService`), so accounting entries post only when the sale succeeds, and journal debits must equal credits.

## High-Value Search Keywords For RAG

POS, point of sale, order, sale, receipt, receiptNumber, cashier, salesUser, orderType, Dirict, Direct, مباشر, idempotencyKey, idempotency hit, CreateOrderRequest, CreateOrderResult, bounce back, refund, return, product, catalog, barcode, serialized, IMEI, serial number, product unit, tracking type, category, MainMajor, business package, offer, promotion, discount, loyalty, points, shift, open shift, close shift, reconciliation, cash movement, CASH_SALE, force close, opening float, damaged item, settle, inventory transaction, stock movement, stock balance, offline sync, pos_device, pos_idempotency_key, sync batch, bootstrap, capability, pos.sale.create, pos.shift.close, companyId, branchId, tenant, c_1095, finance posting, journal.
