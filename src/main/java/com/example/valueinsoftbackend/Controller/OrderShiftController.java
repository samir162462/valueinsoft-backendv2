package com.example.valueinsoftbackend.Controller;


import com.example.valueinsoftbackend.Model.OrderShift;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/shiftOrders")
@CrossOrigin("*")
public class OrderShiftController {

    @RequestMapping(value = "/shiftOrderLastNum", method = RequestMethod.GET)
    @ResponseBody
    public  ArrayList<OrderShift> getOrdersByLastNumbers(



            @RequestParam("id") int id

    ) {
        ArrayList<OrderShift> orderShiftList = new ArrayList<>();
       try {
           for (int i = ValueinsoftBackendApplication.orderShiftArrayList.size()-id; i < ValueinsoftBackendApplication.orderShiftArrayList.size(); i++) {
               OrderShift o = ValueinsoftBackendApplication.orderShiftArrayList.get(i);

               orderShiftList.add(o);

           }
           return orderShiftList;
       }catch (Exception e)
       {
           for (int i = 0; i < ValueinsoftBackendApplication.orderShiftArrayList.size(); i++) {
               OrderShift o = ValueinsoftBackendApplication.orderShiftArrayList.get(i);

               orderShiftList.add(o);

           }
           return orderShiftList;

       }
    }



    @RequestMapping(value = "/shiftOrderId", method = RequestMethod.GET)
    @ResponseBody
    public OrderShift getOrdersById(



            @RequestParam("id") String id

    ) {
        System.out.println("id "+ValueinsoftBackendApplication.orderShiftArrayList.size());
        for (int i = 0; i < ValueinsoftBackendApplication.orderShiftArrayList.size(); i++) {
            OrderShift o = ValueinsoftBackendApplication.orderShiftArrayList.get(i);
            System.out.println(o.getOrderId());
            if (String.valueOf(o.getOrderId()).equals(id)) {
                return o;

            }


        }
        return null;
    }



    @PostMapping("/saveShiftOrder")
    OrderShift newOrder(@RequestBody OrderShift newOrderShiftIn) {
        System.out.println(newOrderShiftIn.toString());
        ValueinsoftBackendApplication.orderShiftArrayList.add(newOrderShiftIn);
        System.out.println(ValueinsoftBackendApplication.productArrayList.size());
        return newOrderShiftIn;
    }




}
