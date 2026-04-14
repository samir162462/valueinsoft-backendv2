CREATE TABLE IF NOT EXISTS public.branch_setting_groups (
    group_key VARCHAR(100) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.branch_setting_definitions (
    branch_setting_definition_id BIGSERIAL PRIMARY KEY,
    group_key VARCHAR(100) NOT NULL,
    setting_key VARCHAR(150) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    value_type VARCHAR(32) NOT NULL DEFAULT 'string',
    field_type VARCHAR(32) NOT NULL DEFAULT 'text',
    default_value_json JSONB NOT NULL DEFAULT 'null'::jsonb,
    options_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    validation_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_branch_setting_definitions_setting_key UNIQUE (setting_key),
    CONSTRAINT fk_branch_setting_definitions_group FOREIGN KEY (group_key)
        REFERENCES public.branch_setting_groups (group_key)
);

CREATE TABLE IF NOT EXISTS public.branch_setting_values (
    branch_setting_value_id BIGSERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL,
    branch_id INTEGER NOT NULL,
    setting_key VARCHAR(150) NOT NULL,
    value_json JSONB NOT NULL DEFAULT 'null'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_branch_setting_values_branch_setting UNIQUE (branch_id, setting_key),
    CONSTRAINT fk_branch_setting_values_tenant FOREIGN KEY (tenant_id)
        REFERENCES public."Company" (id),
    CONSTRAINT fk_branch_setting_values_branch FOREIGN KEY (branch_id)
        REFERENCES public."Branch" ("branchId"),
    CONSTRAINT fk_branch_setting_values_definition FOREIGN KEY (setting_key)
        REFERENCES public.branch_setting_definitions (setting_key)
);

CREATE INDEX IF NOT EXISTS idx_branch_setting_definitions_group_sort
    ON public.branch_setting_definitions (group_key, sort_order, setting_key);

CREATE INDEX IF NOT EXISTS idx_branch_setting_values_branch_active
    ON public.branch_setting_values (branch_id, active, setting_key);

INSERT INTO public.branch_setting_groups (group_key, display_name, description, active, sort_order)
VALUES
    ('receiptPrinter', 'Receipt Printer', 'Branch receipt printer and receipt routing settings.', TRUE, 10),
    ('barcodePrinter', 'Barcode Printer', 'Branch barcode label printer profile and print routing settings.', TRUE, 20),
    ('pos', 'Point Of Sale', 'Branch-level POS runtime behavior and default experience.', TRUE, 30),
    ('ui', 'User Interface', 'Branch-level interface defaults and behavior.', TRUE, 40)
ON CONFLICT (group_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    active = EXCLUDED.active,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW();

INSERT INTO public.branch_setting_definitions (
    group_key,
    setting_key,
    display_name,
    description,
    value_type,
    field_type,
    default_value_json,
    options_json,
    validation_json,
    active,
    sort_order
)
VALUES
    ('receiptPrinter', 'receiptPrinter.enabled', 'Receipt printer enabled', 'Enables branch receipt printing settings.', 'boolean', 'switch', 'true'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 10),
    ('receiptPrinter', 'receiptPrinter.mode', 'Receipt print mode', 'Controls the default receipt print routing.', 'enum', 'select', '"browser"'::jsonb, '[{"value":"browser","label":"Browser"},{"value":"qz","label":"QZ Tray"}]'::jsonb, '{}'::jsonb, TRUE, 20),
    ('receiptPrinter', 'receiptPrinter.printerName', 'Receipt printer name', 'Installed receipt printer queue name.', 'string', 'text', '""'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 30),
    ('receiptPrinter', 'receiptPrinter.paperWidth', 'Receipt paper width (mm)', 'Controls the effective receipt print width.', 'number', 'number', '80'::jsonb, '[]'::jsonb, '{"minValue":58,"maxValue":120}'::jsonb, TRUE, 40),
    ('receiptPrinter', 'receiptPrinter.autoCut', 'Receipt auto cut', 'Signals whether the receipt printer should auto-cut.', 'boolean', 'switch', 'false'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 50),
    ('receiptPrinter', 'receiptPrinter.cashDrawerEnabled', 'Cash drawer enabled', 'Enables cash drawer pulse integration for supported printers.', 'boolean', 'switch', 'false'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 60),
    ('receiptPrinter', 'receiptPrinter.density', 'Receipt density', 'Receipt printer density or fallback DPI.', 'number', 'number', '203'::jsonb, '[]'::jsonb, '{"minValue":96,"maxValue":300}'::jsonb, TRUE, 70),
    ('receiptPrinter', 'receiptPrinter.margins', 'Receipt margins', 'Additional receipt print margin in millimeters.', 'number', 'number', '0'::jsonb, '[]'::jsonb, '{"minValue":0,"maxValue":10}'::jsonb, TRUE, 80),
    ('receiptPrinter', 'receiptPrinter.scaleContent', 'Scale receipt content', 'Scales receipt HTML to fit narrow printers.', 'boolean', 'switch', 'false'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 90),

    ('barcodePrinter', 'barcodePrinter.enabled', 'Barcode printer enabled', 'Enables branch barcode label printer support.', 'boolean', 'switch', 'true'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 10),
    ('barcodePrinter', 'barcodePrinter.printerName', 'Barcode printer name', 'Installed barcode printer queue name.', 'string', 'text', '""'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 20),
    ('barcodePrinter', 'barcodePrinter.dpi', 'Barcode printer DPI', 'Thermal label printer density.', 'number', 'number', '203'::jsonb, '[]'::jsonb, '{"minValue":96,"maxValue":600}'::jsonb, TRUE, 30),
    ('barcodePrinter', 'barcodePrinter.labelWidth', 'Label width (mm)', 'Physical barcode label width.', 'number', 'number', '38'::jsonb, '[]'::jsonb, '{"minValue":20,"maxValue":100}'::jsonb, TRUE, 40),
    ('barcodePrinter', 'barcodePrinter.labelHeight', 'Label height (mm)', 'Physical barcode label height.', 'number', 'number', '25'::jsonb, '[]'::jsonb, '{"minValue":20,"maxValue":100}'::jsonb, TRUE, 50),
    ('barcodePrinter', 'barcodePrinter.templateCode', 'Label template code', 'Selected label template preset.', 'enum', 'select', '"modern-retail"'::jsonb, '[{"value":"modern-retail","label":"Modern retail"},{"value":"compact","label":"Compact"}]'::jsonb, '{}'::jsonb, TRUE, 60),
    ('barcodePrinter', 'barcodePrinter.language', 'Printer language', 'Raw label printer language.', 'enum', 'select', '"TSPL"'::jsonb, '[{"value":"TSPL","label":"TSPL"},{"value":"ZPL","label":"ZPL"},{"value":"EPL","label":"EPL"}]'::jsonb, '{}'::jsonb, TRUE, 70),
    ('barcodePrinter', 'barcodePrinter.gap', 'Label gap (mm)', 'Gap between separated labels.', 'number', 'number', '2'::jsonb, '[]'::jsonb, '{"minValue":0,"maxValue":10}'::jsonb, TRUE, 80),
    ('barcodePrinter', 'barcodePrinter.leftOffset', 'Left offset (mm)', 'Horizontal barcode label print offset.', 'number', 'number', '0'::jsonb, '[]'::jsonb, '{"minValue":-10,"maxValue":10}'::jsonb, TRUE, 90),
    ('barcodePrinter', 'barcodePrinter.topOffset', 'Top offset (mm)', 'Vertical barcode label print offset.', 'number', 'number', '0'::jsonb, '[]'::jsonb, '{"minValue":-10,"maxValue":10}'::jsonb, TRUE, 100),
    ('barcodePrinter', 'barcodePrinter.density', 'Barcode density', 'Raw barcode label printer density setting.', 'number', 'number', '8'::jsonb, '[]'::jsonb, '{"minValue":1,"maxValue":15}'::jsonb, TRUE, 110),
    ('barcodePrinter', 'barcodePrinter.speed', 'Barcode speed', 'Raw barcode label printer speed setting.', 'number', 'number', '4'::jsonb, '[]'::jsonb, '{"minValue":1,"maxValue":8}'::jsonb, TRUE, 120),
    ('barcodePrinter', 'barcodePrinter.nameFont', 'Name font', 'Relative product name font size.', 'number', 'number', '1'::jsonb, '[]'::jsonb, '{"minValue":0.5,"maxValue":5}'::jsonb, TRUE, 130),
    ('barcodePrinter', 'barcodePrinter.barcodeHeight', 'Barcode height', 'Barcode height in printer dots.', 'number', 'number', '96'::jsonb, '[]'::jsonb, '{"minValue":24,"maxValue":300}'::jsonb, TRUE, 140),
    ('barcodePrinter', 'barcodePrinter.barcodeNarrow', 'Barcode narrow width', 'Barcode narrow bar width.', 'number', 'number', '2'::jsonb, '[]'::jsonb, '{"minValue":0.5,"maxValue":6}'::jsonb, TRUE, 150),
    ('barcodePrinter', 'barcodePrinter.barcodeWide', 'Barcode wide width', 'Barcode wide bar width.', 'number', 'number', '2'::jsonb, '[]'::jsonb, '{"minValue":0.5,"maxValue":10}'::jsonb, TRUE, 160),
    ('barcodePrinter', 'barcodePrinter.codeFont', 'Code font', 'Human-readable barcode text size.', 'number', 'number', '1'::jsonb, '[]'::jsonb, '{"minValue":0.5,"maxValue":5}'::jsonb, TRUE, 170),
    ('barcodePrinter', 'barcodePrinter.priceFont', 'Price font', 'Price text size for barcode labels.', 'number', 'number', '1'::jsonb, '[]'::jsonb, '{"minValue":0.5,"maxValue":5}'::jsonb, TRUE, 180),

    ('pos', 'pos.mainScreenMode', 'POS main screen mode', 'Default POS screen mode for the branch.', 'enum', 'select', '"classic"'::jsonb, '[{"value":"classic","label":"Classic POS"},{"value":"modern","label":"Modern POS"}]'::jsonb, '{}'::jsonb, TRUE, 10),
    ('pos', 'pos.enableTouchMode', 'Touch mode enabled', 'Signals that the branch prefers touch-oriented POS spacing.', 'boolean', 'switch', 'false'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 20),
    ('pos', 'pos.quickActionsEnabled', 'POS quick actions enabled', 'Enables quick-action affordances for the main POS screen.', 'boolean', 'switch', 'true'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 30),
    ('pos', 'pos.allowManualPriceEdit', 'Allow manual price edit', 'Allows branch cashiers to override line price from POS.', 'boolean', 'switch', 'true'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 40),
    ('pos', 'pos.showInlineStock', 'Show inline stock', 'Shows inline stock badges in POS search results.', 'boolean', 'switch', 'true'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 50),
    ('pos', 'pos.enableBarcodeAutofocus', 'Enable barcode autofocus', 'Keeps the barcode input focused in modern POS mode.', 'boolean', 'switch', 'true'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 60),

    ('ui', 'ui.compactMode', 'Compact mode', 'Applies a compact branch UI presentation mode.', 'boolean', 'switch', 'false'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 10),
    ('ui', 'ui.themeMode', 'Theme mode', 'Branch interface theme preference.', 'enum', 'select', '"light"'::jsonb, '[{"value":"light","label":"Light"},{"value":"dark","label":"Dark"},{"value":"system","label":"System"}]'::jsonb, '{}'::jsonb, TRUE, 20)
ON CONFLICT (setting_key) DO UPDATE
SET group_key = EXCLUDED.group_key,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    value_type = EXCLUDED.value_type,
    field_type = EXCLUDED.field_type,
    default_value_json = EXCLUDED.default_value_json,
    options_json = EXCLUDED.options_json,
    validation_json = EXCLUDED.validation_json,
    active = EXCLUDED.active,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW();
