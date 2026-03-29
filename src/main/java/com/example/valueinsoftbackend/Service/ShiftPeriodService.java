package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.Request.CurrentShiftRequest;
import com.example.valueinsoftbackend.Model.Request.ShiftOrdersRequest;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;

@Service
@Slf4j
public class ShiftPeriodService {

    private final DbPosShiftPeriod dbPosShiftPeriod;
    private final DbPosOrder dbPosOrder;

    public ShiftPeriodService(DbPosShiftPeriod dbPosShiftPeriod, DbPosOrder dbPosOrder) {
        this.dbPosShiftPeriod = dbPosShiftPeriod;
        this.dbPosOrder = dbPosOrder;
    }

    @Transactional
    public ResponseEntity<Object> startShift(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");

        if (dbPosShiftPeriod.hasOpenShift(companyId, branchId)) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body("There is Exists Shift opened");
        }

        ShiftPeriod shiftPeriod = dbPosShiftPeriod.insertShift(companyId, branchId, new Timestamp(System.currentTimeMillis()));
        log.info("Started shift {} for company {} branch {}", shiftPeriod.getShiftID(), companyId, branchId);
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftPeriod);
    }

    @Transactional
    public String endShift(int companyId, int shiftPeriodId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(shiftPeriodId, "spId");

        int rows = dbPosShiftPeriod.closeShift(companyId, shiftPeriodId, new Timestamp(System.currentTimeMillis()));
        if (rows > 0) {
            log.info("Ended shift {} for company {}", shiftPeriodId, companyId);
        } else {
            log.warn("Shift {} for company {} was requested to close but no row was updated", shiftPeriodId, companyId);
        }
        return "The Shift Ended";
    }

    public ShiftPeriod currentShift(int companyId, CurrentShiftRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        ShiftPeriod shiftPeriod = dbPosShiftPeriod.getCurrentShift(companyId, request.getBranchId());
        if (shiftPeriod != null && request.isGetDetails()) {
            shiftPeriod.setOrderShiftList(
                    dbPosOrder.getOrdersByPeriod(
                            request.getBranchId(),
                            shiftPeriod.getStartTime(),
                            new Timestamp(System.currentTimeMillis()),
                            companyId
                    )
            );
        }
        return shiftPeriod;
    }

    public ArrayList<Order> shiftOrdersById(int companyId, ShiftOrdersRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbPosOrder.getOrdersByShiftId(companyId, request.getBranchId(), request.getSpId());
    }

    public ArrayList<ShiftPeriod> branchShifts(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        return dbPosShiftPeriod.getBranchShifts(companyId, branchId);
    }
}
