package com.example.valueinsoftbackend.Service.product;

import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportColumn;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportMode;
import com.example.valueinsoftbackend.Model.Product;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class ProductImportTemplateService {

    private static final int SAMPLE_PRODUCT_LIMIT = 5;

    private static final List<ProductImportColumn> COLUMNS = List.of(
            new ProductImportColumn("product_name", true, "Required. Maps to inventory_product.product_name; current max length is 30."),
            new ProductImportColumn("barcode", true, "Required in CSV. Maps to inventory_product.serial until a dedicated barcode column exists."),
            new ProductImportColumn("sku", true, "Required in CSV for future compatibility. No dedicated final product column exists yet."),
            new ProductImportColumn("category", true, "Required. Maps to inventory_product.major."),
            new ProductImportColumn("subcategory", false, "Optional. Maps to inventory_product.product_type."),
            new ProductImportColumn("unit_code", true, "Required. Maps to base_uom_code; default is PCS."),
            new ProductImportColumn("branch_id", true, "Required. Target branch id."),
            new ProductImportColumn("purchase_price", true, "Required. Maps to buying_price."),
            new ProductImportColumn("wholesale_price", false, "Optional. Maps to lowest_price."),
            new ProductImportColumn("selling_price", true, "Required. Maps to retail_price."),
            new ProductImportColumn("opening_stock_quantity", true, "Required. Must be integer-compatible because stock quantity is currently INTEGER."),
            new ProductImportColumn("minimum_stock_quantity", false, "Optional minimum stock threshold."),
            new ProductImportColumn("supplier_name", false, "Optional supplier name."),
            new ProductImportColumn("supplier_code", false, "Optional supplier code."),
            new ProductImportColumn("brand", false, "Optional brand text."),
            new ProductImportColumn("product_state", false, "Optional. Default is New."),
            new ProductImportColumn("description", false, "Optional. Maps to description; current max length is 60."),
            new ProductImportColumn("product_name_ar", false, "Optional Arabic product name."),
            new ProductImportColumn("active", false, "Optional boolean flag."),
            new ProductImportColumn("serial_required", false, "Optional boolean flag."),
            new ProductImportColumn("warranty_period_days", false, "Optional non-negative integer."),
            new ProductImportColumn("tax_percentage", false, "Optional tax percentage."),
            new ProductImportColumn("expiry_date", false, "Optional YYYY-MM-DD date."),
            new ProductImportColumn("image_url", false, "Optional image URL."),
            new ProductImportColumn("business_line_key", false, "Optional. Default is MOBILE."),
            new ProductImportColumn("template_key", false, "Optional. Default is mobile_device."),
            new ProductImportColumn("pricing_policy_code", false, "Optional. Default is FIXED_RETAIL."),
            new ProductImportColumn("allow_below_cost", false, "Optional boolean flag."),
            new ProductImportColumn("imei", false, "Required per row for IMEI-tracked products (unit_code=IMEI). One row per device; rows sharing the same product_name + category are grouped into a single product and each row becomes one serialized unit."),
            new ProductImportColumn("serial_number", false, "Required per row for SERIAL-tracked products (unit_code=SERIAL or serial_required=true). One row per unit."),
            new ProductImportColumn("unit_cost", false, "Optional per-unit acquisition cost for serialized rows. Defaults to purchase_price.")
    );

    private final ProductService productService;

    public ProductImportTemplateService(ProductService productService) {
        this.productService = productService;
    }

    public String buildCsvTemplate(int companyId, int branchId, ProductImportMode mode) {
        ProductImportMode effectiveMode = mode == null ? ProductImportMode.ADD_ONLY : mode;
        String sampleBatchKey = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
        List<List<String>> sampleRows = productService
                .getProductsAllRange(String.valueOf(branchId), companyId, null)
                .stream()
                .filter(this::isImportCompatible)
                .limit(SAMPLE_PRODUCT_LIMIT)
                .map(product -> productRow(product, branchId, effectiveMode, sampleBatchKey))
                .toList();

        return "\uFEFFsep=,\r\n"
                + COLUMNS.stream()
                .map(ProductImportColumn::header)
                .map(this::escapeCsv)
                .collect(Collectors.joining(","))
                + "\r\n"
                + sampleRows.stream()
                .map(row -> row.stream()
                        .map(this::escapeCsv)
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\r\n"))
                + "\r\n";
    }

    public List<ProductImportColumn> columns() {
        return COLUMNS;
    }

    private boolean isImportCompatible(Product product) {
        String barcode = sourceBarcode(product);
        return product != null
                && hasText(product.getProductName())
                && product.getProductName().trim().length() <= 30
                && hasText(barcode)
                && barcode.length() <= 35
                && hasText(product.getMajor())
                && product.getMajor().trim().length() <= 30
                && (!hasText(product.getType()) || product.getType().trim().length() <= 15)
                && product.getBPrice() >= 0
                && product.getLPrice() >= product.getBPrice()
                && product.getRPrice() >= product.getLPrice();
    }

    private List<String> productRow(Product product, int branchId, ProductImportMode mode, String sampleBatchKey) {
        boolean addOnly = mode == ProductImportMode.ADD_ONLY;
        String sourceBarcode = sourceBarcode(product);
        String barcode = addOnly
                ? sampleIdentifier("S", branchId, product.getProductId(), sampleBatchKey)
                : sourceBarcode;
        String sourceSku = firstText(product.getSku(), "SKU-" + product.getProductId());
        String sku = addOnly
                ? sampleIdentifier("SKU", branchId, product.getProductId(), sampleBatchKey)
                : sourceSku;
        String imageUrl = httpUrlOrBlank(firstText(product.getOnlineImageUrl(), product.getImage()));
        TrackingType trackingType = TrackingType.defaultIfNull(product.getTrackingType());

        return sampleRow(
                product.getProductName().trim(),
                barcode,
                sku,
                product.getMajor().trim(),
                firstText(product.getType(), "General"),
                exportableUomCode(product.getBaseUomCode()),
                String.valueOf(branchId),
                String.valueOf(product.getBPrice()),
                String.valueOf(product.getLPrice()),
                String.valueOf(product.getRPrice()),
                addOnly ? "0" : String.valueOf(Math.max(0, product.getQuantity())),
                "",
                "",
                "",
                firstText(product.getBrand(), product.getCompanyName(), product.getMajor()),
                "Used".equalsIgnoreCase(product.getPState()) ? "Used" : "New",
                firstText(product.getDesc(), product.getOnlineDescription()),
                "",
                "true",
                String.valueOf(trackingType != TrackingType.QUANTITY),
                "",
                "",
                "",
                imageUrl,
                firstText(product.getBusinessLineKey(), "MOBILE"),
                firstText(product.getTemplateKey(), "mobile_device"),
                firstText(product.getPricingPolicyCode(), "FIXED_RETAIL"),
                "false",
                "",
                "",
                ""
        );
    }

    /**
     * Legacy products may carry a tracking type (IMEI / SERIAL) inside base_uom_code.
     * Never export that into the sample CSV: unit_code must be a real base UOM.
     */
    private String exportableUomCode(String baseUomCode) {
        String code = firstText(baseUomCode, "PCS").toUpperCase(Locale.ROOT);
        return "IMEI".equals(code) || "SERIAL".equals(code) ? "PCS" : code;
    }

    private String sourceBarcode(Product product) {
        if (product == null) {
            return "";
        }
        return firstText(product.getSerial(), product.getBarcode());
    }

    private String sampleIdentifier(String prefix, int branchId, int productId, String sampleBatchKey) {
        return (prefix + "-SAMPLE-" + branchId + "-" + productId + "-" + sampleBatchKey)
                .toUpperCase(Locale.ROOT);
    }

    private String httpUrlOrBlank(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.trim();
        return clean.startsWith("http://") || clean.startsWith("https://") ? clean : "";
    }

    private static List<String> sampleRow(String productName,
                                          String barcode,
                                          String sku,
                                          String category,
                                          String subcategory,
                                          String unitCode,
                                          String branchId,
                                          String purchasePrice,
                                          String wholesalePrice,
                                          String sellingPrice,
                                          String openingStock,
                                          String minimumStock,
                                          String supplierName,
                                          String supplierCode,
                                          String brand,
                                          String productState,
                                          String description,
                                          String productNameAr,
                                          String active,
                                          String serialRequired,
                                          String warrantyPeriodDays,
                                          String taxPercentage,
                                          String expiryDate,
                                          String imageUrl,
                                          String businessLineKey,
                                          String templateKey,
                                          String pricingPolicyCode,
                                          String allowBelowCost,
                                          String imei,
                                          String serialNumber,
                                          String unitCost) {
        return List.of(
                productName,
                barcode,
                sku,
                category,
                subcategory,
                unitCode,
                branchId,
                purchasePrice,
                wholesalePrice,
                sellingPrice,
                openingStock,
                minimumStock,
                supplierName,
                supplierCode,
                brand,
                productState,
                description,
                productNameAr,
                active,
                serialRequired,
                warrantyPeriodDays,
                taxPercentage,
                expiryDate,
                imageUrl,
                businessLineKey,
                templateKey,
                pricingPolicyCode,
                allowBelowCost,
                imei,
                serialNumber,
                unitCost
        );
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0;
        if (!needsQuotes) {
            return value;
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
