package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbSupplier;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.SupplierCreateRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierUpdateRequest;
import com.example.valueinsoftbackend.Model.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierServiceTest {

    private DbSupplier dbSupplier;
    private SupplierService supplierService;

    @BeforeEach
    void setUp() {
        dbSupplier = Mockito.mock(DbSupplier.class);
        supplierService = new SupplierService(dbSupplier);
    }

    @Test
    void createSupplierRejectsDuplicateNormalizedName() {
        SupplierCreateRequest request = buildCreateRequest();
        when(dbSupplier.supplierNameExists(eq("main supplier"), eq(0), eq(1074), eq(1095))).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> supplierService.createSupplier(request));

        assertEquals("SUPPLIER_DUPLICATE_NAME", exception.getCode());
    }

    @Test
    void createSupplierRejectsDuplicateNormalizedPrimaryPhone() {
        SupplierCreateRequest request = buildCreateRequest();
        when(dbSupplier.supplierNameExists(eq("main supplier"), eq(0), eq(1074), eq(1095))).thenReturn(false);
        when(dbSupplier.supplierPrimaryPhoneExists(eq("+201001112222"), eq(0), eq(1074), eq(1095))).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> supplierService.createSupplier(request));

        assertEquals("SUPPLIER_DUPLICATE_PHONE", exception.getCode());
    }

    @Test
    void createSupplierTrimsAndPersistsCanonicalFields() {
        SupplierCreateRequest request = buildCreateRequest();
        when(dbSupplier.supplierNameExists(eq("main supplier"), eq(0), eq(1074), eq(1095))).thenReturn(false);
        when(dbSupplier.supplierPrimaryPhoneExists(eq("+201001112222"), eq(0), eq(1074), eq(1095))).thenReturn(false);
        when(dbSupplier.addSupplier(
                eq("Main   Supplier"),
                eq("+20 100-111-2222"),
                eq("01001112223"),
                eq("Cairo"),
                eq("Groceries"),
                eq(1074),
                eq(1095)
        )).thenReturn(1);

        String result = supplierService.createSupplier(request);

        assertEquals("the supplier added! ok 200", result);
    }

    @Test
    void updateSupplierPreservesExistingValuesForPartialPayload() {
        SupplierUpdateRequest request = new SupplierUpdateRequest();
        request.setSupplierName("Updated Supplier");

        Supplier existing = new Supplier(
                77,
                "Main Supplier",
                "01001112222",
                "01001112223",
                "Cairo",
                "Groceries",
                0,
                0
        );
        when(dbSupplier.getSuppliers(eq(1074), eq(1095))).thenReturn(List.of(existing));
        when(dbSupplier.supplierNameExists(eq("updated supplier"), eq(77), eq(1074), eq(1095))).thenReturn(false);
        when(dbSupplier.supplierPrimaryPhoneExists(eq("01001112222"), eq(77), eq(1074), eq(1095))).thenReturn(false);
        when(dbSupplier.updateSupplier(
                eq(77),
                eq("Updated Supplier"),
                eq("01001112222"),
                eq("01001112223"),
                eq("Cairo"),
                eq("Groceries"),
                eq(1074),
                eq(1095)
        )).thenReturn(1);

        String result = supplierService.updateSupplier(1095, 1074, 77, request);

        assertEquals("the supplier updates with (ok 200)", result);
        verify(dbSupplier).updateSupplier(
                eq(77),
                eq("Updated Supplier"),
                eq("01001112222"),
                eq("01001112223"),
                eq("Cairo"),
                eq("Groceries"),
                eq(1074),
                eq(1095)
        );
    }

    private SupplierCreateRequest buildCreateRequest() {
        SupplierCreateRequest request = new SupplierCreateRequest();
        request.setSupplierName("  Main   Supplier  ");
        request.setSupplierPhone1(" +20 100-111-2222 ");
        request.setSupplierPhone2("01001112223");
        request.setSupplierLocation(" Cairo ");
        request.setSupplierMajor(" Groceries ");
        request.setBranchId(1074);
        request.setCompanyId(1095);
        return request;
    }
}
