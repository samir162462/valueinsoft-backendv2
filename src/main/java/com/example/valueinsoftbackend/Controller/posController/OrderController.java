package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.Model.Request.BounceBackOrderRequest;
import com.example.valueinsoftbackend.Model.Request.CreateOrderRequest;
import com.example.valueinsoftbackend.Service.OrderService;
import com.example.valueinsoftbackend.util.ConvertStringToTimeStamp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/Order")
public class OrderController {

    private final DbPosOrder dbPosOrder;
    private final OrderService orderService;

    @Autowired
    public OrderController(DbPosOrder dbPosOrder, OrderService orderService) {
        this.dbPosOrder = dbPosOrder;
        this.orderService = orderService;
    }

    @PostMapping("/{companyId}/saveOrder")
    ResponseEntity<Integer> newOrder(@Valid @RequestBody CreateOrderRequest newOrderShiftIn, @PathVariable int companyId) {
        return ResponseEntity.status(201).body(orderService.createOrder(newOrderShiftIn, companyId));
    }

    @RequestMapping(path = "/getOrders", method = RequestMethod.GET)
    public ArrayList<Order> getProducts(@RequestBody Map<String, Object> data, @PathVariable int companyId) {
        return dbPosOrder.getOrdersByPeriod(
                (int) data.get("branchId"),
                ConvertStringToTimeStamp.convertString(data.get("startTime").toString()),
                ConvertStringToTimeStamp.convertString(data.get("endTime").toString()),
                companyId
        );
    }

    @RequestMapping(path = "/getOrdersByClientId/{companyId}/{branchId}/{clientId}", method = RequestMethod.GET)
    public ArrayList<Order> getOrdersByClientId(@PathVariable int clientId, @PathVariable int branchId, @PathVariable int companyId) {
        return dbPosOrder.getOrdersByClientId(clientId, branchId, companyId);
    }

    @RequestMapping(path = "/getOrdersDetailsByOrderId/{companyId}/{branchId}/{orderId}", method = RequestMethod.GET)
    public ArrayList<OrderDetails> ordersDetailsByOrderId(@PathVariable int orderId, @PathVariable int branchId, @PathVariable int companyId) {
        return dbPosOrder.getOrdersDetailsByOrderId(orderId, branchId, companyId);
    }

    @RequestMapping(value = "/{companyId}/bounceBackProduct", method = RequestMethod.POST)
    String bounceBackProduct(@Valid @RequestBody BounceBackOrderRequest data, @PathVariable int companyId) {
        return orderService.bounceBackProduct(data, companyId);
    }
}
