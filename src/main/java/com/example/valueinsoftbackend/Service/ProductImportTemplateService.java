package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportColumn;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductImportTemplateService {

    private static final List<ProductImportColumn> COLUMNS = List.of(
            new ProductImportColumn("product_name", true, "Required. Maps to inventory_product.product_name; current max length is 30."),
            new ProductImportColumn("sku", true, "Required in CSV for future compatibility. No dedicated final product column exists yet."),
            new ProductImportColumn("barcode", true, "Required in CSV. Maps to inventory_product.serial until a dedicated barcode column exists."),
            new ProductImportColumn("category", true, "Required. Maps to inventory_product.major."),
            new ProductImportColumn("unit_code", true, "Required. Maps to base_uom_code; default is PCS."),
            new ProductImportColumn("purchase_price", true, "Required. Maps to buying_price."),
            new ProductImportColumn("selling_price", true, "Required. Maps to retail_price."),
            new ProductImportColumn("opening_stock_quantity", true, "Required. Must be integer-compatible because stock quantity is currently INTEGER."),
            new ProductImportColumn("branch_id", true, "Required. Target branch id."),
            new ProductImportColumn("product_name_ar", false, "Optional Arabic product name."),
            new ProductImportColumn("subcategory", false, "Optional. Maps to inventory_product.product_type."),
            new ProductImportColumn("brand", false, "Optional brand text."),
            new ProductImportColumn("supplier_code", false, "Optional supplier code."),
            new ProductImportColumn("supplier_name", false, "Optional supplier name."),
            new ProductImportColumn("wholesale_price", false, "Optional. Maps to lowest_price."),
            new ProductImportColumn("tax_percentage", false, "Optional tax percentage."),
            new ProductImportColumn("minimum_stock_quantity", false, "Optional minimum stock threshold."),
            new ProductImportColumn("description", false, "Optional. Maps to description; current max length is 60."),
            new ProductImportColumn("active", false, "Optional boolean flag."),
            new ProductImportColumn("image_url", false, "Optional image URL."),
            new ProductImportColumn("expiry_date", false, "Optional YYYY-MM-DD date."),
            new ProductImportColumn("serial_required", false, "Optional boolean flag."),
            new ProductImportColumn("warranty_period_days", false, "Optional non-negative integer."),
            new ProductImportColumn("product_state", false, "Optional. Default is New."),
            new ProductImportColumn("business_line_key", false, "Optional. Default is MOBILE."),
            new ProductImportColumn("template_key", false, "Optional. Default is mobile_device."),
            new ProductImportColumn("pricing_policy_code", false, "Optional. Default is FIXED_RETAIL."),
            new ProductImportColumn("allow_below_cost", false, "Optional boolean flag.")
    );

    private static final List<List<String>> SAMPLE_ROWS = List.of(
            sampleRow("iPhone 15 128GB", "IPH15-128", "622000001", "35000", "38000", "5",
                    "iPhone 15", "37000", "2", "Apple iPhone 15 128GB", "https://example.com/iphone15.jpg"),
            sampleRow("iPhone 16 Pro 256GB", "IPH16P-256", "622000002", "52000", "57000", "3",
                    "iPhone 16 Pro", "55000", "1", "Apple iPhone 16 Pro 256GB", "https://example.com/iphone16pro.jpg"),
            sampleRow("iPhone SE 3rd 64GB", "IPHSE3-64", "622000003", "18000", "21000", "4",
                    "iPhone SE 3rd Generation", "20000", "1", "Apple iPhone SE 3rd 64GB", "https://example.com/iphonese3.jpg")
    );

    public String buildCsvTemplate() {
        return COLUMNS.stream()
                .map(ProductImportColumn::header)
                .map(this::escapeCsv)
                .collect(Collectors.joining(","))
                + "\r\n"
                + SAMPLE_ROWS.stream()
                .map(row -> row.stream()
                        .map(this::escapeCsv)
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\r\n"))
                + "\r\n";
    }

    public List<ProductImportColumn> columns() {
        return COLUMNS;
    }

    private static List<String> sampleRow(String productName,
                                          String sku,
                                          String barcode,
                                          String purchasePrice,
                                          String sellingPrice,
                                          String openingStock,
                                          String subcategory,
                                          String wholesalePrice,
                                          String minimumStock,
                                          String description,
                                          String imageUrl) {
        return List.of(
                productName,
                sku,
                barcode,
                "iPhone",
                "PCS",
                purchasePrice,
                sellingPrice,
                openingStock,
                "1074",
                "",
                subcategory,
                "Apple",
                "SUP-001",
                "Main Supplier",
                wholesalePrice,
                "14",
                minimumStock,
                description,
                "true",
                imageUrl,
                "",
                "false",
                "365",
                "New",
                "MOBILE",
                "mobile_device",
                "FIXED_RETAIL",
                "false"
        );
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
