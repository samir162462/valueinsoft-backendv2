package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbArOpenItem;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/clientAccount")
public class ClientAccountController {

    private final DbArOpenItem dbArOpenItem;
    private final AuthorizationService authorizationService;

    public ClientAccountController(DbArOpenItem dbArOpenItem, AuthorizationService authorizationService) {
        this.dbArOpenItem = dbArOpenItem;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{companyId}/{clientId}/open-items")
    public OpenItemsReadModels.OpenItemPage getOpenItems(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int clientId,
            @RequestParam @Positive int branchId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(defaultValue = "50") @Positive @Max(200) int limit,
            @RequestParam(defaultValue = "0") @PositiveOrZero int offset,
            Principal principal) {
        authorize(principal, companyId, branchId, "clients.openitems.view");
        return dbArOpenItem.findOpenItems(companyId, branchId, clientId, status, dueBefore, limit, offset);
    }

    @GetMapping("/{companyId}/{clientId}/statement")
    public OpenItemsReadModels.Statement getStatement(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int clientId,
            @RequestParam @Positive int branchId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            Principal principal) {
        authorize(principal, companyId, branchId, "clients.account.statement.view");
        LocalDate resolvedTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate resolvedFrom = fromDate == null ? resolvedTo.minusDays(90) : fromDate;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPEN_ITEMS_DATE_RANGE_INVALID",
                    "fromDate must not be after toDate");
        }
        return dbArOpenItem.getStatement(companyId, branchId, clientId, resolvedFrom, resolvedTo);
    }

    @GetMapping("/{companyId}/{clientId}/aging")
    public OpenItemsReadModels.Aging getAging(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int clientId,
            @RequestParam @Positive int branchId,
            @RequestParam(required = false) LocalDate asOfDate,
            Principal principal) {
        authorize(principal, companyId, branchId, "clients.openitems.view");
        return dbArOpenItem.getAging(companyId, branchId, clientId,
                asOfDate == null ? LocalDate.now() : asOfDate);
    }

    @GetMapping("/{companyId}/{clientId}/credit")
    public OpenItemsReadModels.ClientCredit getCredit(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int clientId,
            @RequestParam @Positive int branchId,
            Principal principal) {
        authorize(principal, companyId, branchId, "clients.credit.view");
        return dbArOpenItem.getCredit(companyId, branchId, clientId);
    }

    @PutMapping("/{companyId}/{clientId}/credit")
    public OpenItemsReadModels.ClientCredit updateCredit(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int clientId,
            @RequestParam @Positive int branchId,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels.CreditUpdateCommand command,
            Principal principal) {
        authorize(principal, companyId, branchId, "clients.credit.manage");
        int rows = dbArOpenItem.updateClientCredit(companyId, clientId, command);
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND",
                    "Client was not found for this tenant");
        }
        return dbArOpenItem.getCredit(companyId, branchId, clientId);
    }

    private void authorize(Principal principal, int companyId, int branchId, String capability) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, branchId, capability);
    }
}
