package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Inventory.InventoryAdjustmentReason;
import com.example.valueinsoftbackend.Model.Request.Inventory.InventoryAdjustmentRequest;
import com.example.valueinsoftbackend.Model.Response.Inventory.InventoryAdjustmentResponse;
import com.example.valueinsoftbackend.Service.inventory.InventoryAdjustmentService;
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
class InventoryAdjustmentControllerTest {

    @Mock
    private InventoryAdjustmentService adjustmentService;

    @Mock
    private AuthorizationService authorizationService;

    private InventoryAdjustmentController controller;

    @BeforeEach
    void setUp() {
        controller = new InventoryAdjustmentController(adjustmentService, authorizationService);
    }

    @Test
    void authorizesTenantScopeAndReturnsCreatedForNewAdjustment() {
        InventoryAdjustmentRequest request = request();
        InventoryAdjustmentResponse response = response(false);
        Principal principal = () -> "stock.manager";
        when(adjustmentService.adjust("stock.manager", request)).thenReturn(response);

        ResponseEntity<InventoryAdjustmentResponse> result = controller.adjust(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
        verify(authorizationService).assertAuthenticatedCapability(
                "stock.manager", 11, 7, "inventory.adjustment.create");
        verify(adjustmentService).adjust("stock.manager", request);
    }

    @Test
    void returnsOkForCompletedIdempotentReplay() {
        InventoryAdjustmentRequest request = request();
        InventoryAdjustmentResponse response = response(true);
        Principal principal = () -> "stock.manager";
        when(adjustmentService.adjust("stock.manager", request)).thenReturn(response);

        ResponseEntity<InventoryAdjustmentResponse> result = controller.adjust(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }

    private InventoryAdjustmentRequest request() {
        return new InventoryAdjustmentRequest(
                11,
                7,
                42L,
                3,
                8L,
                InventoryAdjustmentReason.FOUND_STOCK,
                "Count correction",
                "inventory-adjustment:test-001"
        );
    }

    private InventoryAdjustmentResponse response(boolean replay) {
        return new InventoryAdjustmentResponse(
                "a7cd3958-38ca-44d8-8dd3-a6a632bc0cc9",
                42L,
                "Sample item",
                3,
                10,
                13,
                2,
                9L,
                101L,
                202L,
                new InventoryAdjustmentResponse.FinanceSummary("PENDING", "301"),
                replay
        );
    }
}
