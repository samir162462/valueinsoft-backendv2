CREATE TABLE IF NOT EXISTS public.inventory_product (
    product_id BIGSERIAL PRIMARY KEY,
    company_id INTEGER NOT NULL,
    product_name VARCHAR(30) NOT NULL,
    buying_day TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activation_period INTEGER NOT NULL DEFAULT 0,
    retail_price INTEGER NOT NULL,
    lowest_price INTEGER NOT NULL,
    buying_price INTEGER NOT NULL,
    company_name VARCHAR(30) NOT NULL,
    product_type VARCHAR(15) NOT NULL,
    owner_name VARCHAR(20),
    serial VARCHAR(35),
    description VARCHAR(60),
    battery_life INTEGER NOT NULL DEFAULT 0,
    owner_phone VARCHAR(14),
    owner_ni VARCHAR(18),
    product_state VARCHAR(10) NOT NULL,
    supplier_id INTEGER NOT NULL DEFAULT 0,
    major VARCHAR(30) NOT NULL,
    img_file TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_product_company_fk FOREIGN KEY (company_id) REFERENCES public."Company" (id) ON DELETE CASCADE,
    CONSTRAINT inventory_product_price_order_ck CHECK (retail_price >= lowest_price AND lowest_price >= buying_price),
    CONSTRAINT inventory_product_state_ck CHECK (product_state IN ('New', 'Used')),
    CONSTRAINT inventory_product_activation_ck CHECK (activation_period >= 0),
    CONSTRAINT inventory_product_battery_ck CHECK (battery_life >= 0)
);

CREATE INDEX IF NOT EXISTS idx_inventory_product_company_major
    ON public.inventory_product (company_id, major, product_id DESC);

CREATE INDEX IF NOT EXISTS idx_inventory_product_company_name
    ON public.inventory_product (company_id, product_name);

CREATE INDEX IF NOT EXISTS idx_inventory_product_company_serial
    ON public.inventory_product (company_id, serial);

CREATE TABLE IF NOT EXISTS public.inventory_branch_stock_balance (
    company_id INTEGER NOT NULL,
    branch_id INTEGER NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    reserved_qty INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (company_id, branch_id, product_id),
    CONSTRAINT inventory_branch_stock_balance_company_fk FOREIGN KEY (company_id) REFERENCES public."Company" (id) ON DELETE CASCADE,
    CONSTRAINT inventory_branch_stock_balance_branch_fk FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId") ON DELETE CASCADE,
    CONSTRAINT inventory_branch_stock_balance_product_fk FOREIGN KEY (product_id) REFERENCES public.inventory_product (product_id) ON DELETE CASCADE,
    CONSTRAINT inventory_branch_stock_balance_quantity_ck CHECK (quantity >= 0),
    CONSTRAINT inventory_branch_stock_balance_reserved_ck CHECK (reserved_qty >= 0)
);

CREATE INDEX IF NOT EXISTS idx_inventory_branch_stock_balance_branch
    ON public.inventory_branch_stock_balance (company_id, branch_id, quantity DESC);

CREATE TABLE IF NOT EXISTS public.inventory_stock_ledger (
    stock_ledger_id BIGSERIAL PRIMARY KEY,
    company_id INTEGER NOT NULL,
    branch_id INTEGER NOT NULL,
    product_id BIGINT NOT NULL,
    quantity_delta INTEGER NOT NULL,
    movement_type VARCHAR(40) NOT NULL,
    reference_type VARCHAR(40),
    reference_id VARCHAR(64),
    actor_name VARCHAR(100),
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_stock_ledger_company_fk FOREIGN KEY (company_id) REFERENCES public."Company" (id) ON DELETE CASCADE,
    CONSTRAINT inventory_stock_ledger_branch_fk FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId") ON DELETE CASCADE,
    CONSTRAINT inventory_stock_ledger_product_fk FOREIGN KEY (product_id) REFERENCES public.inventory_product (product_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_inventory_stock_ledger_branch_product_time
    ON public.inventory_stock_ledger (company_id, branch_id, product_id, created_at DESC);
