package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbApOpenItem;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsReadModels;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/suppliers")
public class SupplierOpenItemsController {

    private final DbApOpenItem dbApOpenItem;
    private final AuthorizationService authorizationService;

    public SupplierOpenItemsController(DbApOpenItem dbApOpenItem, AuthorizationService authorizationService) {
        this.dbApOpenItem = dbApOpenItem;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{companyId}/{branchId}/{supplierId}/open-items")
    public OpenItemsReadModels.OpenItemPage getOpenItems(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int supplierId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(defaultValue = "50") @Positive @Max(200) int limit,
            @RequestParam(defaultValue = "0") @PositiveOrZero int offset,
            Principal principal) {
        authorize(principal, companyId, branchId);
        return dbApOpenItem.findOpenItems(companyId, branchId, supplierId, status, dueBefore, limit, offset);
    }

    @GetMapping("/{companyId}/{branchId}/{supplierId}/open-items/statement")
    public OpenItemsReadModels.Statement getStatement(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int supplierId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            Principal principal) {
        authorize(principal, companyId, branchId);
        LocalDate resolvedTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate resolvedFrom = fromDate == null ? resolvedTo.minusDays(90) : fromDate;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPEN_ITEMS_DATE_RANGE_INVALID",
                    "fromDate must not be after toDate");
        }
        return dbApOpenItem.getStatement(companyId, branchId, supplierId, resolvedFrom, resolvedTo);
    }

    @GetMapping("/{companyId}/{branchId}/{supplierId}/open-items/aging")
    public OpenItemsReadModels.Aging getAging(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int supplierId,
            @RequestParam(required = false) LocalDate asOfDate,
            Principal principal) {
        authorize(principal, companyId, branchId);
        return dbApOpenItem.getAging(companyId, branchId, supplierId,
                asOfDate == null ? LocalDate.now() : asOfDate);
    }

    private void authorize(Principal principal, int companyId, int branchId) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, branchId, "suppliers.openitems.view");
    }
}
