package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProduct;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProductCommandRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ResponseModel.ProductOperationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private DbPosProduct productRepository;
    private DbPosProductCommandRepository productCommandRepository;
    private LegacyInventoryBackfillService legacyInventoryBackfillService;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(DbPosProduct.class);
        productCommandRepository = Mockito.mock(DbPosProductCommandRepository.class);
        legacyInventoryBackfillService = Mockito.mock(LegacyInventoryBackfillService.class);
        productService = new ProductService(productRepository, productCommandRepository, legacyInventoryBackfillService);
    }

    @Test
    void addProductSavesAndReturnsResolvedProduct() {
        Product input = buildProduct();
        Product saved = buildProduct();
        saved.setProductId(5001);
        saved.setBusinessLineKey("MOBILE");
        saved.setTemplateKey("mobile_device");
        saved.setBaseUomCode("PCS");
        saved.setPricingPolicyCode("FIXED_RETAIL");
        saved.setAttributes("{\"manufacturer\":\"Apple\",\"imei\":\"123456789012345\"}");

        when(productCommandRepository.addProduct(eq(input), eq("1074"), eq(1095))).thenReturn(5001L);
        when(productRepository.getProductById(eq(5001), eq(1074), eq(1095))).thenReturn(saved);

        ProductOperationResponse response = productService.addProduct(input, "1074", 1095);

        assertEquals("Add", response.getTransactionType());
        assertEquals(5001L, response.getId());
        assertEquals(saved.getQuantity(), response.getNumItems());
        assertEquals(saved.getBPrice() * saved.getQuantity(), response.getTransTotal());
        assertNotNull(response.getProduct());
        assertEquals(5001, response.getProduct().getProductId());
        assertEquals("mobile_device", response.getProduct().getTemplateKey());
        assertEquals("PCS", response.getProduct().getBaseUomCode());
    }

    @Test
    void addProductRejectsZeroQuantity() {
        Product input = buildProduct();
        input.setQuantity(0);

        ApiException exception = assertThrows(ApiException.class, () -> productService.addProduct(input, "1074", 1095));

        assertEquals("PRODUCT_QUANTITY_REQUIRED", exception.getCode());
    }

    @Test
    void getProductByIdDelegatesToRepository() {
        Product saved = buildProduct();
        saved.setProductId(77);

        when(productRepository.getProductById(eq(77), eq(1074), eq(1095))).thenReturn(saved);

        Product result = productService.getProductById(77, 1074, 1095);

        assertNotNull(result);
        assertEquals(77, result.getProductId());
        assertEquals("Iphone 17", result.getProductName());
    }

    @Test
    void getProductByIdResolvesLegacyMappedIdWhenDirectLookupMisses() {
        Product saved = buildProduct();
        saved.setProductId(9001);

        when(productRepository.getProductById(eq(17), eq(1074), eq(1095))).thenReturn(null);
        when(legacyInventoryBackfillService.resolveModernProductId(eq(1095), eq(1074), eq(17))).thenReturn(9001);
        when(productRepository.getProductById(eq(9001), eq(1074), eq(1095))).thenReturn(saved);

        Product result = productService.getProductById(17, 1074, 1095);

        assertNotNull(result);
        assertEquals(9001, result.getProductId());
    }

    private Product buildProduct() {
        Product product = new Product();
        product.setProductId(0);
        product.setProductName("Iphone 17");
        product.setBuyingDay(Timestamp.valueOf("2026-04-11 10:15:00"));
        product.setActivationPeriod("0");
        product.setRPrice(60000);
        product.setLPrice(55000);
        product.setBPrice(50000);
        product.setCompanyName("Apple");
        product.setType("Phone");
        product.setOwnerName("");
        product.setSerial("SN-IPH-17");
        product.setDesc("Test inventory product");
        product.setBatteryLife(100);
        product.setOwnerPhone("");
        product.setOwnerNI("");
        product.setQuantity(4);
        product.setPState("New");
        product.setSupplierId(12);
        product.setMajor("Smartphones");
        product.setImage("");
        product.setBusinessLineKey("MOBILE");
        product.setTemplateKey("mobile_device");
        product.setBaseUomCode("PCS");
        product.setPricingPolicyCode("FIXED_RETAIL");
        product.setAttributes("{\"manufacturer\":\"Apple\",\"model\":\"Iphone 17\",\"imei\":\"123456789012345\"}");
        return product;
    }
}
