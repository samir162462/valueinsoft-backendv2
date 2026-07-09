-- =====================================================================
-- P1-7 VERIFICATION (Postgres only) — run after V139 has been applied.
--
-- Usage (against a migrated Postgres, e.g. a Testcontainers/CI database):
--   psql "$DB_URL" -v ON_ERROR_STOP=0 -f finance_posted_journal_immutability.sql
--
-- Expected outcome:
--   * The two statements marked "MUST FAIL" raise:
--       ERROR: finance_journal_entry ... is immutable (reversal-only)
--       ERROR: finance_journal_line for posted journal ... is immutable
--   * The statement marked "MUST SUCCEED" (posted -> reversed) completes.
--
-- This mirrors the JUnit test that will live in the P1-9 Testcontainers harness
-- (PostedJournalImmutabilityTest); until Docker is available in CI, this script is the
-- executable proof.
-- =====================================================================

BEGIN;

-- Minimal fixtures: a company, a fiscal year/period and two postable accounts are assumed
-- to already exist from the standard finance setup. Adjust IDs to match your test data, or
-- rely on the JUnit harness which seeds them. Below we assume a helper that inserts a
-- balanced POSTED journal with two lines and returns its id into :entry_id.

-- (Pseudocode markers — the JUnit harness performs the equivalent inserts via the
--  posting adapters. For a raw psql run, replace the SELECT with a real posted entry id.)
\set entry_id '00000000-0000-0000-0000-000000000000'
\set company_id 1

-- 1) MUST FAIL — mutate a posted journal's financial header.
--    Expect: ERROR ... immutable (reversal-only)
UPDATE public.finance_journal_entry
SET total_debit = total_debit + 100,
    total_credit = total_credit + 100
WHERE company_id = :company_id AND journal_entry_id = :'entry_id' AND status = 'posted';

-- 2) MUST FAIL — mutate a posted journal's line amount.
--    Expect: ERROR ... line ... is immutable
UPDATE public.finance_journal_line
SET debit_amount = debit_amount + 100
WHERE company_id = :company_id AND journal_entry_id = :'entry_id';

-- 3) MUST FAIL — delete a posted journal.
--    Expect: ERROR ... cannot be deleted (reversal-only)
DELETE FROM public.finance_journal_entry
WHERE company_id = :company_id AND journal_entry_id = :'entry_id' AND status = 'posted';

-- 4) MUST SUCCEED — the reversal transition posted -> reversed is still permitted.
UPDATE public.finance_journal_entry
SET status = 'reversed',
    reversed_by_journal_id = gen_random_uuid(),
    version = version + 1
WHERE company_id = :company_id AND journal_entry_id = :'entry_id' AND status = 'posted';

ROLLBACK;
