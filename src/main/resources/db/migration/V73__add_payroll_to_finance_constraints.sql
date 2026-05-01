-- Add 'payroll' to allowed source modules and payroll-related types to journal types
ALTER TABLE public.finance_journal_entry DROP CONSTRAINT IF EXISTS chk_finance_journal_entry_type;
ALTER TABLE public.finance_journal_entry ADD CONSTRAINT chk_finance_journal_entry_type 
    CHECK (journal_type IN ('sales', 'sales_return', 'purchase', 'purchase_return', 'payment', 'inventory', 'adjustment', 'reversal', 'opening_balance', 'closing', 'expense', 'payroll_accrual', 'payroll_payment'));

ALTER TABLE public.finance_journal_entry DROP CONSTRAINT IF EXISTS chk_finance_journal_entry_source_module;
ALTER TABLE public.finance_journal_entry ADD CONSTRAINT chk_finance_journal_entry_source_module 
    CHECK (source_module IN ('pos', 'purchase', 'inventory', 'payment', 'manual', 'system', 'migration', 'expense', 'payroll'));
