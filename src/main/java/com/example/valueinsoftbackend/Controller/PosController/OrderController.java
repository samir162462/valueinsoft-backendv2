package com.example.valueinsoftbackend.Controller.PosController;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import com.example.valueinsoftbackend.util.ConvertStringToTimeStamp;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/Order")
@CrossOrigin("*")
public class OrderController {




    @PostMapping("/{companyId}/saveOrder")
    ResponseEntity<Integer> newOrder(@RequestBody Order newOrderShiftIn , @PathVariable int companyId) {


        return DbPosOrder.AddOrder(newOrderShiftIn,companyId);
    }

    @RequestMapping(path = "/getOrders", method = RequestMethod.GET)
    public ArrayList<Order> getProducts(@RequestBody Map<String,Object> data ,@PathVariable int companyId) {
        System.out.println("getOrders--------");
        ArrayList<Order>orderArrayList = DbPosOrder.getOrdersByPeriod(
                (int) data.get("branchId"),
                ConvertStringToTimeStamp.convertString(data.get("startTime").toString()),
                ConvertStringToTimeStamp.convertString(data.get("endTime").toString()),
                companyId
        );
        return orderArrayList;
    }
    @RequestMapping(path = "/getOrdersByClientId/{companyId}/{branchId}/{clientId}", method = RequestMethod.GET)
    public ArrayList<Order> getOrdersByClientId(@PathVariable int clientId,@PathVariable int branchId ,@PathVariable int companyId) {

        ArrayList<Order>orderArrayList = DbPosOrder.getOrdersByClientId(
        clientId,branchId,companyId
        );
        return orderArrayList;
    }
    @RequestMapping(path = "/getOrdersDetailsByOrderId/{companyId}/{branchId}/{orderId}", method = RequestMethod.GET)
    public ArrayList<OrderDetails> OrdersDetailsByOrderId(@PathVariable int orderId,@PathVariable int branchId ,@PathVariable int companyId) {

        ArrayList<OrderDetails>detailsArrayList = DbPosOrder.getOrdersDetailsByOrderId(
                orderId,branchId,companyId
        );
        return detailsArrayList;
    }

    @RequestMapping(value = "/{companyId}/bounceBackProduct" , method = RequestMethod.POST)
    String bounceBackProduct(@RequestBody Map<String,Integer> data ,@PathVariable int companyId) {
        return DbPosOrder.bounceBackOrderDetailItem(data.get("odId"),data.get("branchId"),companyId,data.get("toWho"));//1 to inv 2-> to supp
    }



}
