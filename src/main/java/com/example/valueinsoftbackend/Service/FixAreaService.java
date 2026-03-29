package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbSlotsFixArea;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.FixAreaSlotCreateRequest;
import com.example.valueinsoftbackend.Model.Request.FixAreaSlotUpdateRequest;
import com.example.valueinsoftbackend.Model.Slots.SlotsFixArea;
import com.example.valueinsoftbackend.util.RequestDateParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class FixAreaService {

    private final DbSlotsFixArea dbSlotsFixArea;

    public FixAreaService(DbSlotsFixArea dbSlotsFixArea) {
        this.dbSlotsFixArea = dbSlotsFixArea;
    }

    public List<SlotsFixArea> getFixAreaSlots(int companyId, int branchId, int month) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        return dbSlotsFixArea.getFixAreaSlot(companyId, branchId, month);
    }

    @Transactional
    public String createFixAreaSlot(int companyId, int branchId, FixAreaSlotCreateRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        if (request.getBranchId() != branchId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BRANCH_ID_MISMATCH", "branchId in the body does not match the path");
        }

        SlotsFixArea slotsFixArea = new SlotsFixArea(
                request.getFaId(),
                request.getFixSlot(),
                request.getClientId(),
                RequestDateParser.parseSqlDate(request.getDateIn(), "dateIn"),
                RequestDateParser.parseSqlDate(request.getDateFinished(), "dateFinished"),
                request.getPhoneName().trim(),
                request.getProblem().trim(),
                request.isShow(),
                request.getUserName_Recived().trim(),
                request.getStatus().trim(),
                normalizeNullable(request.getDesc()),
                request.getBranchId(),
                request.getFees()
        );

        int rows = dbSlotsFixArea.insertFixAreaSlot(companyId, slotsFixArea);
        if (rows != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FIX_AREA_CREATE_FAILED", "The Fix Slot Not Added (Error)");
        }

        log.info("Created fix-area slot {} for company {} branch {}", request.getFixSlot(), companyId, branchId);
        return "The Fix Slot Added (success)";
    }

    @Transactional
    public String updateFixAreaSlot(int companyId, FixAreaSlotUpdateRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        SlotsFixArea slotsFixArea = new SlotsFixArea(
                request.getFaId(),
                0,
                0,
                null,
                RequestDateParser.parseSqlDate(request.getDateFinished(), "dateFinished"),
                null,
                request.getProblem().trim(),
                request.isShow(),
                null,
                request.getStatus().trim(),
                normalizeNullable(request.getDesc()),
                request.getBranchId(),
                request.getFees()
        );

        int rows = dbSlotsFixArea.updateFixAreaSlot(companyId, slotsFixArea);
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FIX_AREA_SLOT_NOT_FOUND", "The Fix Slot Not Updated (Failed)");
        }

        log.info("Updated fix-area slot {} for company {} branch {}", request.getFaId(), companyId, request.getBranchId());
        return "The Fix Slot Updated (success)";
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }
}
