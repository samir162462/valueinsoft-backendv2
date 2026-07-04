package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptClassificationRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptProductRequest;
import com.example.valueinsoftbackend.Service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BranchTaxonomyResolverTest {

    private static final String CANONICAL_PAYLOAD = """
            {
              "schemaVersion": 2,
              "groups": [
                {
                  "key": "mobile_devices",
                  "label": "Mobile Devices",
                  "categories": [
                    {
                      "key": "mobiles",
                      "label": "Mobiles",
                      "subcategories": [
                        { "key": "smartphones", "label": "Smartphones" }
                      ]
                    }
                  ]
                },
                {
                  "key": "mobile_accessories",
                  "label": "Mobile Accessories",
                  "categories": [
                    {
                      "key": "accessories",
                      "label": "Accessories",
                      "subcategories": [
                        { "key": "chargers", "label": "Chargers" }
                      ]
                    }
                  ]
                }
              ],
              "categoryData": [
                { "key": "Mobiles", "value": ["Smartphones"] },
                { "key": "Accessories", "value": ["Chargers"] }
              ],
              "branchGroups": [
                { "key": "Mobile Devices", "categories": ["Mobiles"] },
                { "key": "Mobile Accessories", "categories": ["Accessories"] }
              ]
            }
            """;

    @Test
    void resolvesCanonicalClassificationByKeys() {
        BranchTaxonomyResolver resolver = resolverWithPayload(CANONICAL_PAYLOAD);

        ProductReceiptProductRequest product = new ProductReceiptProductRequest();
        ProductReceiptClassificationRequest classification = new ProductReceiptClassificationRequest();
        classification.setGroupKey("mobile_devices");
        classification.setCategoryKey("mobiles");
        classification.setSubcategoryKey("smartphones");
        product.setClassification(classification);

        BranchTaxonomyResolver.ResolvedClassification resolved = resolver.resolveForProduct(1095, 1074, product);

        assertEquals("Mobile Devices", resolved.groupName());
        assertEquals("Mobiles", resolved.categoryName());
        assertEquals("Smartphones", resolved.subcategoryName());
        assertEquals(2, resolved.taxonomyVersion());
    }

    @Test
    void rejectsInvalidParentChildCombination() {
        BranchTaxonomyResolver resolver = resolverWithPayload(CANONICAL_PAYLOAD);

        ProductReceiptProductRequest product = new ProductReceiptProductRequest();
        ProductReceiptClassificationRequest classification = new ProductReceiptClassificationRequest();
        classification.setGroupKey("mobile_accessories");
        classification.setCategoryKey("mobiles");
        classification.setSubcategoryKey("smartphones");
        product.setClassification(classification);

        ApiException exception = assertThrows(ApiException.class, () -> resolver.resolveForProduct(1095, 1074, product));

        assertEquals("CLASSIFICATION_CATEGORY_INVALID", exception.getCode());
    }

    @Test
    void convertsLegacyTwoLevelPayloadToClassification() {
        BranchTaxonomyResolver resolver = resolverWithPayload("""
                {
                  "categoryData": [
                    { "key": "Mobiles", "value": ["Smartphones"] }
                  ],
                  "branchGroups": [
                    { "key": "Mobile Devices", "categories": ["Mobiles"] }
                  ]
                }
                """);

        ProductReceiptProductRequest product = new ProductReceiptProductRequest();
        product.setCategoryName("Mobiles");
        product.setSubcategoryName("Smartphones");

        BranchTaxonomyResolver.ResolvedClassification resolved = resolver.resolveForProduct(1095, 1074, product);

        assertEquals("mobile_devices", resolved.groupKey());
        assertEquals("mobiles", resolved.categoryKey());
        assertEquals("smartphones", resolved.subcategoryKey());
        assertEquals(1, resolved.taxonomyVersion());
    }

    private BranchTaxonomyResolver resolverWithPayload(String payload) {
        CategoryService categoryService = mock(CategoryService.class);
        when(categoryService.getCategoriesJsonFlat(1095, 1074)).thenReturn(payload);
        return new BranchTaxonomyResolver(categoryService, new ObjectMapper());
    }
}
