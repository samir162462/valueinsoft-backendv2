package com.example.valueinsoftbackend.Service.product;

import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportColumn;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductImportTemplateService {

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
            new ProductImportColumn("allow_below_cost", false, "Optional boolean flag.")
    );

    private static final List<List<String>> SAMPLE_ROWS = List.of(
            sampleRow(
                    "iPhone 15 128GB Black",
                    "8806095123001",
                    "APL-IP15-128-BLK",
                    "iPhone",
                    "iPhone 15",
                    "PCS",
                    "1074",
                    "35000",
                    "37000",
                    "38999",
                    "4",
                    "1",
                    "Main Supplier",
                    "SUP-MOB-001",
                    "Apple",
                    "New",
                    "Sealed retail device",
                    "ايفون 15 128 اسود",
                    "true",
                    "true",
                    "365",
                    "14",
                    "",
                    "https://example.com/products/iphone-15-black.jpg",
                    "MOBILE",
                    "mobile_device",
                    "FIXED_RETAIL",
                    "false"
            ),
            sampleRow(
                    "Samsung A55 256GB Navy",
                    "8806095467002",
                    "SMS-A55-256-NVY",
                    "Mobiles",
                    "Samsung A Series",
                    "PCS",
                    "1074",
                    "18500",
                    "19500",
                    "21999",
                    "6",
                    "2",
                    "Mobile House",
                    "SUP-MOB-002",
                    "Samsung",
                    "New",
                    "Mid-range 5G smartphone",
                    "سامسونج A55 256 كحلي",
                    "true",
                    "true",
                    "365",
                    "14",
                    "",
                    "https://example.com/products/samsung-a55-navy.jpg",
                    "MOBILE",
                    "mobile_device",
                    "FIXED_RETAIL",
                    "false"
            ),
            sampleRow(
                    "USB-C Fast Charger 25W",
                    "6223001001250",
                    "ACC-CHG-25W-WHT",
                    "Accessories",
                    "Chargers",
                    "PCS",
                    "1074",
                    "180",
                    "220",
                    "350",
                    "25",
                    "5",
                    "Accessories Partner",
                    "SUP-ACC-001",
                    "Anker",
                    "New",
                    "25W PD wall charger",
                    "شاحن سريع USB-C 25W",
                    "true",
                    "false",
                    "180",
                    "14",
                    "",
                    "https://example.com/products/usb-c-charger-25w.jpg",
                    "MOBILE",
                    "mobile_accessory",
                    "FIXED_RETAIL",
                    "false"
            )
    );

    public String buildCsvTemplate() {
        return "sep=,\r\n"
                + COLUMNS.stream()
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
                                          String allowBelowCost) {
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
                allowBelowCost
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
