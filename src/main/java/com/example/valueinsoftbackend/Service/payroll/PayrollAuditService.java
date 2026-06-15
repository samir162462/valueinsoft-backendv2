package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.Model.Payroll.PayrollAuditLog;
import org.springframework.stereotype.Service;

@Service
public class PayrollAuditService {

    private final DbPayroll dbPayroll;

    public PayrollAuditService(DbPayroll dbPayroll) {
        this.dbPayroll = dbPayroll;
    }

    public long record(int companyId,
                       Integer branchId,
                       String entityType,
                       String entityId,
                       String action,
                       String oldJson,
                       String newJson,
                       String actor,
                       String remarks) {
        PayrollAuditLog log = new PayrollAuditLog();
        log.setCompanyId(companyId);
        log.setBranchId(branchId);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setOldValueJson(oldJson);
        log.setNewValueJson(newJson);
        log.setPerformedBy(actor);
        log.setRemarks(remarks);
        return dbPayroll.createAuditLog(log);
    }
}
