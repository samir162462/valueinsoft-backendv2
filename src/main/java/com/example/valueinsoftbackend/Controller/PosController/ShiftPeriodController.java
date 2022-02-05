package com.example.valueinsoftbackend.Controller.PosController;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/shiftPeriod")
@CrossOrigin("*")
public class ShiftPeriodController {


    @PostMapping("/{branchId}/startShift")
    ShiftPeriod startShift(@PathVariable int branchId) {
        return DbPosShiftPeriod.startShiftPeriod(branchId);
    }

    @PostMapping("/{spId}/endShift")
    String endShift( @PathVariable int spId) {
        DbPosShiftPeriod.endShiftPeriod(spId);
        return "The Shift Ended";
    }
    // dealingWithCurrentShiftData
    @RequestMapping(value = "/currentShift" , method = RequestMethod.POST)
    ShiftPeriod currentShift( @RequestBody Map<String,Object> data) {
        return DbPosShiftPeriod.dealingWithCurrentShiftData((int)data.get("branchId"),(boolean)data.get("getDetails"));
    }
    @RequestMapping(value = "/ShiftOrdersById" , method = RequestMethod.POST)
    ArrayList<Order> ShiftOrdersById(@RequestBody Map<String,Object> data) {
        return DbPosShiftPeriod.ShiftOrdersByPeriod((int)data.get("branchId"),(int) data.get("spId"));
    }

    //toDO Branch
    @RequestMapping(value = "/{branchId}/branchShifts" , method = RequestMethod.GET)
    ArrayList<ShiftPeriod> ShiftsByBranchId(@PathVariable int branchId) {
        return DbPosShiftPeriod.currentBranchShiftData(branchId);
    }

}
