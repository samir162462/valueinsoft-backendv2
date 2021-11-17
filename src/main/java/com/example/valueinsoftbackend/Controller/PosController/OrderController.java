package com.example.valueinsoftbackend.Controller.PosController;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/Order")
@CrossOrigin("*")
public class OrderController {




    @PostMapping("/saveOrder")
    String newOrder(@RequestBody Order newOrderShiftIn) {


        return DbPosOrder.AddOrder(newOrderShiftIn);
    }




}
