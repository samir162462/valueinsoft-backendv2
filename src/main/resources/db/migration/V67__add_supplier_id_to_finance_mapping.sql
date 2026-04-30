-- Add supplier_id column to finance_account_mapping
ALTER TABLE public.finance_account_mapping ADD COLUMN supplier_id INTEGER;

-- Create an index for efficient resolution of supplier-specific mappings
CREATE INDEX IF NOT EXISTS idx_finance_account_mapping_supplier
    ON public.finance_account_mapping (company_id, supplier_id, mapping_key, status);

-- Add a comment to explain the column
COMMENT ON COLUMN public.finance_account_mapping.supplier_id IS 'Optional link to a specific supplier for granular account mapping (e.g., dedicated payable accounts per supplier).';
