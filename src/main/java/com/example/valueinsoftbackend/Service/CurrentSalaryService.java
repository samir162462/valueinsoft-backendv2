package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.Model.Payroll.CurrentSalaryView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CurrentSalaryService {

    private final DbPayroll dbPayroll;
    private final HRService hrService;

    public CurrentSalaryService(DbPayroll dbPayroll, HRService hrService) {
        this.dbPayroll = dbPayroll;
        this.hrService = hrService;
    }

    public List<CurrentSalaryView> listAll(int companyId, Integer branchId, String filter, String actor) {
        hrService.getEmployees(companyId, branchId, branchId == null, actor);
        String setupStatus = "ALL".equalsIgnoreCase(String.valueOf(filter)) ? null : filter;
        return dbPayroll.listCurrentSalaries(companyId, branchId, setupStatus);
    }
}
