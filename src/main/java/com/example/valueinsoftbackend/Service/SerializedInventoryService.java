package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductTrackingRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductUnitRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryStockMovementRepository;
import com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace.DbInventoryWorkspaceCommandGateway;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Inventory.InventoryMovementType;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.Model.Inventory.ProductTrackingMetadata;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnit;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnitStatus;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.Request.Inventory.ProductTrackingTypeChangeRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitInput;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitStockInRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitTransferRequest;
import com.example.valueinsoftbackend.Model.ResponseModel.Inventory.SerializedUnitScanResponse;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SerializedInventoryService {

    private static final BigDecimal UNIT_IN = BigDecimal.ONE;
    private static final BigDecimal UNIT_OUT = BigDecimal.ONE.negate();

    private final DbInventoryProductTrackingRepository productTrackingRepository;
    private final DbInventoryProductUnitRepository productUnitRepository;
    private final DbInventoryStockMovementRepository stockMovementRepository;
    private final FinanceOperationalPostingService financeOperationalPostingService;
    private final DbInventoryWorkspaceCommandGateway workspaceCommandGateway;

    @Autowired
    public SerializedInventoryService(DbInventoryProductUnitRepository productUnitRepository,
                                      DbInventoryProductTrackingRepository productTrackingRepository,
                                      DbInventoryStockMovementRepository stockMovementRepository,
                                      FinanceOperationalPostingService financeOperationalPostingService,
                                      DbInventoryWorkspaceCommandGateway workspaceCommandGateway) {
        this.productUnitRepository = productUnitRepository;
        this.productTrackingRepository = productTrackingRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.financeOperationalPostingService = financeOperationalPostingService;
        this.workspaceCommandGateway = workspaceCommandGateway;
    }

    @Transactional
    public ProductTrackingMetadata updateProductTrackingType(ProductTrackingTypeChangeRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is required");
        }
        long companyId = requirePositive(request.getCompanyId(), "companyId");
        long branchId = requirePositive(request.getBranchId(), "branchId");
        long productId = requirePositive(request.getProductId(), "productId");
        TrackingType targetTrackingType = requireSupportedTrackingType(request.getTrackingType());

        ProductTrackingMetadata existing = productTrackingRepository.findTrackingMetadata(companyId, productId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PRODUCT_NOT_FOUND",
                        "Product was not found for this company"
                ));

        validateTrackingTypeChange(companyId, branchId, productId, existing.getTrackingType(), targetTrackingType);

        int updated = productTrackingRepository.updateTrackingMetadata(
                companyId,
                productId,
                targetTrackingType,
                request.getSku(),
                request.getBarcode()
        );
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_TRACKING_UPDATE_FAILED", "Product tracking type was not updated");
        }

        return productTrackingRepository.findTrackingMetadata(companyId, productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product was not found after update"));
    }

    public SerializedUnitScanResponse scanUnit(long companyId, long branchId, String scanCode) {
        requirePositive(companyId, "companyId");
        requirePositive(branchId, "branchId");
        String normalizedScanCode = requireNonBlank(scanCode, "scanCode");

        return productUnitRepository.findByScanCode(companyId, branchId, normalizedScanCode)
                .map(unit -> new SerializedUnitScanResponse(
                        true,
                        unit.getStatus() == ProductUnitStatus.AVAILABLE,
                        unit.getStatus() == ProductUnitStatus.AVAILABLE ? "OK" : "UNIT_NOT_AVAILABLE",
                        unit.getStatus() == ProductUnitStatus.AVAILABLE ? "SERIALIZED_UNIT_AVAILABLE" : "SERIALIZED_UNIT_NOT_AVAILABLE",
                        unit.getStatus() == ProductUnitStatus.AVAILABLE ? "Unit is available" : "Unit is not available for sale",
                        unit.getProductUnitId(),
                        unit.getCompanyId(),
                        unit.getBranchId(),
                        unit.getProductId(),
                        null,
                        unit.getTrackingType(),
                        unit.getUnitIdentifier(),
                        unit.getImei(),
                        unit.getSerialNumber(),
                        unit.getStatus(),
                        unit.getConditionCode()
                ))
                .orElseGet(() -> new SerializedUnitScanResponse(
                        false,
                        false,
                        "NOT_FOUND",
                        "SERIALIZED_UNIT_NOT_FOUND",
                        "Unit was not found in this branch",
                        null,
                        companyId,
                        branchId,
                        null,
                        null,
                        null,
                        normalizedScanCode,
                        null,
                        null,
                        null,
                        null
                ));
    }

    @Transactional
    public List<Long> stockInSerializedUnits(SerializedUnitStockInRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is required");
        }
        long companyId = requirePositive(request.getCompanyId(), "companyId");
        long branchId = requirePositive(request.getBranchId(), "branchId");
        long productId = requirePositive(request.getProductId(), "productId");
        TrackingType trackingType = requireSerializedTrackingType(request.getTrackingType());

        ProductTrackingMetadata productMetadata = productTrackingRepository.findTrackingMetadata(companyId, productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product was not found for this company"));

        if (TrackingType.defaultIfNull(productMetadata.getTrackingType()) != trackingType) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PRODUCT_TRACKING_TYPE_MISMATCH",
                    "Product tracking type must match the serialized units being received"
            );
        }

        List<SerializedUnitInput> units = request.getUnits();
        if (units == null || units.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERIALIZED_STOCK_IN_EMPTY", "At least one serialized unit is required");
        }

        Set<String> requestIdentifiers = new HashSet<>();
        List<ProductUnit> productUnits = new ArrayList<>();
        for (SerializedUnitInput input : units) {
            ProductUnit productUnit = buildProductUnit(companyId, branchId, productId, trackingType, input, request);
            String uniquenessKey = productUnit.getUnitIdentifier().toLowerCase(Locale.ROOT);
            if (!requestIdentifiers.add(uniquenessKey)) {
                throw new ApiException(HttpStatus.CONFLICT, "SERIALIZED_UNIT_DUPLICATE_IN_REQUEST", "Duplicate unit identifier in stock-in request");
            }
            productUnits.add(productUnit);
        }

        List<Long> insertedUnitIds = new ArrayList<>();
        for (ProductUnit productUnit : productUnits) {
            long productUnitId = stockInOrReactivateProductUnit(productUnit);

            insertedUnitIds.add(productUnitId);
            InventoryStockMovement stockInMovement = buildMovement(
                    companyId,
                    branchId,
                    productId,
                    productUnitId,
                    InventoryMovementType.STOCK_IN,
                    UNIT_IN,
                    request.getPurchaseReferenceType(),
                    request.getPurchaseReferenceId(),
                    request.getPurchaseLineId(),
                    request.getSupplierId(),
                    null,
                    request.getActorName(),
                    buildUnitIdempotencyKey(request.getIdempotencyKey(), productUnit.getUnitIdentifier())
            );
            stockMovementRepository.insertMovement(stockInMovement);
            stockMovementRepository.insertHistoryLedgerMovement(stockInMovement, 0, "Add", 0);
        }
        return insertedUnitIds;
    }

    private long stockInOrReactivateProductUnit(ProductUnit productUnit) {
        try {
            return productUnitRepository.insertProductUnit(productUnit);
        } catch (DuplicateKeyException exception) {
            ProductUnit existingUnit = productUnitRepository.findByCompanyScanCode(
                            productUnit.getCompanyId(),
                            productUnit.getUnitIdentifier()
                    )
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT,
                            "SERIALIZED_UNIT_DUPLICATE",
                            "This IMEI or serial already exists for the company"
                    ));

            validateExistingUnitCanBeReceivedAgain(existingUnit, productUnit);
            productUnit.setProductUnitId(existingUnit.getProductUnitId());

            int updated = productUnitRepository.reactivateForStockIn(productUnit, existingUnit.getStatus());
            if (updated != 1) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "SERIALIZED_UNIT_RESTOCK_CONFLICT",
                        "This IMEI or serial could not be received again because its status changed"
                );
            }
            return existingUnit.getProductUnitId();
        }
    }

    private void validateExistingUnitCanBeReceivedAgain(ProductUnit existingUnit, ProductUnit incomingUnit) {
        if (existingUnit.getProductId() != incomingUnit.getProductId()
                || TrackingType.defaultIfNull(existingUnit.getTrackingType()) != TrackingType.defaultIfNull(incomingUnit.getTrackingType())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SERIALIZED_UNIT_PRODUCT_MISMATCH",
                    "This IMEI or serial already belongs to another product"
            );
        }

        if (existingUnit.getStatus() == ProductUnitStatus.SOLD
                || existingUnit.getStatus() == ProductUnitStatus.RETURNED
                || existingUnit.getStatus() == ProductUnitStatus.DAMAGED
                || existingUnit.getStatus() == ProductUnitStatus.UNDER_REPAIR) {
            return;
        }

        throw new ApiException(
                HttpStatus.CONFLICT,
                "SERIALIZED_UNIT_ACTIVE_DUPLICATE",
                "This IMEI or serial already exists as an active inventory unit"
        );
    }

    public ProductUnit requireAvailableUnitForSale(long companyId, long branchId, long productId, long productUnitId) {
        return productUnitRepository.findAvailableForSaleForUpdate(
                        requirePositive(companyId, "companyId"),
                        requirePositive(branchId, "branchId"),
                        requirePositive(productId, "productId"),
                        requirePositive(productUnitId, "productUnitId")
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "SERIALIZED_UNIT_NOT_AVAILABLE",
                        "Unit is not available for sale in this branch"
                ));
    }

    @Transactional
    public void markSerializedUnitSold(long companyId,
                                       long branchId,
                                       long productId,
                                       long productUnitId,
                                       long orderId,
                                       Long orderDetailId,
                                       Long customerId,
                                       String actorName) {
        ProductUnit unit = requireAvailableUnitForSale(companyId, branchId, productId, productUnitId);

        int updated = productUnitRepository.markSold(companyId, branchId, productId, productUnitId, orderId, orderDetailId, customerId);
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SERIALIZED_UNIT_SALE_CONFLICT", "Unit was already sold or moved");
        }

        stockMovementRepository.insertMovement(buildMovement(
                companyId,
                branchId,
                productId,
                unit.getProductUnitId(),
                InventoryMovementType.SALE,
                UNIT_OUT,
                "ORDER",
                String.valueOf(orderId),
                orderDetailId,
                null,
                customerId,
                actorName,
                null
        ));
    }

    @Transactional
    public void markSerializedUnitReturned(long companyId,
                                           long branchId,
                                           long productId,
                                           long productUnitId,
                                           ProductUnitStatus targetStatus,
                                           Long customerId,
                                           String referenceId,
                                           String actorName) {
        ProductUnitStatus safeTargetStatus = targetStatus == null ? ProductUnitStatus.RETURNED : targetStatus;
        if (!(safeTargetStatus == ProductUnitStatus.RETURNED
                || safeTargetStatus == ProductUnitStatus.AVAILABLE
                || safeTargetStatus == ProductUnitStatus.DAMAGED
                || safeTargetStatus == ProductUnitStatus.UNDER_REPAIR)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERIALIZED_RETURN_STATUS_INVALID", "Invalid return status for serialized unit");
        }

        ProductUnit unit = productUnitRepository.findById(companyId, productUnitId)
                .filter(found -> found.getBranchId() == branchId)
                .filter(found -> found.getProductId() == productId)
                .filter(found -> found.getStatus() == ProductUnitStatus.SOLD)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "SERIALIZED_RETURN_CONFLICT",
                        "Only sold units for this product and branch can be returned by this flow"
                ));

        int updated = productUnitRepository.updateStatus(
                companyId,
                branchId,
                unit.getProductUnitId(),
                ProductUnitStatus.SOLD,
                safeTargetStatus,
                null
        );
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SERIALIZED_RETURN_CONFLICT", "Only sold units can be returned by this flow");
        }

        stockMovementRepository.insertMovement(buildMovement(
                companyId,
                branchId,
                productId,
                unit.getProductUnitId(),
                InventoryMovementType.RETURN,
                safeTargetStatus == ProductUnitStatus.AVAILABLE ? UNIT_IN : BigDecimal.ZERO,
                "RETURN",
                referenceId,
                null,
                null,
                customerId,
                actorName,
                null
        ));
    }

    @Transactional
    public ProductUnit updateSerializedUnitIdentifier(long companyId,
                                                      long branchId,
                                                      long productId,
                                                      long productUnitId,
                                                      String imeiInput,
                                                      String serialNumberInput,
                                                      String conditionCodeInput) {
        long safeCompanyId = requirePositive(companyId, "companyId");
        long safeBranchId = requirePositive(branchId, "branchId");
        long safeProductId = requirePositive(productId, "productId");
        long safeProductUnitId = requirePositive(productUnitId, "productUnitId");

        ProductUnit unit = productUnitRepository.findById(safeCompanyId, safeProductUnitId)
                .filter(found -> found.getBranchId() == safeBranchId)
                .filter(found -> found.getProductId() == safeProductId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SERIALIZED_UNIT_NOT_FOUND",
                        "Unit was not found for this product and branch"
                ));

        if (unit.getStatus() != ProductUnitStatus.AVAILABLE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SERIALIZED_UNIT_NOT_EDITABLE",
                    "Only available units can have their IMEI/serial corrected"
            );
        }

        TrackingType trackingType = requireSerializedTrackingType(unit.getTrackingType());
        String imei;
        String serialNumber;
        if (trackingType == TrackingType.IMEI) {
            imei = normalizeImei(imeiInput);
            serialNumber = blankToNull(serialNumberInput);
        } else {
            serialNumber = requireNonBlank(serialNumberInput, "serialNumber");
            imei = blankToNull(imeiInput);
        }
        String unitIdentifier = trackingType == TrackingType.IMEI ? imei : serialNumber;
        String conditionCode = blankToNull(conditionCodeInput);
        if (conditionCode != null
                && unit.getConditionCode() != null
                && !conditionCode.equalsIgnoreCase(unit.getConditionCode())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CONDITION_CHANGE_REQUIRES_CORRECTION",
                    "Changing a posted unit's condition requires the auditable condition correction flow"
            );
        }

        int updated;
        try {
            updated = productUnitRepository.updateUnitIdentifier(
                    safeCompanyId,
                    safeBranchId,
                    safeProductId,
                    safeProductUnitId,
                    imei,
                    serialNumber,
                    unitIdentifier,
                    conditionCode
            );
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SERIALIZED_UNIT_DUPLICATE", "This IMEI or serial already exists for the company");
        }

        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SERIALIZED_UNIT_UPDATE_CONFLICT", "Unit could not be updated because its status changed");
        }

        return productUnitRepository.findById(safeCompanyId, safeProductUnitId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERIALIZED_UNIT_NOT_FOUND", "Unit was not found after update"));
    }

    /**
     * Auditable correction flow for a posted unit's condition (NEW <-> USED).
     * A reason is mandatory and every change is written to
     * inventory_unit_condition_audit before the unit is updated.
     */
    @Transactional
    public ProductUnit correctSerializedUnitCondition(long companyId,
                                                      long branchId,
                                                      long productUnitId,
                                                      String newConditionCode,
                                                      String reason,
                                                      String actorName) {
        long safeCompanyId = requirePositive(companyId, "companyId");
        long safeBranchId = requirePositive(branchId, "branchId");
        long safeProductUnitId = requirePositive(productUnitId, "productUnitId");
        String safeReason = requireNonBlank(reason, "reason");
        String normalizedCondition = requireNonBlank(newConditionCode, "conditionCode").toUpperCase(Locale.ROOT);
        if (!"NEW".equals(normalizedCondition) && !"USED".equals(normalizedCondition)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONDITION_CODE_INVALID", "conditionCode must be NEW or USED");
        }

        ProductUnit unit = productUnitRepository.findById(safeCompanyId, safeProductUnitId)
                .filter(found -> found.getBranchId() == safeBranchId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SERIALIZED_UNIT_NOT_FOUND",
                        "Unit was not found for this branch"
                ));

        if (normalizedCondition.equalsIgnoreCase(unit.getConditionCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONDITION_UNCHANGED", "Unit already has this condition");
        }

        productUnitRepository.insertConditionAudit(
                safeCompanyId,
                safeBranchId,
                safeProductUnitId,
                null,
                unit.getConditionCode(),
                normalizedCondition,
                safeReason,
                actorName);
        int updated = productUnitRepository.updateUnitCondition(safeCompanyId, safeProductUnitId, normalizedCondition);
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SERIALIZED_UNIT_UPDATE_CONFLICT", "Unit condition could not be updated");
        }

        return productUnitRepository.findById(safeCompanyId, safeProductUnitId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERIALIZED_UNIT_NOT_FOUND", "Unit was not found after update"));
    }

    public long countAvailableSerializedUnits(long companyId, long branchId, long productId) {
        return productUnitRepository.countAvailableByProduct(
                requirePositive(companyId, "companyId"),
                requirePositive(branchId, "branchId"),
                requirePositive(productId, "productId")
        );
    }

    public ProductTrackingMetadata getProductTrackingMetadata(long companyId, long productId) {
        return productTrackingRepository.findTrackingMetadata(
                        requirePositive(companyId, "companyId"),
                        requirePositive(productId, "productId")
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PRODUCT_NOT_FOUND",
                        "Product was not found for this company"
                ));
    }

    public List<ProductUnit> listProductUnits(long companyId,
                                              long branchId,
                                              long productId,
                                              ProductUnitStatus status) {
        return productUnitRepository.listByProductBranchAndStatus(
                requirePositive(companyId, "companyId"),
                requirePositive(branchId, "branchId"),
                requirePositive(productId, "productId"),
                status
        );
    }

    public List<InventoryStockMovement> listProductMovementHistory(long companyId,
                                                                   long branchId,
                                                                   long productId,
                                                                   int limit) {
        return stockMovementRepository.findByProduct(
                requirePositive(companyId, "companyId"),
                requirePositive(branchId, "branchId"),
                requirePositive(productId, "productId"),
                normalizeLimit(limit)
        );
    }

    public List<InventoryStockMovement> listProductUnitMovementHistory(long companyId,
                                                                       long branchId,
                                                                       long productUnitId,
                                                                       int limit) {
        long safeCompanyId = requirePositive(companyId, "companyId");
        long safeBranchId = requirePositive(branchId, "branchId");
        long safeProductUnitId = requirePositive(productUnitId, "productUnitId");

        productUnitRepository.findById(safeCompanyId, safeProductUnitId)
                .filter(unit -> unit.getBranchId() == safeBranchId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SERIALIZED_UNIT_NOT_FOUND",
                        "Unit was not found in this branch"
                ));

        return stockMovementRepository.findByProductUnit(
                safeCompanyId,
                safeProductUnitId,
                normalizeLimit(limit)
        );
    }

    @Transactional
    public void transferSerializedUnits(SerializedUnitTransferRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is required");
        }
        long companyId = requirePositive(request.getCompanyId(), "companyId");
        long fromBranchId = requirePositive(request.getFromBranchId(), "fromBranchId");
        long toBranchId = requirePositive(request.getToBranchId(), "toBranchId");
        long productId = requirePositive(request.getProductId(), "productId");
        if (fromBranchId == toBranchId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERIALIZED_TRANSFER_BRANCH_CONFLICT", "Source and destination branch must be different");
        }
        if (request.getProductUnitIds() == null || request.getProductUnitIds().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERIALIZED_TRANSFER_EMPTY", "At least one serialized unit is required");
        }

        Set<Long> uniqueUnitIds = new HashSet<>(request.getProductUnitIds());
        if (uniqueUnitIds.size() != request.getProductUnitIds().size()) {
            throw new ApiException(HttpStatus.CONFLICT, "SERIALIZED_UNIT_DUPLICATE_IN_TRANSFER", "The same serialized unit appears more than once in the transfer");
        }

        // Idempotent assignment on destination branch
        workspaceCommandGateway.assignProductToBranch(
                request.getActorName(),
                (int) companyId,
                (int) toBranchId,
                productId,
                null
        );

        for (Long productUnitId : request.getProductUnitIds()) {
            ProductUnit unit = requireAvailableUnitForSale(companyId, fromBranchId, productId, productUnitId);
            int updated = productUnitRepository.updateStatus(
                    companyId,
                    fromBranchId,
                    unit.getProductUnitId(),
                    ProductUnitStatus.AVAILABLE,
                    ProductUnitStatus.AVAILABLE,
                    toBranchId
            );
            if (updated != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "SERIALIZED_TRANSFER_CONFLICT", "Serialized unit could not be transferred because its status changed");
            }

            String idempotencyBase = buildUnitIdempotencyKey(request.getIdempotencyKey(), unit.getUnitIdentifier());
            stockMovementRepository.insertMovement(buildTransferMovement(
                    companyId,
                    fromBranchId,
                    productId,
                    unit.getProductUnitId(),
                    InventoryMovementType.TRANSFER_OUT,
                    UNIT_OUT,
                    fromBranchId,
                    toBranchId,
                    request.getActorName(),
                    appendSuffix(idempotencyBase, ":out")
            ));
            stockMovementRepository.insertMovement(buildTransferMovement(
                    companyId,
                    toBranchId,
                    productId,
                    unit.getProductUnitId(),
                    InventoryMovementType.TRANSFER_IN,
                    UNIT_IN,
                    fromBranchId,
                    toBranchId,
                    request.getActorName(),
                    appendSuffix(idempotencyBase, ":in")
            ));
        }
    }

    private void validateTrackingTypeChange(long companyId,
                                            long branchId,
                                            long productId,
                                            TrackingType currentTrackingType,
                                            TrackingType targetTrackingType) {
        TrackingType current = TrackingType.defaultIfNull(currentTrackingType);
        if (current == targetTrackingType) {
            return;
        }

        long serializedUnitCount = productTrackingRepository.countSerializedUnits(companyId, productId);
        if ((current.isSerialized() || targetTrackingType.isSerialized()) && serializedUnitCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PRODUCT_TRACKING_CONVERSION_UNITS_EXIST",
                    "Product tracking type cannot be changed while serialized units exist"
            );
        }

        if (current == TrackingType.QUANTITY && targetTrackingType.isSerialized()) {
            long nonZeroBalances = productTrackingRepository.countNonZeroQuantityBalance(companyId, branchId, productId);
            if (nonZeroBalances > 0) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "PRODUCT_TRACKING_CONVERSION_STOCK_EXISTS",
                        "Quantity stock must be reconciled before converting this product to serialized tracking"
                );
            }
        }
    }

    private ProductUnit buildProductUnit(long companyId,
                                         long branchId,
                                         long productId,
                                         TrackingType trackingType,
                                         SerializedUnitInput input,
                                         SerializedUnitStockInRequest request) {
        if (input == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERIALIZED_UNIT_INVALID", "Serialized unit is required");
        }

        String imei = trackingType == TrackingType.IMEI ? normalizeImei(input.getImei()) : blankToNull(input.getImei());
        String serialNumber = trackingType == TrackingType.SERIAL
                ? requireNonBlank(input.getSerialNumber(), "serialNumber")
                : blankToNull(input.getSerialNumber());
        String unitIdentifier = trackingType == TrackingType.IMEI ? imei : serialNumber;
        String conditionCode = blankToNull(input.getConditionCode());

        ProductUnit productUnit = new ProductUnit();
        productUnit.setCompanyId(companyId);
        productUnit.setBranchId(branchId);
        productUnit.setProductId(productId);
        productUnit.setTrackingType(trackingType);
        productUnit.setUnitIdentifier(unitIdentifier);
        productUnit.setImei(imei);
        productUnit.setSerialNumber(serialNumber);
        productUnit.setStatus(ProductUnitStatus.AVAILABLE);
        productUnit.setConditionCode(conditionCode == null ? "NEW" : conditionCode);
        productUnit.setAcquisitionCost(request.getUnitCost());
        productUnit.setSupplierId(request.getSupplierId());
        productUnit.setPurchaseReferenceType(blankToNull(request.getPurchaseReferenceType()));
        productUnit.setPurchaseReferenceId(blankToNull(request.getPurchaseReferenceId()));
        productUnit.setPurchaseLineId(request.getPurchaseLineId());
        productUnit.setReceivedAt(new Timestamp(System.currentTimeMillis()));
        return productUnit;
    }

    private InventoryStockMovement buildMovement(long companyId,
                                                 Long branchId,
                                                 long productId,
                                                 Long productUnitId,
                                                 InventoryMovementType movementType,
                                                 BigDecimal quantityDelta,
                                                 String referenceType,
                                                 String referenceId,
                                                 Long referenceLineId,
                                                 Long supplierId,
                                                 Long customerId,
                                                 String actorName,
                                                 String idempotencyKey) {
        InventoryStockMovement movement = new InventoryStockMovement();
        movement.setCompanyId(companyId);
        movement.setBranchId(branchId);
        movement.setProductId(productId);
        movement.setProductUnitId(productUnitId);
        movement.setMovementType(movementType);
        movement.setQuantityDelta(quantityDelta);
        movement.setReferenceType(blankToNull(referenceType));
        movement.setReferenceId(blankToNull(referenceId));
        movement.setReferenceLineId(referenceLineId);
        movement.setSupplierId(supplierId);
        movement.setCustomerId(customerId);
        movement.setActorName(blankToNull(actorName));
        movement.setIdempotencyKey(blankToNull(idempotencyKey));
        return movement;
    }

    private InventoryStockMovement buildTransferMovement(long companyId,
                                                         long branchId,
                                                         long productId,
                                                         long productUnitId,
                                                         InventoryMovementType movementType,
                                                         BigDecimal quantityDelta,
                                                         long fromBranchId,
                                                         long toBranchId,
                                                         String actorName,
                                                         String idempotencyKey) {
        InventoryStockMovement movement = buildMovement(
                companyId,
                branchId,
                productId,
                productUnitId,
                movementType,
                quantityDelta,
                "BRANCH_TRANSFER",
                fromBranchId + "->" + toBranchId,
                null,
                null,
                null,
                actorName,
                idempotencyKey
        );
        movement.setFromBranchId(fromBranchId);
        movement.setToBranchId(toBranchId);
        return movement;
    }

    private TrackingType requireSupportedTrackingType(TrackingType trackingType) {
        TrackingType safeTrackingType = TrackingType.defaultIfNull(trackingType);
        if (safeTrackingType == TrackingType.BATCH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_TRACKING_TYPE_UNSUPPORTED", "Batch tracking is reserved for a later phase");
        }
        return safeTrackingType;
    }

    private TrackingType requireSerializedTrackingType(TrackingType trackingType) {
        TrackingType safeTrackingType = requireSupportedTrackingType(trackingType);
        if (!safeTrackingType.isSerialized()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_TRACKING_TYPE_NOT_SERIALIZED", "Tracking type must be IMEI or SERIAL");
        }
        return safeTrackingType;
    }

    private String normalizeImei(String imei) {
        String normalizedImei = requireNonBlank(imei, "imei");
        if (!isValidImei(normalizedImei)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMEI_INVALID", "IMEI must be a valid 15-digit number");
        }
        return normalizedImei;
    }

    private boolean isValidImei(String value) {
        if (value == null || !value.matches("\\d{15}")) {
            return false;
        }

        int sum = 0;
        boolean doubleDigit = false;
        for (int index = value.length() - 1; index >= 0; index -= 1) {
            int digit = value.charAt(index) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private String buildUnitIdempotencyKey(String requestIdempotencyKey, String unitIdentifier) {
        String normalizedRequestKey = blankToNull(requestIdempotencyKey);
        if (normalizedRequestKey == null) {
            return null;
        }

        String key = normalizedRequestKey + ":" + unitIdentifier;
        if (key.length() <= 160) {
            return key;
        }
        return key.substring(0, 160);
    }

    private String appendSuffix(String value, String suffix) {
        if (value == null) {
            return null;
        }
        String result = value + suffix;
        return result.length() <= 160 ? result : result.substring(0, 160);
    }

    private long requirePositive(long value, String fieldName) {
        if (value < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", fieldName + " is required");
        }
        return value;
    }

    private int normalizeLimit(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 500);
    }

    private String requireNonBlank(String value, String fieldName) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", fieldName + " is required");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
