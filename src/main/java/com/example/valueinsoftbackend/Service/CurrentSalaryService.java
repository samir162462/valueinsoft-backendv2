package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.Model.Payroll.CurrentSalaryView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CurrentSalaryService {

    private final DbPayroll dbPayroll;

    public CurrentSalaryService(DbPayroll dbPayroll) {
        this.dbPayroll = dbPayroll;
    }

    public List<CurrentSalaryView> listAll(int companyId, Integer branchId, String filter) {
        String setupStatus = "ALL".equalsIgnoreCase(String.valueOf(filter)) ? null : filter;
        return dbPayroll.listCurrentSalaries(companyId, branchId, setupStatus);
    }
}
