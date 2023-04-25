package com.example.valueinsoftbackend.Controller.posController;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/shiftPeriod")
@CrossOrigin("*")
public class ShiftPeriodController {


    @PostMapping("/{companyId}/{branchId}/startShift")
    ResponseEntity<Object> startShift(@PathVariable int branchId, @PathVariable int companyId) {
        System.out.println("in Start Shift");
        return DbPosShiftPeriod.startShiftPeriod(companyId,branchId);
    }

    @PostMapping("/{companyId}/{spId}/endShift")
    String endShift( @PathVariable int spId,@PathVariable int companyId) {
        DbPosShiftPeriod.endShiftPeriod(companyId,spId);
        return "The Shift Ended";
    }
    // dealingWithCurrentShiftData
    @RequestMapping(value = "/{companyId}/currentShift" , method = RequestMethod.POST)
    ShiftPeriod currentShift( @RequestBody Map<String,Object> data,@PathVariable int companyId) {
        return DbPosShiftPeriod.dealingWithCurrentShiftData(companyId,(int)data.get("branchId"),(boolean)data.get("getDetails"));
    }
    @RequestMapping(value = "/{companyId}/ShiftOrdersById" , method = RequestMethod.POST)
    ArrayList<Order> ShiftOrdersById(@RequestBody Map<String,Object> data ,@PathVariable int companyId) {
        System.out.println();
        return DbPosShiftPeriod.ShiftOrdersByPeriod(companyId,(int)data.get("branchId"),(int) data.get("spId"));
    }

    //toDO Branch
    @RequestMapping(value = "/{companyId}/{branchId}/branchShifts" , method = RequestMethod.GET)
    ArrayList<ShiftPeriod> ShiftsByBranchId(@PathVariable int branchId,@PathVariable int companyId) {
        return DbPosShiftPeriod.currentBranchShiftData(companyId,branchId);
    }

}
