package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbHR;
import com.example.valueinsoftbackend.Model.HR.AttendanceDay;
import com.example.valueinsoftbackend.Model.HR.AttendanceMonthResponse;
import com.example.valueinsoftbackend.Model.HR.AttendanceSelfStatus;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.Service.HRService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
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
            @RequestParam(defaultValue = "branch") String scope,
            Principal principal) {
        boolean companyScope = "company".equalsIgnoreCase(scope);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, companyScope ? null : branchId,
                companyScope ? "attendance.report.company" : "attendance.report.view"
        );
        if (companyScope) hrService.ensureCompanyWorkspace(companyId, principal.getName());
        else hrService.ensureBranchWorkspace(companyId, branchId, principal.getName());
        return dbHR.getAttendanceReport(companyId, companyScope ? null : branchId, start, end);
    }

    @GetMapping("/{companyId}/{branchId}/month")
    public AttendanceMonthResponse getMonth(
            @PathVariable int companyId,
            @PathVariable int branchId,
            @RequestParam String month,
            @RequestParam(defaultValue = "branch") String scope,
            Principal principal) {
        boolean companyScope = "company".equalsIgnoreCase(scope);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, companyScope ? null : branchId,
                companyScope ? "attendance.report.company" : "attendance.report.view"
        );
        try {
            return hrService.getAttendanceMonth(companyId, branchId, YearMonth.parse(month), companyScope, principal.getName());
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_MONTH", "Month must use YYYY-MM format");
        }
    }

    @PostMapping("/{companyId}/{branchId}/correct")
    public ResponseEntity<Void> correctAttendance(
            @PathVariable int companyId,
            @PathVariable int branchId,
            @RequestBody Map<String, Object> payload,
            Principal principal) {
        
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "attendance.correction.create");

        int userId = ((Number) payload.get("userId")).intValue();
        Date date = Date.valueOf((String) payload.get("date"));
        String actionType = (String) payload.get("actionType");
        Timestamp time = new Timestamp((Long) payload.get("time"));
        String reason = (String) payload.get("reason");

        hrService.manualCorrection(companyId, branchId, userId, date.toLocalDate(), actionType, time, reason, principal.getName());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{companyId}/{branchId}/me/status")
    public AttendanceSelfStatus getMyAttendanceStatus(@PathVariable int companyId,
                                                      @PathVariable int branchId,
                                                      Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "attendance.self.use");
        return hrService.getSelfStatus(companyId, branchId, principal.getName());
    }

    @GetMapping("/{companyId}/{branchId}/me/history")
    public List<AttendanceDay> getMyAttendanceHistory(@PathVariable int companyId,
                                                       @PathVariable int branchId,
                                                       @RequestParam(defaultValue = "14") int days,
                                                       Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "attendance.self.use");
        if (days < 1 || days > 90) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ATTENDANCE_HISTORY_DAYS_INVALID",
                    "History days must be between 1 and 90");
        }
        AttendanceSelfStatus selfStatus = hrService.getSelfStatus(companyId, branchId, principal.getName());
        if (!selfStatus.isEligible()) {
            throw new ApiException(HttpStatus.FORBIDDEN, selfStatus.getIneligibleReason(),
                    "Attendance is not available in this branch");
        }
        Date end = Date.valueOf(java.time.LocalDate.now());
        Date start = Date.valueOf(end.toLocalDate().minusDays(days - 1L));
        return dbHR.getAttendanceForUserBranchPeriod(companyId, branchId, selfStatus.getUserId(), start, end);
    }

    @PostMapping("/{companyId}/{branchId}/me/action")
    public AttendanceSelfStatus performMyAttendanceAction(@PathVariable int companyId,
                                                          @PathVariable int branchId,
                                                          @RequestBody Map<String, String> payload,
                                                          HttpServletRequest request,
                                                          Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "attendance.self.use");
        return hrService.processSelfAttendanceAction(
                companyId,
                branchId,
                principal.getName(),
                payload.get("actionType"),
                payload.getOrDefault("source", "web-self"),
                payload.get("deviceId"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
    }
}
