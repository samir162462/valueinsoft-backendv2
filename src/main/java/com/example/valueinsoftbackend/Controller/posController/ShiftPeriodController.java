package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.Request.CurrentShiftRequest;
import com.example.valueinsoftbackend.Model.Request.ShiftOrdersRequest;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import com.example.valueinsoftbackend.Service.ShiftPeriodService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.util.ArrayList;

@RestController
@Validated
@RequestMapping("/shiftPeriod")
public class ShiftPeriodController {

    private final ShiftPeriodService shiftPeriodService;

    public ShiftPeriodController(ShiftPeriodService shiftPeriodService) {
        this.shiftPeriodService = shiftPeriodService;
    }

    @PostMapping("/{companyId}/{branchId}/startShift")
    ResponseEntity<Object> startShift(
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId
    ) {
        return shiftPeriodService.startShift(companyId, branchId);
    }

    @PostMapping("/{companyId}/{spId}/endShift")
    String endShift(
            @PathVariable @Positive int spId,
            @PathVariable @Positive int companyId
    ) {
        return shiftPeriodService.endShift(companyId, spId);
    }

    @PostMapping("/{companyId}/currentShift")
    ShiftPeriod currentShift(
            @Valid @RequestBody CurrentShiftRequest data,
            @PathVariable @Positive int companyId
    ) {
        return shiftPeriodService.currentShift(companyId, data);
    }

    @PostMapping("/{companyId}/ShiftOrdersById")
    ArrayList<Order> shiftOrdersById(
            @Valid @RequestBody ShiftOrdersRequest data,
            @PathVariable @Positive int companyId
    ) {
        return shiftPeriodService.shiftOrdersById(companyId, data);
    }

    @GetMapping("/{companyId}/{branchId}/branchShifts")
    ArrayList<ShiftPeriod> shiftsByBranchId(
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId
    ) {
        return shiftPeriodService.branchShifts(companyId, branchId);
    }
}
