-- =====================================================================
-- V147: Append-only audit trail for the gated opening-balance import
-- (Stage 6.3 of docs/ar-ap-credit-open-items/OPEN_ITEMS_IMPLEMENTATION_ROADMAP.md,
--  strategy: OPEN_ITEMS_BACKFILL_DECISION.md §2/§3).
--
-- Every import attempt — dry runs included — writes one row per side (AR/AP)
-- recording both sides of the gating rule at decision time:
--   subledger total after import  vs  control-account attributable balance,
-- the resulting variance, the approved variance (if any) and the approver.
-- This row IS the signed-off variance record demanded by the backfill
-- decision ("no historical balance may be inserted automatically unless ...
-- or the approved difference is explicitly documented").
--
-- Append-only by trigger (V139 style): no UPDATE, no DELETE, ever.
-- =====================================================================

CREATE OR REPLACE FUNCTION public.openitems_opening_import_run_guard()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'openitems_opening_import_run is an append-only audit table (% blocked)', TG_OP
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION public.ensure_openitems_opening_import_audit_for_tenant(
    target_schema TEXT,
    target_company_id INTEGER
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    IF target_schema IS NULL OR btrim(target_schema) = '' THEN
        RAISE EXCEPTION 'target_schema is required';
    END IF;
    IF target_company_id IS NULL OR target_company_id <= 0 THEN
        RAISE EXCEPTION 'target_company_id must be positive';
    END IF;

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.openitems_opening_import_run (
            run_id             BIGSERIAL PRIMARY KEY,
            company_id         INTEGER NOT NULL,
            side               VARCHAR(2) NOT NULL,
            dry_run            BOOLEAN NOT NULL,
            status             VARCHAR(20) NOT NULL,
            parties_requested  INTEGER NOT NULL,
            parties_imported   INTEGER NOT NULL,
            parties_skipped    INTEGER NOT NULL,
            imported_total     NUMERIC(19,4) NOT NULL,
            subledger_total    NUMERIC(19,4) NOT NULL,
            control_attributable NUMERIC(19,4) NOT NULL,
            variance           NUMERIC(19,4) NOT NULL,
            approved_variance  NUMERIC(19,4),
            approver           VARCHAR(120),
            notes              VARCHAR(500),
            created_by         VARCHAR(120),
            created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT oi_import_run_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT oi_import_run_side_ck CHECK (side IN (''AR'', ''AP'')),
            CONSTRAINT oi_import_run_status_ck
                CHECK (status IN (''COMMITTED'', ''ABORTED'', ''DRY_RUN''))
        )
    ', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.openitems_opening_import_run (company_id, side, created_at DESC)
    ', 'idx_' || target_schema || '_oi_import_run_side', target_schema);

    EXECUTE format('DROP TRIGGER IF EXISTS trg_oi_import_run_guard ON %I.openitems_opening_import_run', target_schema);
    EXECUTE format('
        CREATE TRIGGER trg_oi_import_run_guard
            BEFORE UPDATE OR DELETE ON %I.openitems_opening_import_run
            FOR EACH ROW EXECUTE FUNCTION public.openitems_opening_import_run_guard()
    ', target_schema);
END;
$$;

DO $$
DECLARE
    company_record RECORD;
    schema_name TEXT;
BEGIN
    FOR company_record IN
        SELECT id FROM public."Company" ORDER BY id
    LOOP
        schema_name := format('c_%s', company_record.id);
        IF to_regnamespace(schema_name) IS NOT NULL THEN
            PERFORM public.ensure_openitems_opening_import_audit_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;
