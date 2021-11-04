package com.example.valueinsoftbackend.Controller;


import com.example.valueinsoftbackend.Model.OrderShift;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

@RestController
@RequestMapping("/ShiftPeriod")
@CrossOrigin("*")
public class ShiftPeriodController {

    @RequestMapping(value = "/All", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<ShiftPeriod> allShifts()
    {
        return ValueinsoftBackendApplication.shiftPeriodArrayList;
    }

    @RequestMapping(value = "/shiftId", method = RequestMethod.GET)
    @ResponseBody
    public ShiftPeriod getOrdersById(



            @RequestParam("id") String id

    ) {
        System.out.println("id "+ValueinsoftBackendApplication.shiftPeriodArrayList.size());
        for (int i = 0; i < ValueinsoftBackendApplication.shiftPeriodArrayList.size(); i++) {
            ShiftPeriod o = ValueinsoftBackendApplication.shiftPeriodArrayList.get(i);
            System.out.println(o.getShiftID());
            if (String.valueOf(o.getShiftID()).equals(id)) {
                return o;

            }


        }
        return null;
    }


    @PostMapping("/endShiftOrder")
    OrderShift endShiftOrder(@RequestBody OrderShift newOrderShiftIn) {
        System.out.println(newOrderShiftIn.toString());
        ValueinsoftBackendApplication.orderShiftArrayList.add(newOrderShiftIn);
        System.out.println(ValueinsoftBackendApplication.productArrayList.size());
        return newOrderShiftIn;
    }


    @PostMapping("/StartShiftOrder")
    String StartShiftOrder() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        System.out.println(dtf.format(now));
        ShiftPeriod sp = new ShiftPeriod(10,now+"","Running",new ArrayList<>());
        ValueinsoftBackendApplication.shiftPeriodArrayList.add(sp);

        return "The Shift Started at "+now+" with Id: "+10;
    }



}

