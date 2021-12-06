package com.example.valueinsoftbackend.Controller.PosController;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import org.springframework.web.bind.annotation.*;

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


}
