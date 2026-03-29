package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbSupplier;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Request.SupplierCreateRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierProductCreateRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierUpdateRequest;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.Model.SupplierBProduct;
import com.example.valueinsoftbackend.util.RequestTimestampParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.google.gson.JsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
@Slf4j
public class SupplierService {

    private final DbSupplier dbSupplier;

    public SupplierService(DbSupplier dbSupplier) {
        this.dbSupplier = dbSupplier;
    }

    public List<Supplier> getSuppliers(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        return dbSupplier.getSuppliers(branchId, companyId);
    }

    public JsonObject getRemainingAmountByProductId(int companyId, int branchId, int productId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(productId, "productId");
        return dbSupplier.getRemainingSupplierAmountByProductId(productId, branchId, companyId);
    }

    @Transactional
    public String createSupplier(SupplierCreateRequest request) {
        int rows = dbSupplier.addSupplier(
                normalize(request.getSupplierName()),
                normalize(request.getSupplierPhone1()),
                normalizeNullable(request.getSupplierPhone2()),
                normalize(request.getSuplierLocation()),
                normalize(request.getSuplierMajor()),
                request.getBranchId(),
                request.getCompanyId()
        );

        if (rows != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SUPPLIER_CREATE_FAILED", "Supplier could not be created");
        }

        log.info("Created supplier for company {} branch {}", request.getCompanyId(), request.getBranchId());
        return "the supplier added! ok 200";
    }

    @Transactional
    public String updateSupplier(int companyId, int branchId, int supplierId, SupplierUpdateRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");

        if (request.getSupplierId() != null && request.getSupplierId() > 0 && request.getSupplierId() != supplierId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_ID_MISMATCH", "supplierId in the body does not match the path");
        }

        int rows = dbSupplier.updateSupplier(
                supplierId,
                normalizeNullable(request.getSupplierName()),
                normalizeNullable(request.getSupplierPhone1()),
                normalizeNullable(request.getSupplierPhone2()),
                normalizeNullable(request.getSuplierLocation()),
                normalizeNullable(request.getSuplierMajor()),
                branchId,
                companyId
        );

        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "Supplier not found");
        }

        log.info("Updated supplier {} for company {} branch {}", supplierId, companyId, branchId);
        return "the supplier updates with (ok 200)";
    }

    @Transactional
    public boolean deleteSupplier(int companyId, int branchId, int supplierId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");

        int rows = dbSupplier.deleteSupplier(supplierId, branchId, companyId);
        log.info("Deleted supplier {} for company {} branch {}: {}", supplierId, companyId, branchId, rows == 1);
        return rows == 1;
    }

    public List<InventoryTransaction> getSupplierSales(int companyId, int branchId, int supplierId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");
        return dbSupplier.getSupplierSales(branchId, supplierId, companyId);
    }

    public List<SupplierBProduct> getSupplierBoughtProducts(int companyId, int branchId, int supplierId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");
        return dbSupplier.getSupplierBProduct(branchId, supplierId, companyId);
    }

    @Transactional
    public String createSupplierBoughtProduct(int companyId, int branchId, int productId, SupplierProductCreateRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(productId, "productId");

        Timestamp time = RequestTimestampParser.parse(request.getTime(), "time");
        SupplierBProduct supplierBProduct = new SupplierBProduct(
                0,
                productId,
                request.getQuantity(),
                request.getCost(),
                normalize(request.getUserName()),
                request.getsPaid(),
                time,
                normalizeNullable(request.getDesc()),
                request.getOrderDetailsId()
        );

        int rows = dbSupplier.addSupplierBProduct(supplierBProduct, productId, branchId, companyId);
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_PRODUCT_TARGET_NOT_FOUND", "Product not found for supplier product entry");
        }

        log.info("Created supplier bought-product row for company {} branch {} product {}", companyId, branchId, productId);
        return "the supplier BProduct added! ok 200";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }
}
