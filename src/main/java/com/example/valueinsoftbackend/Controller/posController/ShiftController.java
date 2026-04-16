package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.Request.CashMovementRequest;
import com.example.valueinsoftbackend.Model.Request.CloseShiftRequest;
import com.example.valueinsoftbackend.Model.Request.CurrentShiftRequest;
import com.example.valueinsoftbackend.Model.Request.OpenShiftRequest;
import com.example.valueinsoftbackend.Model.Request.ShiftOrdersRequest;
import com.example.valueinsoftbackend.Model.Shift.Shift;
import com.example.valueinsoftbackend.Model.Shift.ShiftPeriod;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.ShiftService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/shiftPeriod")
public class ShiftController {

    private final ShiftService shiftService;
    private final AuthorizationService authorizationService;

    public ShiftController(ShiftService shiftService,
                           AuthorizationService authorizationService) {
        this.shiftService = shiftService;
        this.authorizationService = authorizationService;
    }

    // ── lifecycle ───────────────────────────────────────

    /**
     * Opens a new shift with opening float and cashier assignment.
     * Idempotent — returns existing open shift if one is active.
     */
    @PostMapping("/{companyId}/open")
    ResponseEntity<Shift> openShift(
            @Valid @RequestBody OpenShiftRequest request,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, request.getBranchId(),
                "pos.shift.create"
        );
        Shift shift = shiftService.openShift(companyId, request, principal.getName());
        return ResponseEntity.status(201).body(shift);
    }

    /**
     * Returns the currently active shift for a branch, or 204 if none.
     */
    @GetMapping("/{companyId}/{branchId}/active")
    ResponseEntity<Shift> getActiveShift(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, branchId, "pos.shift.read"
        );
        Shift shift = shiftService.getActiveShift(companyId, branchId);
        if (shift == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(shift);
    }

    /**
     * Returns reconciliation data for the close flow.
     */
    @GetMapping("/{companyId}/shift/{shiftId}/reconciliation")
    ResponseEntity<Map<String, Object>> getReconciliation(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int shiftId,
            Principal principal
    ) {
        Shift shift = shiftService.getShiftById(companyId, shiftId);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, shift.getBranchId(), "pos.shift.read"
        );
        return ResponseEntity.ok(shiftService.getReconciliation(companyId, shiftId));
    }

    /**
     * Records a cash movement during an active shift.
     */
    @PostMapping("/{companyId}/shift/{shiftId}/cash-movement")
    ResponseEntity<Map<String, Object>> recordCashMovement(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int shiftId,
            @Valid @RequestBody CashMovementRequest request,
            Principal principal
    ) {
        Shift shift = shiftService.getShiftById(companyId, shiftId);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, shift.getBranchId(), "pos.shift.edit"
        );
        return ResponseEntity.ok(shiftService.recordCashMovement(
                companyId, shiftId, request, principal.getName()));
    }

    /**
     * Closes a shift with server-side cash reconciliation.
     */
    @PostMapping("/{companyId}/shift/{shiftId}/close")
    ResponseEntity<Shift> closeShift(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int shiftId,
            @Valid @RequestBody CloseShiftRequest request,
            Principal principal
    ) {
        Shift shift = shiftService.getShiftById(companyId, shiftId);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, shift.getBranchId(), "pos.shift.close"
        );
        Shift closedShift = shiftService.closeShiftWithReconciliation(
                companyId, shiftId, request, principal.getName());
        return ResponseEntity.ok(closedShift);
    }

    /**
     * Force-closes a shift (manager action).
     */
    @PostMapping("/{companyId}/shift/{shiftId}/force-close")
    ResponseEntity<Shift> forceCloseShift(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int shiftId,
            @RequestBody Map<String, String> body,
            Principal principal
    ) {
        Shift shift = shiftService.getShiftById(companyId, shiftId);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, shift.getBranchId(), "pos.shift.force_close"
        );
        String reason = body.getOrDefault("reason", "Force closed by manager");
        Shift closedShift = shiftService.forceCloseShift(
                companyId, shiftId, reason, principal.getName());
        return ResponseEntity.ok(closedShift);
    }

    /**
     * Returns a shift by its ID (enriched with orders and totals).
     */
    @GetMapping("/{companyId}/shift/{shiftId}")
    ResponseEntity<Shift> getShiftById(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int shiftId,
            Principal principal
    ) {
        Shift shift = shiftService.getShiftById(companyId, shiftId);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, shift.getBranchId(), "pos.shift.read"
        );
        return ResponseEntity.ok(shift);
    }

    /**
     * Returns the audit event log for a shift.
     */
    @GetMapping("/{companyId}/shift/{shiftId}/events")
    ResponseEntity<List<Map<String, Object>>> getShiftEvents(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int shiftId,
            Principal principal
    ) {
        Shift shift = shiftService.getShiftById(companyId, shiftId);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, shift.getBranchId(), "pos.shift.read"
        );
        return ResponseEntity.ok(shiftService.getShiftEvents(companyId, shiftId));
    }

    /**
     * Returns all shifts for a branch.
     */
    @GetMapping("/{companyId}/{branchId}/shifts")
    ResponseEntity<ArrayList<Shift>> getBranchShifts(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, branchId, "pos.shift.read"
        );
        return ResponseEntity.ok(shiftService.getBranchShifts(companyId, branchId));
    }

    // ── legacy COMPATIBILITY ──────────────────────────

    @PostMapping("/{companyId}/{branchId}/startShift")
    @Deprecated
    ResponseEntity<Object> startShift(
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, branchId, "pos.shift.create"
        );
        return shiftService.startShift(companyId, branchId);
    }

    @PostMapping("/{companyId}/{spId}/endShift")
    @Deprecated
    String endShift(
            @PathVariable @Positive int spId,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        int branchId = shiftService.getShiftBranchId(companyId, spId);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, branchId, "pos.shift.edit"
        );
        return shiftService.endShift(companyId, spId);
    }

    @PostMapping("/{companyId}/currentShift")
    @Deprecated
    ShiftPeriod currentShift(
            @Valid @RequestBody CurrentShiftRequest data,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, data.getBranchId(),
                data.isGetDetails() ? "pos.sale.read" : "pos.shift.read"
        );
        return shiftService.currentShift(companyId, data);
    }

    @PostMapping("/{companyId}/ShiftOrdersById")
    @Deprecated
    ArrayList<Order> shiftOrdersById(
            @Valid @RequestBody ShiftOrdersRequest data,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, data.getBranchId(), "pos.sale.read"
        );
        return shiftService.shiftOrdersById(companyId, data);
    }

    @GetMapping("/{companyId}/{branchId}/branchShifts")
    @Deprecated
    ArrayList<ShiftPeriod> shiftsByBranchId(
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, branchId, "pos.shift.read"
        );
        return shiftService.shiftsByBranchId(companyId, branchId);
    }
}
