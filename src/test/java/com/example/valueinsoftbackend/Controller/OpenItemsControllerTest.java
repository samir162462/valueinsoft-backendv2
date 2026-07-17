package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbApOpenItem;
import com.example.valueinsoftbackend.DatabaseRequests.DbArOpenItem;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsReadModels;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenItemsControllerTest {

    private static final Principal PRINCIPAL = () -> "accountant@example.com";

    @Test
    void clientOpenItemsAuthorizeWithExplicitBranchAndApplyPagination() {
        DbArOpenItem repository = mock(DbArOpenItem.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        ClientAccountController controller = new ClientAccountController(repository, authorization);
        OpenItemsReadModels.OpenItemPage expected = new OpenItemsReadModels.OpenItemPage(List.of(), 50, 10, 0);
        when(repository.findOpenItems(7, 3, 11, "OPEN", null, 50, 10)).thenReturn(expected);

        assertEquals(expected, controller.getOpenItems(7, 11, 3, "OPEN", null, 50, 10, PRINCIPAL));

        verify(authorization).assertAuthenticatedCapability(
                PRINCIPAL.getName(), 7, 3, "clients.openitems.view");
        verify(repository).findOpenItems(7, 3, 11, "OPEN", null, 50, 10);
    }

    @Test
    void foreignTenantDenialStopsClientReadBeforeRepositoryAccess() {
        DbArOpenItem repository = mock(DbArOpenItem.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        ClientAccountController controller = new ClientAccountController(repository, authorization);
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "Tenant access denied"))
                .when(authorization).assertAuthenticatedCapability(
                        PRINCIPAL.getName(), 99, 3, "clients.credit.view");

        ApiException exception = assertThrows(ApiException.class,
                () -> controller.getCredit(99, 11, 3, PRINCIPAL));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("TENANT_ACCESS_DENIED", exception.getCode());
        verifyNoInteractions(repository);
    }

    @Test
    void supplierOpenItemsUseBranchScopedCapability() {
        DbApOpenItem repository = mock(DbApOpenItem.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        SupplierOpenItemsController controller = new SupplierOpenItemsController(repository, authorization);
        LocalDate dueBefore = LocalDate.of(2026, 7, 12);
        OpenItemsReadModels.OpenItemPage expected = new OpenItemsReadModels.OpenItemPage(List.of(), 25, 0, 0);
        when(repository.findOpenItems(7, 3, 12, null, dueBefore, 25, 0)).thenReturn(expected);

        assertEquals(expected, controller.getOpenItems(7, 3, 12, null, dueBefore, 25, 0, PRINCIPAL));

        verify(authorization).assertAuthenticatedCapability(
                PRINCIPAL.getName(), 7, 3, "suppliers.openitems.view");
        verify(repository).findOpenItems(7, 3, 12, null, dueBefore, 25, 0);
    }

    @Test
    void clientStatementDefaultsToNinetyDays() {
        DbArOpenItem repository = mock(DbArOpenItem.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        ClientAccountController controller = new ClientAccountController(repository, authorization);
        LocalDate to = LocalDate.of(2026, 7, 12);
        OpenItemsReadModels.Statement expected = new OpenItemsReadModels.Statement(
                11, 3, to.minusDays(90), to, java.util.Map.of(), List.of(), java.util.Map.of());
        when(repository.getStatement(7, 3, 11, to.minusDays(90), to)).thenReturn(expected);

        assertEquals(expected, controller.getStatement(7, 11, 3, null, to, PRINCIPAL));
        verify(repository).getStatement(7, 3, 11, to.minusDays(90), to);
    }

    @Test
    void supplierStatementDefaultsToNinetyDays() {
        DbApOpenItem repository = mock(DbApOpenItem.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        SupplierOpenItemsController controller = new SupplierOpenItemsController(repository, authorization);
        LocalDate to = LocalDate.of(2026, 7, 12);
        OpenItemsReadModels.Statement expected = new OpenItemsReadModels.Statement(
                12, 3, to.minusDays(90), to, java.util.Map.of(), List.of(), java.util.Map.of());
        when(repository.getStatement(7, 3, 12, to.minusDays(90), to)).thenReturn(expected);

        assertEquals(expected, controller.getStatement(7, 3, 12, null, to, PRINCIPAL));
        verify(repository).getStatement(7, 3, 12, to.minusDays(90), to);
    }
}
