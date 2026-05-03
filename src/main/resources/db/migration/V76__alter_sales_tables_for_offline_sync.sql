-- ==========================================================
-- V76 — Alter Existing Sales Tables for Offline Sync
-- ==========================================================
-- Safely add offline sync tracking columns to existing tables
-- ONLY if they already exist.
--
-- NOTE: The ValueINSoft POS system uses dynamic per-branch
-- table names (e.g. "PosOrder_1", "PosOrder_2", etc.) under
-- tenant schemas (c_{companyId}). These cannot be altered
-- generically in a single Flyway migration because the
-- table names are dynamic and the schemas vary per tenant.
--
-- TODO: Implement a programmatic migration strategy that:
--   1. Enumerates all active tenant schemas (c_*).
--   2. For each schema, discovers PosOrder_*, PosOrderDetail_*,
--      InventoryTransactions_*, shifts, etc.
--   3. Safely ADDs the offline tracking columns below.
--
-- The columns to add to each relevant table:
--
--   source_channel    VARCHAR(30)  DEFAULT 'ONLINE'
--   sync_batch_id     BIGINT
--   device_id         BIGINT
--   offline_order_no  VARCHAR(150)
--   idempotency_key   VARCHAR(200)
--   client_type       VARCHAR(50)
--   local_created_at  TIMESTAMPTZ
--   synced_at         TIMESTAMPTZ
--
-- Target table patterns per tenant schema:
--   "PosOrder_{branchId}"
--   "PosOrderDetail_{branchId}"
--   "InventoryTransactions_{branchId}"
--   "PosShiftPeriod"
--   "Client"
--   "Expenses"
--
-- Modern tables (already in tenant schema) that may need columns:
--   inventory_stock_ledger
--   payroll_payment
--
-- Finance tables in tenant schema:
--   finance_journal_entry
--   finance_journal_line
--
-- ==========================================================

-- Placeholder: Add columns to any shared/public tables that DO
-- exist with known names. Finance journal tables are tenant-scoped
-- and created during onboarding, so they will need the same
-- programmatic approach as orders above.

-- Since we cannot enumerate tenant schemas in plain SQL migration,
-- we will at minimum create a helper function that future code
-- or a repeatable migration can call per-schema.

CREATE OR REPLACE FUNCTION public.add_offline_sync_columns_to_table(
    p_schema_name TEXT,
    p_table_name  TEXT
) RETURNS VOID AS $$
BEGIN
    -- source_channel
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = p_schema_name
          AND table_name   = p_table_name
          AND column_name  = 'source_channel'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.%I ADD COLUMN source_channel VARCHAR(30) DEFAULT ''ONLINE''',
            p_schema_name, p_table_name
        );
    END IF;

    -- sync_batch_id
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = p_schema_name
          AND table_name   = p_table_name
          AND column_name  = 'sync_batch_id'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.%I ADD COLUMN sync_batch_id BIGINT',
            p_schema_name, p_table_name
        );
    END IF;

    -- device_id
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = p_schema_name
          AND table_name   = p_table_name
          AND column_name  = 'device_id'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.%I ADD COLUMN device_id BIGINT',
            p_schema_name, p_table_name
        );
    END IF;

    -- offline_order_no
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = p_schema_name
          AND table_name   = p_table_name
          AND column_name  = 'offline_order_no'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.%I ADD COLUMN offline_order_no VARCHAR(150)',
            p_schema_name, p_table_name
        );
    END IF;

    -- idempotency_key
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = p_schema_name
          AND table_name   = p_table_name
          AND column_name  = 'idempotency_key'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.%I ADD COLUMN idempotency_key VARCHAR(200)',
            p_schema_name, p_table_name
        );
    END IF;

    -- client_type
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = p_schema_name
          AND table_name   = p_table_name
          AND column_name  = 'client_type'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.%I ADD COLUMN client_type VARCHAR(50)',
            p_schema_name, p_table_name
        );
    END IF;

    -- local_created_at
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = p_schema_name
          AND table_name   = p_table_name
          AND column_name  = 'local_created_at'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.%I ADD COLUMN local_created_at TIMESTAMPTZ',
            p_schema_name, p_table_name
        );
    END IF;

    -- synced_at
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = p_schema_name
          AND table_name   = p_table_name
          AND column_name  = 'synced_at'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.%I ADD COLUMN synced_at TIMESTAMPTZ',
            p_schema_name, p_table_name
        );
    END IF;
END;
$$ LANGUAGE plpgsql;

-- TODO: After deploying, run the following for each active tenant schema:
--
--   SELECT public.add_offline_sync_columns_to_table('c_1', 'PosOrder_1');
--   SELECT public.add_offline_sync_columns_to_table('c_1', 'PosOrderDetail_1');
--   SELECT public.add_offline_sync_columns_to_table('c_1', 'InventoryTransactions_1');
--   SELECT public.add_offline_sync_columns_to_table('c_1', 'PosShiftPeriod');
--   ...etc for each branch and schema
--
-- Or call it programmatically from a Spring Boot startup listener
-- that enumerates all c_* schemas and their branch tables.
