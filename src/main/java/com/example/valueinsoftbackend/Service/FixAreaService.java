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
                request.getFees(),request.getImei(),
                request.getDeviceCondition(),request.getAccessories()
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
                request.getFees(),request.getImei(),
                request.getDeviceCondition(),request.getAccessories()
        );

        int rows = dbSlotsFixArea.updateFixAreaSlot(companyId, slotsFixArea);
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FIX_AREA_SLOT_NOT_FOUND", "The Fix Slot Not Updated (Failed)");
        }

        // Save associated parts
        if (request.getUsedParts() != null) {
            dbSlotsFixArea.saveParts(companyId, request.getFaId(), request.getUsedParts());
        }

        log.info("Updated fix-area slot {} for company {} branch {}", request.getFaId(), companyId, request.getBranchId());
        return "The Fix Slot Updated (success)";
    }

    public SlotsFixArea getFixSlotById(int companyId, int branchId, int faId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        SlotsFixArea slot = dbSlotsFixArea.getFixSlotById(companyId, branchId, faId);
        if (slot == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FIX_SLOT_NOT_FOUND", "Repair Ticket not found");
        }
        return slot;
    }

    public List<SlotsFixArea> searchFixSlots(int companyId, String query) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return dbSlotsFixArea.searchFixAreaSlots(companyId, query);
    }

    @Transactional
    public String markSlotPaidAndSave(int companyId, int faId, int orderId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        int rows = dbSlotsFixArea.markSlotPaidAndSave(companyId, faId, orderId);
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FIX_AREA_SLOT_NOT_FOUND", "Could not update order status");
        }
        return "Slot marked as paid successfully";
    }

    @Transactional
    public String reverseRepairPayment(int companyId, int orderId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(orderId, "orderId");
        int rows = dbSlotsFixArea.reverseMarkPaid(companyId, orderId);
        if (rows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FIX_AREA_SLOT_NOT_FOUND", "No repair ticket linked to this order");
        }
        log.info("Reversed repair payment for company {} orderId {}", companyId, orderId);
        return "Repair ticket restored successfully";
    }

    @Transactional
    public String closeSlot(int companyId, int faId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(faId, "faId");
        int rows = dbSlotsFixArea.closeSlot(companyId, faId);
        if (rows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FIX_AREA_SLOT_NOT_FOUND", "Repair ticket not found");
        }
        log.info("Closed fix-area slot {} for company {}", faId, companyId);
        return "Repair ticket closed and parts removed";
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }
}
