package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosCategory;
import com.example.valueinsoftbackend.util.CustomPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private DbPosCategory dbPosCategory;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        dbPosCategory = Mockito.mock(DbPosCategory.class);
        categoryService = new CategoryService(dbPosCategory, new ObjectMapper());
    }

    @Test
    void getCategoriesJsonSupportsLegacyObjectPayload() {
        when(dbPosCategory.getCategoryJson(eq(1074), eq(1095)))
                .thenReturn("{\"Mobiles\":[\"iPhone\",\"Samsung\"],\"Accessories\":[\"Case\"]}");

        ArrayList<CustomPair> result = categoryService.getCategoriesJson(1095, 1074);

        assertEquals(2, result.size());
        assertEquals("Mobiles", result.get(0).getKey());
        assertEquals("iPhone", result.get(0).getValue().get(0));
        assertEquals("Accessories", result.get(1).getKey());
    }

    @Test
    void getCategoriesJsonSupportsArrayOfCategoryObjects() {
        when(dbPosCategory.getCategoryJson(eq(1074), eq(1095)))
                .thenReturn("[{\"Mobiles\":[\"iPhone\",\"Samsung\"]},{\"Accessories\":[\"Case\",\"Charger\"]}]");

        ArrayList<CustomPair> result = categoryService.getCategoriesJson(1095, 1074);

        assertEquals(2, result.size());
        assertEquals("Mobiles", result.get(0).getKey());
        assertEquals(2, result.get(0).getValue().size());
        assertEquals("Accessories", result.get(1).getKey());
        assertEquals("Charger", result.get(1).getValue().get(1));
    }

    @Test
    void getCategoriesJsonSupportsKeyValuePairPayload() {
        when(dbPosCategory.getCategoryJson(eq(1074), eq(1095)))
                .thenReturn("[{\"key\":\"Mobiles\",\"value\":[\"iPhone\"]},{\"key\":\"Accessories\",\"value\":\"[Case, Charger]\"}]");

        ArrayList<CustomPair> result = categoryService.getCategoriesJson(1095, 1074);

        assertEquals(2, result.size());
        assertEquals("Mobiles", result.get(0).getKey());
        assertEquals("iPhone", result.get(0).getValue().get(0));
        assertEquals("Accessories", result.get(1).getKey());
        assertEquals("Charger", result.get(1).getValue().get(1));
    }
}
