package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbSupplier;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Request.SupplierArchiveRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierCreateRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierProductCreateRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierUpdateRequest;
import com.example.valueinsoftbackend.Model.Response.SupplierAgingResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierAuditResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierReferenceResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierStatementResponse;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.Model.SupplierBProduct;
import com.example.valueinsoftbackend.util.RequestTimestampParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.google.gson.JsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

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
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        String supplierName = normalizeRequired(request.getSupplierName(), "SUPPLIER_NAME_REQUIRED", "supplierName is required");
        String supplierPhone1 = normalizeRequired(request.getSupplierPhone1(), "SUPPLIER_PHONE_REQUIRED", "supplierPhone1 is required");
        String supplierLocation = normalizeNullable(request.getSuplierLocation());
        String supplierMajor = normalizeNullable(request.getSuplierMajor());
        if (supplierLocation == null || supplierLocation.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_LOCATION_REQUIRED", "supplierLocation is required");
        }
        if (supplierMajor == null || supplierMajor.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_MAJOR_REQUIRED", "supplierMajor is required");
        }
        validateSupplierUniqueness(request.getCompanyId(), request.getBranchId(), 0, supplierName, supplierPhone1);

        int rows = dbSupplier.addSupplier(
                supplierName,
                supplierPhone1,
                normalizeNullable(request.getSupplierPhone2()),
                supplierLocation,
                supplierMajor,
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

        Supplier existing = getSupplier(companyId, branchId, supplierId);
        String supplierName = normalizeRequired(
                firstNonBlank(request.getSupplierName(), existing.getSupplierName()),
                "SUPPLIER_NAME_REQUIRED",
                "supplierName is required");
        String supplierPhone1 = normalizeRequired(
                firstNonBlank(request.getSupplierPhone1(), existing.getSupplierPhone1()),
                "SUPPLIER_PHONE_REQUIRED",
                "supplierPhone1 is required");
        String supplierPhone2 = normalizeNullable(firstNonBlank(request.getSupplierPhone2(), existing.getSupplierPhone2()));
        String supplierLocation = normalizeRequired(
                firstNonBlank(request.getSuplierLocation(), existing.getSupplierLocation()),
                "SUPPLIER_LOCATION_REQUIRED",
                "supplierLocation is required");
        String supplierMajor = normalizeRequired(
                firstNonBlank(request.getSuplierMajor(), existing.getSupplierMajor()),
                "SUPPLIER_MAJOR_REQUIRED",
                "supplierMajor is required");
        validateSupplierUniqueness(companyId, branchId, supplierId, supplierName, supplierPhone1);

        int rows = dbSupplier.updateSupplier(
                supplierId,
                supplierName,
                supplierPhone1,
                supplierPhone2,
                supplierLocation,
                supplierMajor,
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

        SupplierReferenceResponse references = getSupplierReferences(companyId, branchId, supplierId);
        if (!references.isCanDelete()) {
            throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_HAS_REFERENCES", "Supplier cannot be deleted because referenced records or open balance exist");
        }

        int rows = dbSupplier.deleteSupplier(supplierId, branchId, companyId);
        log.info("Deleted supplier {} for company {} branch {}: {}", supplierId, companyId, branchId, rows == 1);
        return rows == 1;
    }

    public SupplierReferenceResponse getSupplierReferences(int companyId, int branchId, int supplierId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");
        requireSupplierExists(companyId, branchId, supplierId);
        return dbSupplier.getSupplierReferences(supplierId, branchId, companyId);
    }

    @Transactional
    public Supplier archiveSupplier(int companyId, int branchId, int supplierId, SupplierArchiveRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");
        requireSupplierExists(companyId, branchId, supplierId);

        String reason = request == null ? "" : normalizeNullable(request.getReason());
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_ARCHIVE_REASON_REQUIRED", "Archive reason is required");
        }

        int rows = dbSupplier.archiveSupplier(supplierId, branchId, companyId, reason, null);
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_ALREADY_ARCHIVED", "Supplier is already archived");
        }

        log.info("Archived supplier {} for company {} branch {}", supplierId, companyId, branchId);
        return getSupplier(companyId, branchId, supplierId);
    }

    @Transactional
    public Supplier reactivateSupplier(int companyId, int branchId, int supplierId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");
        requireSupplierExists(companyId, branchId, supplierId);

        int rows = dbSupplier.reactivateSupplier(supplierId, branchId, companyId);
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_NOT_ARCHIVED", "Only archived suppliers can be reactivated");
        }

        log.info("Reactivated supplier {} for company {} branch {}", supplierId, companyId, branchId);
        return getSupplier(companyId, branchId, supplierId);
    }

    public Supplier getSupplier(int companyId, int branchId, int supplierId) {
        return getSuppliers(companyId, branchId).stream()
                .filter(supplier -> supplier.getSupplierId() == supplierId)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "Supplier not found"));
    }

    public SupplierStatementResponse getSupplierStatement(int companyId,
                                                          int branchId,
                                                          int supplierId,
                                                          LocalDate fromDate,
                                                          LocalDate toDate) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");
        LocalDate normalizedFromDate = fromDate == null ? LocalDate.now().withDayOfMonth(1) : fromDate;
        LocalDate normalizedToDate = toDate == null ? LocalDate.now() : toDate;
        if (normalizedToDate.isBefore(normalizedFromDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_STATEMENT_DATE_RANGE_INVALID", "toDate must be on or after fromDate");
        }
        requireSupplierExists(companyId, branchId, supplierId);
        return dbSupplier.getSupplierStatement(supplierId, branchId, companyId, normalizedFromDate, normalizedToDate);
    }

    public SupplierAgingResponse getSupplierAging(int companyId,
                                                  int branchId,
                                                  int supplierId,
                                                  LocalDate asOfDate) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");
        LocalDate normalizedAsOfDate = asOfDate == null ? LocalDate.now() : asOfDate;
        requireSupplierExists(companyId, branchId, supplierId);
        return dbSupplier.getSupplierAging(supplierId, branchId, companyId, normalizedAsOfDate);
    }

    public SupplierAuditResponse getSupplierAudit(int companyId,
                                                  int branchId,
                                                  int supplierId,
                                                  LocalDate fromDate,
                                                  LocalDate toDate,
                                                  Integer page,
                                                  Integer size) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");
        LocalDate normalizedFromDate = fromDate == null ? LocalDate.now().minusDays(30) : fromDate;
        LocalDate normalizedToDate = toDate == null ? LocalDate.now() : toDate;
        if (normalizedToDate.isBefore(normalizedFromDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_AUDIT_DATE_RANGE_INVALID", "toDate must be on or after fromDate");
        }
        int normalizedPage = page == null ? 0 : page;
        int normalizedSize = size == null ? 50 : Math.min(size, 200);
        if (normalizedPage < 0 || normalizedSize <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_AUDIT_PAGE_INVALID", "page must be zero or greater and size must be positive");
        }
        requireSupplierExists(companyId, branchId, supplierId);
        return dbSupplier.getSupplierAudit(
                supplierId,
                branchId,
                companyId,
                normalizedFromDate,
                normalizedToDate,
                normalizedPage,
                normalizedSize);
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

    private String normalizeRequired(String value, String code, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null || normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return normalized;
    }

    private String firstNonBlank(String preferred, String fallback) {
        String normalizedPreferred = normalizeNullable(preferred);
        if (normalizedPreferred != null && !normalizedPreferred.isBlank()) {
            return normalizedPreferred;
        }
        return fallback;
    }

    private void validateSupplierUniqueness(int companyId, int branchId, int excludedSupplierId, String supplierName, String supplierPhone1) {
        String normalizedName = normalizeSupplierNameKey(supplierName);
        if (dbSupplier.supplierNameExists(normalizedName, excludedSupplierId, branchId, companyId)) {
            throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_DUPLICATE_NAME", "Supplier name already exists in this branch");
        }

        String normalizedPhone = normalizeSupplierPhoneKey(supplierPhone1);
        if (!normalizedPhone.isBlank() && dbSupplier.supplierPrimaryPhoneExists(normalizedPhone, excludedSupplierId, branchId, companyId)) {
            throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_DUPLICATE_PHONE", "Supplier primary phone already exists in this branch");
        }
    }

    private String normalizeSupplierNameKey(String value) {
        return normalize(value).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeSupplierPhoneKey(String value) {
        return normalize(value).replaceAll("[^0-9+]", "");
    }

    private void requireSupplierExists(int companyId, int branchId, int supplierId) {
        if (!dbSupplier.supplierExists(supplierId, branchId, companyId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "Supplier not found");
        }
    }
}
