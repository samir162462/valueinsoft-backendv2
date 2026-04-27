package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbHR;
import com.example.valueinsoftbackend.Model.HR.Employee;
import com.example.valueinsoftbackend.Model.HR.EmployeeShift;
import com.example.valueinsoftbackend.Model.HR.Shift;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.HRService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/hr")
public class HRController {

    private final DbHR dbHR;
    private final HRService hrService;
    private final AuthorizationService authorizationService;

    public HRController(DbHR dbHR, HRService hrService, AuthorizationService authorizationService) {
        this.dbHR = dbHR;
        this.hrService = hrService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{companyId}/{branchId}/employees")
    public List<Employee> getEmployees(@PathVariable int companyId, @PathVariable int branchId, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.employee.read");
        return dbHR.getAllEmployees(companyId, branchId);
    }

    @PostMapping("/{companyId}/{branchId}/employees")
    public Employee createEmployee(@PathVariable int companyId, @PathVariable int branchId, @RequestBody Employee employee, @RequestParam String pin, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.employee.create");
        employee.setCompanyId(companyId);
        employee.setBranchId(branchId);
        employee.setCreatedBy(principal.getName());
        employee.setUpdatedBy(principal.getName());
        return hrService.createEmployee(employee, pin);
    }

    @PostMapping("/{companyId}/{branchId}/sync-users")
    public ResponseEntity<Integer> syncUsers(@PathVariable int companyId, @PathVariable int branchId, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.employee.create");
        int count = hrService.syncFromUsers(companyId, branchId, principal.getName());
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{companyId}/my-employee-record")
    public ResponseEntity<Employee> getMyEmployeeRecord(@PathVariable int companyId, Principal principal) {
        return ResponseEntity.ok(hrService.getEmployeeByUsername(companyId, principal.getName()));
    }

    @GetMapping("/{companyId}/{branchId}/shifts")
    public List<Shift> getShifts(@PathVariable int companyId, @PathVariable int branchId, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.shift.read");
        return dbHR.getAllShifts(companyId, branchId);
    }

    @PostMapping("/{companyId}/{branchId}/shifts")
    public ResponseEntity<Integer> createShift(@PathVariable int companyId, @PathVariable int branchId, @RequestBody Shift shift, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.shift.create");
        shift.setCompanyId(companyId);
        shift.setBranchId(branchId);
        shift.setCreatedBy(principal.getName());
        shift.setUpdatedBy(principal.getName());
        int id = dbHR.addShift(shift);
        return ResponseEntity.ok(id);
    }

    @PostMapping("/{companyId}/{branchId}/assign")
    public ResponseEntity<Void> assignShift(@PathVariable int companyId, @PathVariable int branchId, @RequestBody EmployeeShift assignment, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.shift.assign");
        assignment.setCompanyId(companyId);
        assignment.setBranchId(branchId);
        assignment.setCreatedBy(principal.getName());
        assignment.setUpdatedBy(principal.getName());
        dbHR.assignShift(assignment);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{companyId}/{branchId}/assignments")
    public List<EmployeeShift> getAssignments(@PathVariable int companyId, @PathVariable int branchId, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "hr.shift.read");
        return dbHR.getAllAssignments(companyId, branchId);
    }
}
