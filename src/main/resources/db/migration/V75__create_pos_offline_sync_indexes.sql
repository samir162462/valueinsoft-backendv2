-- ==========================================================
-- V75 — POS Offline Sync Indexes
-- ==========================================================
-- Targeted indexes for query patterns used by the offline
-- sync module. Avoids over-indexing — only covers the
-- primary lookup and filtering paths.
-- ==========================================================

-- pos_device indexes
CREATE INDEX IF NOT EXISTS idx_pos_device_company_branch_status
    ON pos_device (company_id, branch_id, status);

CREATE INDEX IF NOT EXISTS idx_pos_device_heartbeat
    ON pos_device (last_heartbeat_at);

-- pos_sync_batch indexes
CREATE INDEX IF NOT EXISTS idx_sync_batch_company_status_created
    ON pos_sync_batch (company_id, branch_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_sync_batch_device_created
    ON pos_sync_batch (device_id, created_at);

-- pos_offline_order_import indexes
CREATE INDEX IF NOT EXISTS idx_order_import_batch_status
    ON pos_offline_order_import (sync_batch_id, status);

CREATE INDEX IF NOT EXISTS idx_order_import_company_status_created
    ON pos_offline_order_import (company_id, branch_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_order_import_offline_order_no
    ON pos_offline_order_import (company_id, branch_id, offline_order_no);

-- pos_idempotency_key indexes
CREATE INDEX IF NOT EXISTS idx_idempotency_official_order
    ON pos_idempotency_key (official_order_id)
    WHERE official_order_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_idempotency_official_invoice
    ON pos_idempotency_key (official_invoice_no)
    WHERE official_invoice_no IS NOT NULL;

-- pos_offline_order_error indexes
CREATE INDEX IF NOT EXISTS idx_order_error_import
    ON pos_offline_order_error (offline_order_import_id);

CREATE INDEX IF NOT EXISTS idx_order_error_company_code_created
    ON pos_offline_order_error (company_id, branch_id, error_code, created_at);

-- pos_bootstrap_version indexes
-- (unique constraint already covers company_id, branch_id, data_type)

-- pos_device_session indexes
CREATE INDEX IF NOT EXISTS idx_device_session_lookup
    ON pos_device_session (company_id, branch_id, device_id, cashier_id, status);

CREATE INDEX IF NOT EXISTS idx_device_session_last_seen
    ON pos_device_session (last_seen_at);

-- pos_sync_audit_log indexes
CREATE INDEX IF NOT EXISTS idx_sync_audit_company_created
    ON pos_sync_audit_log (company_id, branch_id, created_at);

CREATE INDEX IF NOT EXISTS idx_sync_audit_batch
    ON pos_sync_audit_log (sync_batch_id)
    WHERE sync_batch_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sync_audit_order_import
    ON pos_sync_audit_log (offline_order_import_id)
    WHERE offline_order_import_id IS NOT NULL;
