package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.Model.Request.BounceBackOrderRequest;
import com.example.valueinsoftbackend.Model.Request.CreateOrderRequest;
import com.example.valueinsoftbackend.Model.Request.OrderPeriodRequest;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.Service.security.TenantScopeGuard;
import com.example.valueinsoftbackend.Service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.security.Principal;
import java.util.ArrayList;

@RestController
@Validated
@RequestMapping("/Order")
public class OrderController {

    private final DbPosOrder dbPosOrder;
    private final OrderService orderService;
    private final AuthorizationService authorizationService;
    private final TenantScopeGuard tenantScopeGuard;

    @Autowired
    public OrderController(DbPosOrder dbPosOrder,
                           OrderService orderService,
                           AuthorizationService authorizationService,
                           TenantScopeGuard tenantScopeGuard) {
        this.dbPosOrder = dbPosOrder;
        this.orderService = orderService;
        this.authorizationService = authorizationService;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    @PostMapping("/{companyId}/saveOrder")
    ResponseEntity<Integer> newOrder(@Valid @RequestBody CreateOrderRequest newOrderShiftIn,
                                     @PathVariable @Positive int companyId,
                                     Principal principal) {
        TenantScopeGuard.ResolvedTenantScope scope =
                tenantScopeGuard.requireScope(principal.getName(), companyId, newOrderShiftIn.branchId());
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                scope.companyId(),
                newOrderShiftIn.branchId(),
                "pos.sale.create"
        );
        return ResponseEntity.status(201).body(orderService.createOrder(newOrderShiftIn, scope.companyId()).orderId());
    }

    @PostMapping("/v2/pos/{companyId}/orders")
    ResponseEntity<com.example.valueinsoftbackend.Model.Response.CreateOrderResult> newOrderV2(
            @Valid @RequestBody CreateOrderRequest newOrderShiftIn,
            @PathVariable @Positive int companyId,
            Principal principal) {
        TenantScopeGuard.ResolvedTenantScope scope =
                tenantScopeGuard.requireScope(principal.getName(), companyId, newOrderShiftIn.branchId());
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                scope.companyId(),
                newOrderShiftIn.branchId(),
                "pos.sale.create"
        );
        com.example.valueinsoftbackend.Model.Response.CreateOrderResult result = orderService.createOrder(newOrderShiftIn, scope.companyId());
        return ResponseEntity.status(result.idempotencyHit() ? 200 : 201).body(result);
    }

    @PostMapping(path = {"/getOrders", "/{companyId}/getOrders"})
    public ArrayList<Order> getProducts(
            @Valid @RequestBody OrderPeriodRequest data,
            @PathVariable(value = "companyId", required = false) Integer pathCompanyId,
            @RequestParam(value = "companyId", required = false) Integer queryCompanyId,
            Principal principal
    ) {
        Integer requestedCompanyId = pathCompanyId != null ? pathCompanyId : queryCompanyId;
        TenantScopeGuard.ResolvedTenantScope scope =
                tenantScopeGuard.requireScope(principal.getName(), requestedCompanyId, data.branchId());
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                scope.companyId(),
                data.branchId(),
                "pos.sale.read"
        );
        return orderService.getOrdersByPeriod(data, scope.companyId());
    }

    @RequestMapping(path = "/getOrdersByClientId/{companyId}/{branchId}/{clientId}", method = RequestMethod.GET)
    public ArrayList<Order> getOrdersByClientId(@PathVariable @Positive int clientId,
                                                @PathVariable @Positive int branchId,
                                                @PathVariable @Positive int companyId,
                                                Principal principal) {
        TenantScopeGuard.ResolvedTenantScope scope =
                tenantScopeGuard.requireScope(principal.getName(), companyId, branchId);
        authorizationService.assertAuthenticatedCapability(principal.getName(), scope.companyId(), branchId, "pos.sale.read");
        return dbPosOrder.getOrdersByClientId(clientId, branchId, scope.companyId());
    }

    @GetMapping("/{companyId}/search/{branchId}")
    public ResponseEntity<Order> searchOrderByReceipt(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            @RequestParam("q") String receiptNumber,
            Principal principal) {
        TenantScopeGuard.ResolvedTenantScope scope =
                tenantScopeGuard.requireScope(principal.getName(), companyId, branchId);
        authorizationService.assertAuthenticatedCapability(principal.getName(), scope.companyId(), branchId, "pos.sale.read");
        Order order = dbPosOrder.getOrderByReceiptNumber(scope.companyId(), branchId, receiptNumber);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    @RequestMapping(path = "/getOrdersDetailsByOrderId/{companyId}/{branchId}/{orderId}", method = RequestMethod.GET)
    public ArrayList<OrderDetails> ordersDetailsByOrderId(@PathVariable @Positive int orderId,
                                                          @PathVariable @Positive int branchId,
                                                          @PathVariable @Positive int companyId,
                                                          Principal principal) {
        TenantScopeGuard.ResolvedTenantScope scope =
                tenantScopeGuard.requireScope(principal.getName(), companyId, branchId);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                scope.companyId(),
                branchId,
                "pos.sale.read"
        );
        return dbPosOrder.getOrdersDetailsByOrderId(orderId, branchId, scope.companyId());
    }

    @RequestMapping(value = "/{companyId}/bounceBackProduct", method = RequestMethod.POST)
    String bounceBackProduct(@Valid @RequestBody BounceBackOrderRequest data,
                             @PathVariable @Positive int companyId,
                             Principal principal) {
        TenantScopeGuard.ResolvedTenantScope scope =
                tenantScopeGuard.requireScope(principal.getName(), companyId, data.getBranchId());
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                scope.companyId(),
                data.getBranchId(),
                "pos.sale.edit"
        );
        return orderService.bounceBackProduct(data, scope.companyId());
    }
}

