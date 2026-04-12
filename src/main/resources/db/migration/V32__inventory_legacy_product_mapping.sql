CREATE TABLE IF NOT EXISTS public.inventory_legacy_product_mapping (
    company_id INTEGER NOT NULL,
    branch_id INTEGER NOT NULL,
    legacy_product_id INTEGER NOT NULL,
    product_id BIGINT NOT NULL,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (company_id, branch_id, legacy_product_id),
    CONSTRAINT inventory_legacy_product_mapping_company_fk FOREIGN KEY (company_id) REFERENCES public."Company" (id) ON DELETE CASCADE,
    CONSTRAINT inventory_legacy_product_mapping_branch_fk FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId") ON DELETE CASCADE,
    CONSTRAINT inventory_legacy_product_mapping_product_fk FOREIGN KEY (product_id) REFERENCES public.inventory_product (product_id) ON DELETE CASCADE,
    CONSTRAINT inventory_legacy_product_mapping_product_unique UNIQUE (company_id, branch_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_inventory_legacy_product_mapping_product
    ON public.inventory_legacy_product_mapping (company_id, branch_id, product_id);
