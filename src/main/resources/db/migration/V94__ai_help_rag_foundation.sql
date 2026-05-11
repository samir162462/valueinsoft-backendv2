-- ==========================================================
-- V94 - AI HELP RAG Foundation
-- ==========================================================
-- Central/shared internal help documents and chunks.
-- pgvector is not assumed; HELP search uses keyword fallback first.
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.ai_document (
    id UUID PRIMARY KEY,
    company_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    module VARCHAR(100),
    language VARCHAR(20),
    content TEXT NOT NULL,
    metadata_json TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS public.ai_document_chunk (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    company_id BIGINT NULL,
    title VARCHAR(255),
    module VARCHAR(100),
    language VARCHAR(20),
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ai_document_chunk_document
        FOREIGN KEY (document_id)
        REFERENCES public.ai_document (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_document_active_module_language
    ON public.ai_document (active, module, language);

CREATE INDEX IF NOT EXISTS idx_ai_document_chunk_document_index
    ON public.ai_document_chunk (document_id, chunk_index);

CREATE INDEX IF NOT EXISTS idx_ai_document_chunk_company_module
    ON public.ai_document_chunk (company_id, module);

INSERT INTO public.ai_document
    (id, company_id, title, document_type, module, language, content, metadata_json, active, created_at, updated_at)
VALUES
    ('10000000-0000-0000-0000-000000000001', NULL, 'How to add product', 'HELP_ARTICLE', 'inventory', 'en',
     'Open Inventory, choose Add item, enter the product name, barcode or serial when available, prices, quantity, supplier, category, and any required attributes. Review the details, then save. After saving, confirm the product appears in the inventory list and can be found from POS search if it is sellable.',
     '{"seed":"phase5"}', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000002', NULL, 'How to import products', 'HELP_ARTICLE', 'inventory', 'en',
     'Open Inventory and choose Bulk product import. Download the template, fill product rows carefully, upload the file, review validation errors, fix invalid rows, then confirm the import. Use the import history to download error reports or review previous batches.',
     '{"seed":"phase5"}', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000003', NULL, 'How to use POS', 'HELP_ARTICLE', 'pos', 'en',
     'Open Point of sale, select a category or search for products, add items to the cart, adjust quantities or discounts if permitted, select the customer when needed, choose payment details, then complete the sale. Print or share the receipt after the order is saved.',
     '{"seed":"phase5"}', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000004', NULL, 'How to print receipt', 'HELP_ARTICLE', 'pos', 'en',
     'After completing a POS sale, open the receipt view from the order confirmation or order details. Check printer settings for the branch, choose the receipt format, then print. If printing fails, verify browser permissions, printer connection, and configured receipt printer.',
     '{"seed":"phase5"}', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000005', NULL, 'How to manage suppliers', 'HELP_ARTICLE', 'suppliers', 'en',
     'Open Suppliers from the Inventory area. Use the supplier list to search existing suppliers, view supplier details, balances, statements, returned products, receipts, and audit history. Add or edit supplier records only when you have the required permission.',
     '{"seed":"phase5"}', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000006', NULL, 'How to open and close shift', 'HELP_ARTICLE', 'shift', 'en',
     'Start a shift from the app shell or shift screen before selling when shift tracking is enabled. During the shift, record permitted cash movements. To close the shift, review expected cash, enter counted cash, add a variance reason when required, and submit the close action.',
     '{"seed":"phase5"}', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000007', NULL, 'How to use dashboard', 'HELP_ARTICLE', 'dashboard', 'en',
     'Open Dashboard to review high level business indicators such as sales, inventory health, top performers, finance snapshot, notifications, alerts, and module launchers. Use filters and date ranges where available to narrow the view.',
     '{"seed":"phase5"}', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    document_type = EXCLUDED.document_type,
    module = EXCLUDED.module,
    language = EXCLUDED.language,
    content = EXCLUDED.content,
    metadata_json = EXCLUDED.metadata_json,
    active = true,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO public.ai_document_chunk
    (id, document_id, company_id, title, module, language, chunk_index, content, metadata_json, created_at)
SELECT
    ('20000000-0000-0000-0000-' || lpad(substring(d.id::text from 25), 12, '0'))::uuid,
    d.id,
    d.company_id,
    d.title,
    d.module,
    d.language,
    0,
    d.content,
    d.metadata_json,
    CURRENT_TIMESTAMP
FROM public.ai_document d
WHERE d.id IN (
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000006',
    '10000000-0000-0000-0000-000000000007'
)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    module = EXCLUDED.module,
    language = EXCLUDED.language,
    content = EXCLUDED.content,
    metadata_json = EXCLUDED.metadata_json;
