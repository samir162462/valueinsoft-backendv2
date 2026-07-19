package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Request.Inventory.InventorySupplierReturnRequest;
import com.example.valueinsoftbackend.Model.Response.Inventory.InventorySupplierReturnResponse;
import com.example.valueinsoftbackend.Service.inventory.InventorySupplierReturnService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventorySupplierReturnControllerTest {

    @Mock
    private InventorySupplierReturnService supplierReturnService;

    @Mock
    private AuthorizationService authorizationService;

    private InventorySupplierReturnController controller;

    @BeforeEach
    void setUp() {
        controller = new InventorySupplierReturnController(supplierReturnService, authorizationService);
    }

    @Test
    void authorizesReturnCapabilityAndReturnsCreated() {
        InventorySupplierReturnRequest request = request();
        InventorySupplierReturnResponse response = response(false);
        Principal principal = () -> "manager";
        when(supplierReturnService.create("manager", request)).thenReturn(response);

        ResponseEntity<InventorySupplierReturnResponse> result = controller.create(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(authorizationService).assertAuthenticatedCapability(
                "manager", 10, 20, "suppliers.return.create");
        verify(supplierReturnService).create("manager", request);
    }

    @Test
    void returnsOkForCompletedReplay() {
        InventorySupplierReturnRequest request = request();
        InventorySupplierReturnResponse response = response(true);
        Principal principal = () -> "manager";
        when(supplierReturnService.create("manager", request)).thenReturn(response);

        ResponseEntity<InventorySupplierReturnResponse> result = controller.create(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }

    private InventorySupplierReturnRequest request() {
        return new InventorySupplierReturnRequest(
                10, 20, 30, 2, 4L, 0, "Return", "supplier-return-key-1");
    }

    private InventorySupplierReturnResponse response(boolean replay) {
        return new InventorySupplierReturnResponse(
                "return-operation-1", 201, 30, "Phone", 9, 2, 10, 8, 1, 5,
                301, 302, new InventorySupplierReturnResponse.FinanceSummary("PENDING", null), replay);
    }
}
