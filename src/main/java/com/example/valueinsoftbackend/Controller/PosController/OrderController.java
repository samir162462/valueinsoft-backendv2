package com.example.valueinsoftbackend.Controller.PosController;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import com.example.valueinsoftbackend.util.ConvertStringToTimeStamp;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/Order")
@CrossOrigin("*")
public class OrderController {




    @PostMapping("/saveOrder")
    String newOrder(@RequestBody Order newOrderShiftIn) {


        return "The orderId is: "+DbPosOrder.AddOrder(newOrderShiftIn);
    }

    @RequestMapping(path = "/getOrders", method = RequestMethod.GET)
    public ArrayList<Order> getProducts(@RequestBody Map<String,Object> data) {

        ArrayList<Order>orderArrayList = DbPosOrder.getOrdersByPeriod(
                (int) data.get("branchId"),
                ConvertStringToTimeStamp.convertString(data.get("startTime").toString()),
                ConvertStringToTimeStamp.convertString(data.get("endTime").toString())
        );
        return orderArrayList;
    }

    @RequestMapping(value = "/bounceBackProduct" , method = RequestMethod.POST)
    String bounceBackProduct(@RequestBody Map<String,Integer> data) {
        return DbPosOrder.bounceBackOrderDetailItem(data.get("odId"),data.get("branchId"));
    }



}
