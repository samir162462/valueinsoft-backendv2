-- =====================================================================
-- P1-7: Database-enforced immutability for posted/reversed finance journals.
--
-- The application already restricts edits to draft journals and uses reversal-only for
-- posted journals (FinanceJournalService). These triggers enforce that invariant at the
-- DATABASE level so that no rogue code path, future bug, migration, or direct SQL can
-- silently alter a posted financial record.
--
-- Allowed operations that these triggers deliberately DO NOT block:
--   * INSERT of a posted source journal + its lines (POS/purchase/payment adapters insert
--     lines while the parent entry is already 'posted' -> INSERT is never guarded).
--   * Transitions INTO 'posted' (UPDATE where OLD.status = 'validated').
--   * The single posted -> reversed transition performed by the reversal workflow
--     (markOriginalJournalReversed).
--   * Any edit while the entry is still 'draft'/'validated'/'voided'.
--
-- Everything else against a 'posted' or 'reversed' entry (and its lines) is rejected.
-- =====================================================================

-- ---- Journal entry guard -------------------------------------------------
CREATE OR REPLACE FUNCTION public.finance_journal_entry_immutability_guard()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'DELETE') THEN
        IF OLD.status IN ('posted', 'reversed') THEN
            RAISE EXCEPTION 'finance_journal_entry % is % and cannot be deleted (reversal-only)',
                OLD.journal_entry_id, OLD.status
                USING ERRCODE = 'check_violation';
        END IF;
        RETURN OLD;
    END IF;

    -- UPDATE
    IF OLD.status IN ('posted', 'reversed') THEN
        -- Permit ONLY the posted -> reversed transition (the reversal workflow).
        IF OLD.status = 'posted' AND NEW.status = 'reversed' THEN
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'finance_journal_entry % is % and is immutable (reversal-only)',
            OLD.journal_entry_id, OLD.status
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_finance_journal_entry_immutable ON public.finance_journal_entry;
CREATE TRIGGER trg_finance_journal_entry_immutable
    BEFORE UPDATE OR DELETE ON public.finance_journal_entry
    FOR EACH ROW EXECUTE FUNCTION public.finance_journal_entry_immutability_guard();

-- ---- Journal line guard --------------------------------------------------
CREATE OR REPLACE FUNCTION public.finance_journal_line_immutability_guard()
RETURNS TRIGGER AS $$
DECLARE
    parent_company INTEGER;
    parent_entry   UUID;
    parent_status  VARCHAR(32);
BEGIN
    IF (TG_OP = 'DELETE') THEN
        parent_company := OLD.company_id;
        parent_entry   := OLD.journal_entry_id;
    ELSE
        parent_company := NEW.company_id;
        parent_entry   := NEW.journal_entry_id;
    END IF;

    SELECT status INTO parent_status
    FROM public.finance_journal_entry
    WHERE company_id = parent_company
      AND journal_entry_id = parent_entry;

    IF parent_status IN ('posted', 'reversed') THEN
        RAISE EXCEPTION 'finance_journal_line for % journal % is immutable',
            parent_status, parent_entry
            USING ERRCODE = 'check_violation';
    END IF;

    IF (TG_OP = 'DELETE') THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Guards UPDATE/DELETE only. INSERT is intentionally allowed so posted source journals
-- (which insert their lines against an already-'posted' entry) continue to work.
DROP TRIGGER IF EXISTS trg_finance_journal_line_immutable ON public.finance_journal_line;
CREATE TRIGGER trg_finance_journal_line_immutable
    BEFORE UPDATE OR DELETE ON public.finance_journal_line
    FOR EACH ROW EXECUTE FUNCTION public.finance_journal_line_immutability_guard();
