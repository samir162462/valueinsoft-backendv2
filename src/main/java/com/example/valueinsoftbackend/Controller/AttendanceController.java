package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbHR;
import com.example.valueinsoftbackend.Model.HR.AttendanceDay;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.HRService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final DbHR dbHR;
    private final HRService hrService;
    private final AuthorizationService authorizationService;

    public AttendanceController(DbHR dbHR, HRService hrService, AuthorizationService authorizationService) {
        this.dbHR = dbHR;
        this.hrService = hrService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/{companyId}/{branchId}/action")
    public ResponseEntity<Void> performAction(
            @PathVariable int companyId,
            @PathVariable int branchId,
            @RequestBody Map<String, String> payload,
            HttpServletRequest request,
            Principal principal) {
        
        // Kiosk screen must be opened by an authenticated manager
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "attendance.kiosk.use");

        String code = payload.get("code");
        String pin = payload.get("pin");
        String actionType = payload.get("actionType");
        String source = payload.getOrDefault("source", "web-kiosk");
        String deviceId = payload.get("deviceId");
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        hrService.processAttendanceAction(companyId, branchId, code, pin, actionType, source, deviceId, ipAddress, userAgent);
        
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{companyId}/{branchId}/report")
    public List<AttendanceDay> getReport(
            @PathVariable int companyId,
            @PathVariable int branchId,
            @RequestParam Date start,
            @RequestParam Date end,
            Principal principal) {
        
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "attendance.report.read");
        return dbHR.getAttendanceReport(companyId, branchId, start, end);
    }

    @PostMapping("/{companyId}/{branchId}/correct")
    public ResponseEntity<Void> correctAttendance(
            @PathVariable int companyId,
            @PathVariable int branchId,
            @RequestBody Map<String, Object> payload,
            Principal principal) {
        
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "attendance.correction.manage");

        int employeeId = (Integer) payload.get("employeeId");
        Date date = Date.valueOf((String) payload.get("date"));
        String actionType = (String) payload.get("actionType");
        Timestamp time = new Timestamp((Long) payload.get("time"));
        String reason = (String) payload.get("reason");

        hrService.manualCorrection(companyId, branchId, employeeId, date.toLocalDate(), actionType, time, reason, principal.getName());

        return ResponseEntity.ok().build();
    }
}
