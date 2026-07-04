package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.util.PageHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductQueryBuilderTest {

    @Test
    void textSearchUsesCanonicalClassificationKeysWhenProvided() {
        ProductFilter filter = new ProductFilter(false, false, false, false, 0, 100000, null, null);
        filter.setGroupKey("mobile_devices");
        filter.setCategoryKey("mobiles");
        filter.setSubcategoryKey("smartphones");

        ProductQuerySpec spec = ProductQueryBuilder.buildTextSearchQuery(
                new String[]{"iphone"},
                "1074",
                1095,
                filter,
                new PageHandler("productId", 1, 25),
                false);

        assertTrue(spec.dataSql().contains("ibp.group_key = :groupKey"));
        assertTrue(spec.dataSql().contains("ibp.category_key = :categoryKey"));
        assertTrue(spec.dataSql().contains("ibp.subcategory_key = :subcategoryKey"));
    }

    @Test
    void legacyMajorFilterFallsBackAcrossResolvedTaxonomyLabels() {
        ProductFilter filter = new ProductFilter(false, false, false, false, 0, 100000, "Mobiles", null);

        ProductQuerySpec spec = ProductQueryBuilder.buildAllRangeQuery("1074", 1095, filter);

        assertTrue(spec.dataSql().contains("COALESCE(ibp.group_name"));
        assertTrue(spec.dataSql().contains("COALESCE(ibp.category_name, prod.major"));
        assertTrue(spec.dataSql().contains("COALESCE(ibp.subcategory_name, prod.product_type"));
    }
}
