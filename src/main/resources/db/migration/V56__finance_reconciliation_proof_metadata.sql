-- Add proof file metadata columns to finance_reconciliation_item
ALTER TABLE public.finance_reconciliation_item
    ADD COLUMN IF NOT EXISTS resolution_proof_file_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS resolution_proof_file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS resolution_proof_file_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS resolution_proof_file_size BIGINT,
    ADD COLUMN IF NOT EXISTS resolution_proof_uploaded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolution_proof_uploaded_by INTEGER;

-- Add foreign key constraint for the user who uploaded the proof
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_finance_reconciliation_item_proof_user'
    ) THEN
        ALTER TABLE public.finance_reconciliation_item
            ADD CONSTRAINT fk_finance_reconciliation_item_proof_user
            FOREIGN KEY (resolution_proof_uploaded_by)
            REFERENCES public.users (id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- Index for analytics and auditing of uploads
CREATE INDEX IF NOT EXISTS idx_finance_reconciliation_item_proof_uploaded_at
    ON public.finance_reconciliation_item (company_id, resolution_proof_uploaded_at);
