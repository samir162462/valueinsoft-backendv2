package com.example.valueinsoftbackend.Service.product;

import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.InventoryImport.ParsedProductImportRow;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportMode;
import com.example.valueinsoftbackend.Model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductImportTemplateServiceTest {

    @Test
    void addOnlySampleUsesFiveSystemProductsAndRoundTripsThroughCsvParser() {
        ProductService productService = mock(ProductService.class);
        when(productService.getProductsAllRange("1074", 1095, null)).thenReturn(List.of(
                product(1), product(2), product(3), product(4), product(5), product(6)
        ));
        ProductImportTemplateService templateService = new ProductImportTemplateService(productService);
        ProductImportCsvParserService parserService = new ProductImportCsvParserService(
                templateService,
                100,
                1024 * 1024
        );

        String csv = templateService.buildCsvTemplate(1095, 1074, ProductImportMode.ADD_ONLY);
        List<ParsedProductImportRow> rows = parserService.parse(new MockMultipartFile(
                "file",
                "products_import_sample_5.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        ));

        assertTrue(csv.startsWith("\uFEFFsep=,"), "Excel-compatible UTF-8 BOM and separator hint are required");
        assertEquals(5, rows.size());
        for (int index = 0; index < rows.size(); index++) {
            ParsedProductImportRow row = rows.get(index);
            assertEquals("System Product " + (index + 1), row.value("product_name"));
            assertEquals("1074", row.value("branch_id"));
            assertEquals("0", row.value("opening_stock_quantity"));
            assertTrue(row.value("barcode").startsWith("S-SAMPLE-1074-" + (index + 1) + "-"));
            assertTrue(row.value("sku").startsWith("SKU-SAMPLE-1074-" + (index + 1) + "-"));
        }
    }

    @Test
    void updateSampleKeepsExistingBarcodeAndQuantity() {
        ProductService productService = mock(ProductService.class);
        when(productService.getProductsAllRange("1074", 1095, null)).thenReturn(List.of(product(42)));
        ProductImportTemplateService templateService = new ProductImportTemplateService(productService);
        ProductImportCsvParserService parserService = new ProductImportCsvParserService(
                templateService,
                100,
                1024 * 1024
        );

        String csv = templateService.buildCsvTemplate(1095, 1074, ProductImportMode.UPDATE_ONLY);
        List<ParsedProductImportRow> rows = parserService.parse(new MockMultipartFile(
                "file",
                "products_import_sample_5_update_only.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        ));

        assertEquals(1, rows.size());
        assertEquals("BARCODE-42", rows.get(0).value("barcode"));
        assertEquals("SKU-42", rows.get(0).value("sku"));
        assertEquals("42", rows.get(0).value("opening_stock_quantity"));
    }

    private Product product(int id) {
        Product product = new Product();
        product.setProductId(id);
        product.setProductName("System Product " + id);
        product.setSerial("BARCODE-" + id);
        product.setBarcode("BARCODE-" + id);
        product.setSku("SKU-" + id);
        product.setMajor("Mobiles");
        product.setType("Phones");
        product.setBaseUomCode("PCS");
        product.setBPrice(1000 + id);
        product.setLPrice(1100 + id);
        product.setRPrice(1200 + id);
        product.setQuantity(id);
        product.setCompanyName("System Brand");
        product.setBrand("System Brand");
        product.setPState("New");
        product.setDesc("System product sample " + id);
        product.setBusinessLineKey("MOBILE");
        product.setTemplateKey("mobile_device");
        product.setPricingPolicyCode("FIXED_RETAIL");
        product.setTrackingType(TrackingType.QUANTITY);
        return product;
    }
}
