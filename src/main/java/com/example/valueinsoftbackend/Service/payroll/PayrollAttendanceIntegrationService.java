package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbHR;
import com.example.valueinsoftbackend.Model.HR.AttendanceDay;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PayrollAttendanceIntegrationService {

    private final DbHR dbHR;

    public PayrollAttendanceIntegrationService(DbHR dbHR) {
        this.dbHR = dbHR;
    }

    public List<AttendanceDay> getAttendanceForPeriod(int companyId, int employeeId, Date start, Date end) {
        List<AttendanceDay> days = new ArrayList<>();
        LocalDate cursor = start.toLocalDate();
        LocalDate last = end.toLocalDate();
        while (!cursor.isAfter(last)) {
            AttendanceDay day = dbHR.getAttendanceDay(companyId, employeeId, Date.valueOf(cursor));
            if (day != null) {
                days.add(day);
            }
            cursor = cursor.plusDays(1);
        }
        return days;
    }
}
