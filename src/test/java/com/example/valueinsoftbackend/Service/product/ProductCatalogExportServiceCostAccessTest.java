package com.example.valueinsoftbackend.Service.product;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.ProductCatalogExportRequest;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProductCatalogExportServiceCostAccessTest {

    @Mock
    private ProductService productService;
    @Mock
    private AuthorizationService authorizationService;

    @Test
    void exportRejectsBeforeReadingProductsWhenCostCapabilityIsMissing() {
        ProductCatalogExportService service = new ProductCatalogExportService(
                productService,
                authorizationService,
                50000,
                5000
        );
        ProductCatalogExportRequest request = new ProductCatalogExportRequest();
        request.setCompanyId(7);
        request.setBranchId(11);

        doAnswer(invocation -> {
            if ("inventory.pricing.cost.read".equals(invocation.getArgument(3, String.class))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "CAPABILITY_DENIED", "Missing cost capability");
            }
            return null;
        }).when(authorizationService)
                .assertAuthenticatedCapability(eq("manager@example.com"), eq(7), eq(11), anyString());

        ApiException error = assertThrows(ApiException.class, () -> service.writeExcel(
                "manager@example.com",
                request,
                new ByteArrayOutputStream()
        ));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verifyNoInteractions(productService);
    }
}
