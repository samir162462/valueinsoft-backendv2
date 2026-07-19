package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.HR.Employee;
import com.example.valueinsoftbackend.Model.HR.EmployeeShift;
import com.example.valueinsoftbackend.Model.HR.Shift;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.Service.HRService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/hr")
public class HRController {

    private final HRService hrService;
    private final AuthorizationService authorizationService;

    public HRController(HRService hrService, AuthorizationService authorizationService) {
        this.hrService = hrService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{companyId}/{branchId}/employees")
    public List<Employee> getEmployees(@PathVariable int companyId,
                                       @PathVariable int branchId,
                                       @RequestParam(defaultValue = "branch") String scope,
                                       Principal principal) {
        boolean companyScope = "company".equalsIgnoreCase(scope);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, companyScope ? null : branchId,
                companyScope ? "hr.employee.read.company" : "hr.employee.read"
        );
        return hrService.getEmployees(companyId, branchId, companyScope, principal.getName());
    }

    @PostMapping("/{companyId}/{branchId}/sync-users")
    public ResponseEntity<Integer> syncUsers(@PathVariable int companyId, @PathVariable int branchId, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.employee.create");
        int count = hrService.ensureBranchWorkspace(companyId, branchId, principal.getName());
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{companyId}/my-employee-record")
    public ResponseEntity<Employee> getMyEmployeeRecord(@PathVariable int companyId, Principal principal) {
        return ResponseEntity.ok(hrService.getEmployeeByUsername(companyId, principal.getName()));
    }

    @GetMapping("/{companyId}/{branchId}/shifts")
    public List<Shift> getShifts(@PathVariable int companyId, @PathVariable int branchId, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.shift.read");
        return hrService.getShifts(companyId, branchId, principal.getName());
    }

    @PostMapping("/{companyId}/{branchId}/shifts")
    public ResponseEntity<Integer> createShift(@PathVariable int companyId, @PathVariable int branchId, @RequestBody Shift shift, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.shift.create");
        int id = hrService.createShift(companyId, branchId, shift, principal.getName());
        return ResponseEntity.ok(id);
    }

    @PutMapping("/{companyId}/{branchId}/shifts/{shiftId}")
    public ResponseEntity<Shift> updateShift(@PathVariable int companyId,
                                              @PathVariable int branchId,
                                              @PathVariable int shiftId,
                                              @RequestBody Shift shift,
                                              Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.shift.edit");
        return ResponseEntity.ok(hrService.updateShift(companyId, branchId, shiftId, shift, principal.getName()));
    }

    @PostMapping("/{companyId}/{branchId}/assign")
    public ResponseEntity<Void> assignShift(@PathVariable int companyId, @PathVariable int branchId, @RequestBody EmployeeShift assignment, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.shift.assign");
        hrService.assignShiftByUser(companyId, branchId, assignment, principal.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{companyId}/{branchId}/assignments")
    public List<EmployeeShift> getAssignments(@PathVariable int companyId, @PathVariable int branchId, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.shift.read");
        return hrService.getAssignments(companyId, branchId, principal.getName());
    }
}
