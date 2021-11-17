package com.example.valueinsoftbackend.Controller.PosController;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import org.springframework.web.bind.annotation.*;

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
        return "newProProduct";
    }
}
