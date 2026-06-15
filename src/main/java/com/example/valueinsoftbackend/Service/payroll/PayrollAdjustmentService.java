package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Payroll.PayrollAdjustment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class PayrollAdjustmentService {

    private final DbPayroll dbPayroll;
    private final PayrollAuditService auditService;

    public PayrollAdjustmentService(DbPayroll dbPayroll, PayrollAuditService auditService) {
        this.dbPayroll = dbPayroll;
        this.auditService = auditService;
    }

    public List<PayrollAdjustment> list(int companyId, Integer branchId, Integer employeeId, String status) {
        return dbPayroll.listAdjustments(companyId, branchId, employeeId, status);
    }

    public PayrollAdjustment get(int companyId, long id) {
        PayrollAdjustment adjustment = dbPayroll.getAdjustment(companyId, id);
        if (adjustment == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYROLL_ADJUSTMENT_NOT_FOUND", "Payroll adjustment was not found");
        }
        return adjustment;
    }

    @Transactional
    public PayrollAdjustment create(String actor, PayrollAdjustment adjustment) {
        adjustment.setStatus(adjustment.getStatus() == null ? "PENDING" : adjustment.getStatus());
        adjustment.setCreatedBy(actor);
        adjustment.setUpdatedBy(actor);
        long id = dbPayroll.createAdjustment(adjustment);
        auditService.record(adjustment.getCompanyId(), adjustment.getBranchId(), "adjustment", String.valueOf(id),
                "ADJUSTMENT_CREATED", null, null, actor, "Payroll adjustment created");
        return get(adjustment.getCompanyId(), id);
    }

    @Transactional
    public PayrollAdjustment approve(String actor, int companyId, long id) {
        PayrollAdjustment adjustment = requirePending(companyId, id);
        adjustment.setStatus("APPROVED");
        adjustment.setApprovedBy(actor);
        adjustment.setApprovedAt(Timestamp.from(Instant.now()));
        adjustment.setUpdatedBy(actor);
        dbPayroll.updateAdjustment(adjustment);
        auditService.record(companyId, adjustment.getBranchId(), "adjustment", String.valueOf(id),
                "ADJUSTMENT_APPROVED", null, null, actor, "Payroll adjustment approved");
        return get(companyId, id);
    }

    @Transactional
    public PayrollAdjustment reject(String actor, int companyId, long id) {
        PayrollAdjustment adjustment = requirePending(companyId, id);
        adjustment.setStatus("REJECTED");
        adjustment.setUpdatedBy(actor);
        dbPayroll.updateAdjustment(adjustment);
        auditService.record(companyId, adjustment.getBranchId(), "adjustment", String.valueOf(id),
                "ADJUSTMENT_REJECTED", null, null, actor, "Payroll adjustment rejected");
        return get(companyId, id);
    }

    @Transactional
    public PayrollAdjustment cancel(String actor, int companyId, long id) {
        PayrollAdjustment adjustment = get(companyId, id);
        if (!"PENDING".equals(adjustment.getStatus()) && !"APPROVED".equals(adjustment.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_ADJUSTMENT_NOT_CANCELLABLE",
                    "Only pending or approved adjustments can be cancelled");
        }
        adjustment.setStatus("CANCELLED");
        adjustment.setUpdatedBy(actor);
        dbPayroll.updateAdjustment(adjustment);
        auditService.record(companyId, adjustment.getBranchId(), "adjustment", String.valueOf(id),
                "ADJUSTMENT_CANCELLED", null, null, actor, "Payroll adjustment cancelled");
        return get(companyId, id);
    }

    private PayrollAdjustment requirePending(int companyId, long id) {
        PayrollAdjustment adjustment = get(companyId, id);
        if (!"PENDING".equals(adjustment.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_ADJUSTMENT_NOT_PENDING",
                    "Only pending adjustments can be changed by this action");
        }
        return adjustment;
    }
}
