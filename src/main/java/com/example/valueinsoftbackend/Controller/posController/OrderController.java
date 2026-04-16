package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.Model.Request.BounceBackOrderRequest;
import com.example.valueinsoftbackend.Model.Request.CreateOrderRequest;
import com.example.valueinsoftbackend.Model.Request.OrderPeriodRequest;
import com.example.valueinsoftbackend.Service.AuthorizationService;
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

    @Autowired
    public OrderController(DbPosOrder dbPosOrder,
                           OrderService orderService,
                           AuthorizationService authorizationService) {
        this.dbPosOrder = dbPosOrder;
        this.orderService = orderService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/{companyId}/saveOrder")
    ResponseEntity<Integer> newOrder(@Valid @RequestBody CreateOrderRequest newOrderShiftIn,
                                     @PathVariable @Positive int companyId,
                                     Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                newOrderShiftIn.branchId(),
                "pos.sale.create"
        );
        return ResponseEntity.status(201).body(orderService.createOrder(newOrderShiftIn, companyId));
    }

    @PostMapping(path = {"/getOrders", "/{companyId}/getOrders"})
    public ArrayList<Order> getProducts(
            @Valid @RequestBody OrderPeriodRequest data,
            @PathVariable(value = "companyId", required = false) Integer pathCompanyId,
            @RequestParam(value = "companyId", required = false) Integer queryCompanyId,
            Principal principal
    ) {
        int companyId = pathCompanyId != null ? pathCompanyId : queryCompanyId == null ? 0 : queryCompanyId;
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                data.branchId(),
                "pos.sale.read"
        );
        return orderService.getOrdersByPeriod(data, companyId);
    }

    @RequestMapping(path = "/getOrdersByClientId/{companyId}/{branchId}/{clientId}", method = RequestMethod.GET)
    public ArrayList<Order> getOrdersByClientId(@PathVariable @Positive int clientId,
                                                @PathVariable @Positive int branchId,
                                                @PathVariable @Positive int companyId,
                                                Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "pos.sale.read"
        );
        return dbPosOrder.getOrdersByClientId(clientId, branchId, companyId);
    }

    @RequestMapping(path = "/getOrdersDetailsByOrderId/{companyId}/{branchId}/{orderId}", method = RequestMethod.GET)
    public ArrayList<OrderDetails> ordersDetailsByOrderId(@PathVariable @Positive int orderId,
                                                          @PathVariable @Positive int branchId,
                                                          @PathVariable @Positive int companyId,
                                                          Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "pos.sale.read"
        );
        return dbPosOrder.getOrdersDetailsByOrderId(orderId, branchId, companyId);
    }

    @RequestMapping(value = "/{companyId}/bounceBackProduct", method = RequestMethod.POST)
    String bounceBackProduct(@Valid @RequestBody BounceBackOrderRequest data,
                             @PathVariable @Positive int companyId,
                             Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                data.getBranchId(),
                "pos.sale.edit"
        );
        return orderService.bounceBackProduct(data, companyId);
    }
}
