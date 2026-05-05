# Offline POS End-to-End (E2E) Validation Guide

This document provides a comprehensive checklist for verifying the Offline POS implementation across the backend (PostgreSQL, Spring Boot) and frontend (React, IndexedDB).

## Prerequisites
- **Tenant ID**: 1095 (or your local test company)
- **Branch ID**: 1
- **User Role**: Owner or BranchManager (required for device registration and sync retry)
- **Database**: PostgreSQL 15+ with tenant schemas (`c_1095`)
- **Backend Properties**:
  - `valueinsoft.pos.offline.run-tenant-migration-on-startup=true`
  - `valueinsoft.pos.offline.admin.posting-enabled=false` (Keep false for safety during verification)

---

## 1. PostgreSQL Schema Verification
Run these checks against your target tenant schema (replace `c_1095` with your schema).

### Table Existence
```sql
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'c_1095' 
AND table_name IN (
    'pos_sync_batch', 
    'pos_order_import', 
    'pos_order_import_item',
    'pos_import_error',
    'pos_idempotency_key',
    'pos_registered_device',
    'pos_sync_audit_log'
);
```

### Reference Column Persistence (Phase 10L/10R)
```sql
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_schema = 'c_1095' 
AND table_name = 'pos_order_import'
AND column_name IN ('local_order_id', 'device_code', 'client_created_at', 'cashier_id');
```

### Posting Metadata (Phase 8B)
```sql
SELECT column_name 
FROM information_schema.columns 
WHERE table_schema = 'c_1095' 
AND table_name = 'pos_order_import'
AND column_name IN ('posted_order_id', 'official_order_id', 'posting_started_at', 'posting_completed_at');
```

### Finance Integration (Phase 10B)
```sql
SELECT column_name 
FROM information_schema.columns 
WHERE table_schema = 'c_1095' 
AND table_name = 'pos_order_import'
AND column_name IN ('finance_enqueue_status', 'finance_posting_request_id');
```

---

## 2. Browser IndexedDB Verification
Open Browser DevTools -> Application -> IndexedDB -> `OfflinePosDatabase`.

### Store Checks
- `offline_products`: Should contain cached products for your branch.
- `offline_prices`: Should contain matching price records.
- `offline_bootstrap_state`: Should show status `COMPLETED` for `PRODUCTS`, `PRICES`, `PAYMENT_METHODS`, and `CASHIER_PERMISSIONS`.
- `offline_device_state`: Should contain a record with `deviceCode` and `deviceId` (after registration).
- `offline_settings`: Should contain `autoSyncOnReconnect` (default `false`).

---

## 3. Cashier E2E Flow (Manual Steps)

### Step 3.1: Initialization
1.  **Open POS**: Navigate to the Point of Sale screen.
2.  **Register Device**: In the Offline Readiness Panel, click **Register Device**. Verify status badge turns green.
3.  **Download Data**: Click **Update Offline Data**. Monitor progress and verify counts for Products/Prices.
4.  **Check Connection**: Click **Check connection**. Verify badge shows `REACHABLE`.

### Step 3.2: Offline Operation
1.  **Go Offline**: Use Browser DevTools -> Network -> **Offline**.
2.  **Search & Add**: Search for a cached product, add it to the cart.
3.  **Checkout**: Click **Checkout** -> **Pay**.
4.  **Save Offline**: Click **Save Offline Order**.
5.  **Verify Queue**: Open the Queue Modal. Confirm the order is `QUEUED` with a `LOC-xxx` ID.

### Step 3.3: Manual Synchronization
1.  **Go Online**: Disable browser offline mode.
2.  **Manual Sync**: In the Queue Modal, click **Sync Queued Orders**.
3.  **Confirm Upload**: Verify status moves to `BACKEND_RECEIVED`.
4.  **Refresh Status**: Click **Refresh Sync Status**. Verify status moves towards `SYNCED` (if backend workers are processing).

### Step 3.4: Auto-Sync & Archive
1.  **Enable Auto-Sync**: Toggle "Auto-sync on reconnect" to **ON**.
2.  **Queue Another**: Go offline, save another order.
3.  **Trigger Auto-Sync**: Go online, wait 5 seconds. Verify auto-sync initiates (spinner appears).
4.  **Archive**: For `SYNCED` orders, click **Archive Synced**. Verify they are moved to the "Archived" filter and hidden from "Active".

---

## 4. Admin E2E Flow (Manual Steps)

### Step 4.1: Batch Management
1.  **Open Admin**: Navigate to **Offline POS Sync Admin** (requires `pos.offline.admin.process`).
2.  **List Batches**: Verify the batch from the cashier flow appears in the list.
3.  **Batch Details**: Click on a batch. Verify counters (Processed, Synced, Failed) match reality.

### Step 4.2: Import Details
1.  **View Imports**: Click **View Imports** within a batch.
2.  **Details**: Open an import detail.
3.  **Safety Check**: Verify **No raw payload JSON** is displayed. Verify `postedOrderId` matches the cashier view.

### Step 4.3: Posting Preview
1.  **Post Preview**: Click **Post Preview**. Confirm it reports the number of eligible orders without creating real invoices.

---

## 5. Safety & Security Checklist
- [ ] **Auth Token**: Confirm `localStorage` contains the token, but IndexedDB `offline_device_state` does **NOT**.
- [ ] **Admin Isolation**: Confirm Cashier UI does NOT call any `/api/admin/*` endpoints.
- [ ] **Archived Data**: Confirm archived orders are NOT re-synced by automatic or manual triggers.
- [ ] **Reachability**: Confirm auto-sync is skipped if `Check connection` shows `UNREACHABLE`.
- [ ] **Cleanup**: Confirm no local orders were deleted from IndexedDB during sync or archiving.

---

## 6. Rollback & Reset
- **Browser**: Clear IndexedDB `OfflinePosDatabase` to reset the device/cache.
- **Backend**: Update `pos_order_import` status to `PENDING` to re-trigger processing (for dev only).
