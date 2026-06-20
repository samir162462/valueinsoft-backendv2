package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptProductRequest;
import com.example.valueinsoftbackend.Service.CategoryService;
import com.example.valueinsoftbackend.util.CustomPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryProductReceiptServiceTest {

    @Test
    void validateCategoryAllowsLegacyGroupAsCategoryNameWhenTypeMatchesBranchCategory() {
        CategoryService categoryService = mock(CategoryService.class);
        when(categoryService.getCategoriesJson(1095, 1074)).thenReturn(categoryPairs("Phones"));

        InventoryProductReceiptService service = newService(categoryService);
        ProductReceiptProductRequest product = new ProductReceiptProductRequest();
        product.setCategoryName("Mobiles");
        product.setSubcategoryName("Phones");

        assertDoesNotThrow(() -> invokeValidateCategory(service, 1095, 1074, product));
    }

    @Test
    void validateCategoryRejectsUnknownCategoryAndUnknownLegacyType() {
        CategoryService categoryService = mock(CategoryService.class);
        when(categoryService.getCategoriesJson(1095, 1074)).thenReturn(categoryPairs("Phones"));

        InventoryProductReceiptService service = newService(categoryService);
        ProductReceiptProductRequest product = new ProductReceiptProductRequest();
        product.setCategoryName("Mobiles");
        product.setSubcategoryName("Tablets");

        ApiException exception = assertThrows(ApiException.class, () -> invokeValidateCategory(service, 1095, 1074, product));

        assertEquals("CATEGORY_INVALID", exception.getCode());
    }

    private InventoryProductReceiptService newService(CategoryService categoryService) {
        return new InventoryProductReceiptService(
                null,
                null,
                null,
                null,
                null,
                categoryService,
                new ObjectMapper());
    }

    private ArrayList<CustomPair> categoryPairs(String... categoryNames) {
        ArrayList<CustomPair> pairs = new ArrayList<>();
        for (String categoryName : categoryNames) {
            pairs.add(new CustomPair(categoryName, new ArrayList<>()));
        }
        return pairs;
    }

    private void invokeValidateCategory(InventoryProductReceiptService service,
                                        int companyId,
                                        int branchId,
                                        ProductReceiptProductRequest product) throws Exception {
        Method method = InventoryProductReceiptService.class.getDeclaredMethod(
                "validateCategory",
                int.class,
                int.class,
                ProductReceiptProductRequest.class);
        method.setAccessible(true);
        try {
            method.invoke(service, companyId, branchId, product);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception typed) {
                throw typed;
            }
            throw exception;
        }
    }
}
