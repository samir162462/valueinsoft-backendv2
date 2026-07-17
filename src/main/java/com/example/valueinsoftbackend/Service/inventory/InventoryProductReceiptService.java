package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductReceiptRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductReceiptRepository.InventoryTemplateAttributeDefinition;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductReceiptRepository.InventoryTemplateDefinition;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductReceiptRepository.ProductReceiptProductSnapshot;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductReceiptRepository.StockBalanceResult;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductUnitRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryStockMovementRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProductCommandRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Inventory.InventoryMovementType;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnit;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnitStatus;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitInput;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.AcquisitionSource;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptDetailsRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptOperationMode;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptProductRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptPublicCatalogRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptRequest;
import com.example.valueinsoftbackend.Model.Response.InventoryReceipt.ProductReceiptResponse;
import com.example.valueinsoftbackend.Service.CategoryService;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.Service.openitems.ApOpenItemService;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class InventoryProductReceiptService {

    private static final String OPERATION_TYPE = "PRODUCT_RECEIPT";

    private final DbInventoryProductReceiptRepository receiptRepository;
    private final DbPosProductCommandRepository productCommandRepository;
    private final DbInventoryProductUnitRepository productUnitRepository;
    private final DbInventoryStockMovementRepository stockMovementRepository;
    private final FinanceOperationalPostingService financeOperationalPostingService;
    private final CategoryService categoryService;
    private final BranchTaxonomyResolver branchTaxonomyResolver;
    private final ObjectMapper objectMapper;
    private ApOpenItemService apOpenItemService;

    public InventoryProductReceiptService(DbInventoryProductReceiptRepository receiptRepository,
                                          DbPosProductCommandRepository productCommandRepository,
                                          DbInventoryProductUnitRepository productUnitRepository,
                                          DbInventoryStockMovementRepository stockMovementRepository,
                                          FinanceOperationalPostingService financeOperationalPostingService,
                                          CategoryService categoryService,
                                          BranchTaxonomyResolver branchTaxonomyResolver,
                                          ObjectMapper objectMapper) {
        this.receiptRepository = receiptRepository;
        this.productCommandRepository = productCommandRepository;
        this.productUnitRepository = productUnitRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.financeOperationalPostingService = financeOperationalPostingService;
        this.categoryService = categoryService;
        this.branchTaxonomyResolver = branchTaxonomyResolver;
        this.objectMapper = objectMapper;
    }

    @Autowired
    void setApOpenItemService(ApOpenItemService apOpenItemService) {
        this.apOpenItemService = apOpenItemService;
    }

    @Transactional
    public ProductReceiptResponse receiveProduct(String actorName, ProductReceiptRequest request) {
        validateRequestEnvelope(request);
        String operationId = UUID.randomUUID().toString();
        String requestHash = hashRequest(request);
        String idempotencyKey = normalizeRequired(request.getIdempotencyKey(), "IDEMPOTENCY_KEY_REQUIRED", "idempotencyKey is required");

        receiptRepository.insertPendingIdempotency(
                request.getCompanyId(),
                request.getBranchId(),
                OPERATION_TYPE,
                idempotencyKey,
                requestHash,
                actorName,
                operationId);

        DbInventoryProductReceiptRepository.IdempotencyRecord idempotency = receiptRepository.findIdempotencyForUpdate(
                        request.getCompanyId(),
                        request.getBranchId(),
                        OPERATION_TYPE,
                        idempotencyKey)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_STATE_MISSING", "Idempotency state could not be locked"));

        if (!requestHash.equals(idempotency.requestHash())) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_PAYLOAD_CONFLICT", "The same idempotency key was already used with a different request payload");
        }
        if ("COMPLETED".equalsIgnoreCase(idempotency.status()) && idempotency.responsePayload() != null) {
            ProductReceiptResponse replay = objectMapper.convertValue(idempotency.responsePayload(), ProductReceiptResponse.class);
            replay.setIdempotentReplay(true);
            return replay;
        }

        ProductReceiptDetailsRequest receipt = request.getReceipt();
        AcquisitionSource acquisitionSource = AcquisitionSource.defaultIfNull(receipt.getAcquisitionSource());
        String conditionCode = normalizeConditionCode(receipt.getConditionCode());
        BigDecimal totalCost = money(receipt.getUnitCost()).multiply(BigDecimal.valueOf(receipt.getQuantity())).setScale(4, RoundingMode.HALF_UP);
        BigDecimal paidAmount = money(receipt.getPaidAmount());
        BigDecimal remainingAmount = totalCost.subtract(paidAmount).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        Timestamp receiptTime = Timestamp.valueOf((receipt.getReceiptDate() == null ? LocalDate.now() : receipt.getReceiptDate()).atStartOfDay());

        if (paidAmount.compareTo(totalCost) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_PAID_AMOUNT_INVALID", "paidAmount cannot exceed receipt total cost");
        }
        validatePaymentOption(receipt, totalCost, paidAmount);

        // Template, tracking-type, attribute and serialized-unit validation now happens
        // inside resolveProduct BEFORE any product row is inserted, so a bad payload
        // (e.g. an invalid IMEI) never creates-then-rolls-back a product record.
        ProductReceiptProductSnapshot product = resolveProduct(request);
        TrackingType trackingType = TrackingType.defaultIfNull(product.trackingType());

        DbInventoryProductReceiptRepository.ClientSnapshot tradeInClient = null;
        if (acquisitionSource.isClientTradeIn()) {
            tradeInClient = receiptRepository.findClientForUpdate(request.getCompanyId(), receipt.getClientId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "Client was not found for this tenant"));
            if (!tradeInClient.isActive()) {
                throw new ApiException(HttpStatus.CONFLICT, "CLIENT_NOT_ACTIVE", "Archived clients cannot sell products to the shop");
            }
        }

        StockBalanceResult balance = receiptRepository.increaseBranchStockBalance(
                request.getCompanyId(),
                request.getBranchId(),
                product.productId(),
                receipt.getQuantity());

        String referenceId = idempotency.operationId() == null ? operationId : idempotency.operationId();
        long ledgerId = receiptRepository.insertReceiptLedger(
                request.getCompanyId(),
                request.getBranchId(),
                product.productId(),
                receipt.getQuantity(),
                acquisitionSource.isClientTradeIn() ? 0 : receipt.getSupplierId(),
                totalCost,
                normalizePaymentMethod(receipt.getPaymentMethod()),
                remainingAmount,
                actorName,
                referenceId,
                idempotencyKey,
                receipt.getNotes(),
                acquisitionSource.isClientTradeIn() ? "CLIENT" : "SUPPLIER",
                acquisitionSource.isClientTradeIn() ? receipt.getClientId() : null,
                conditionCode,
                blankToNull(receipt.getConditionNotes()));

        insertSerializedUnitsIfRequired(request, product, trackingType, referenceId, actorName, acquisitionSource, conditionCode);

        if (!acquisitionSource.isClientTradeIn() && remainingAmount.signum() > 0 && apOpenItemService != null) {
            apOpenItemService.createPurchaseOpenItem(
                    request.getCompanyId(), request.getBranchId(), receipt.getSupplierId(), ledgerId,
                    remainingAmount, receiptTime.toLocalDateTime(), idempotencyKey, actorName);
        }

        int legacyTransactionId = 0;
        FinancePostingRequestItem financeRequest;
        String paymentStatus = derivePaymentStatus(totalCost, paidAmount, remainingAmount);

        if (acquisitionSource.isClientTradeIn()) {
            long tradeInReceiptId = receiptRepository.insertClientTradeInReceipt(
                    request.getCompanyId(),
                    request.getBranchId(),
                    receipt.getClientId(),
                    ledgerId,
                    product.productId(),
                    referenceId,
                    receipt.getQuantity(),
                    conditionCode,
                    blankToNull(receipt.getConditionNotes()),
                    money(receipt.getUnitCost()),
                    totalCost,
                    paidAmount,
                    remainingAmount,
                    paymentStatus,
                    paidAmount.compareTo(BigDecimal.ZERO) > 0 ? normalizePaymentMethod(receipt.getPaymentMethod()) : null,
                    idempotencyKey,
                    actorName);

            financeRequest = financeOperationalPostingService.enqueueClientTradeInReceipt(
                    request.getCompanyId(),
                    request.getBranchId(),
                    receipt.getClientId(),
                    tradeInReceiptId,
                    product.productId(),
                    ledgerId,
                    receipt.getQuantity(),
                    totalCost,
                    paidAmount,
                    normalizePaymentMethod(receipt.getPaymentMethod()),
                    receiptTime,
                    actorName);
        } else {
            int supplierRows = receiptRepository.updateSupplierPurchaseTotals(
                    request.getCompanyId(),
                    request.getBranchId(),
                    receipt.getSupplierId(),
                    totalCost,
                    remainingAmount);
            if (supplierRows != 1) {
                throw new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "Supplier was not found in the selected branch");
            }

            legacyTransactionId = receiptRepository.insertLegacyInventoryTransaction(
                    request.getCompanyId(),
                    request.getBranchId(),
                    product.productId(),
                    actorName,
                    receipt.getSupplierId(),
                    receipt.getQuantity(),
                    totalCost,
                    normalizePaymentMethod(receipt.getPaymentMethod()),
                    receiptTime,
                    remainingAmount);

            financeRequest = financeOperationalPostingService.enqueuePurchaseInventoryTransaction(
                    request.getCompanyId(),
                    request.getBranchId(),
                    new InventoryTransaction(
                            legacyTransactionId,
                            (int) product.productId(),
                            actorName,
                            receipt.getSupplierId(),
                            "PurchaseReceipt",
                            receipt.getQuantity(),
                            moneyToInt(totalCost),
                            normalizePaymentMethod(receipt.getPaymentMethod()),
                            receiptTime,
                            moneyToInt(remainingAmount)),
                    legacyTransactionId,
                    ledgerId);
        }

        ProductReceiptResponse response = buildResponse(referenceId, product, request, balance, ledgerId, totalCost, paidAmount, remainingAmount, legacyTransactionId, financeRequest, acquisitionSource, conditionCode, paymentStatus);
        try {
            receiptRepository.markIdempotencyCompleted(request.getCompanyId(), idempotency.id(), objectMapper.writeValueAsString(response));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_RESPONSE_SAVE_FAILED", "Receipt response could not be persisted for idempotency replay");
        }
        return response;
    }

    private ProductReceiptProductSnapshot resolveProduct(ProductReceiptRequest request) {
        if (request.getOperationMode() == ProductReceiptOperationMode.RECEIVE_EXISTING_PRODUCT) {
            if (request.getExistingProductId() == null || request.getExistingProductId() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "EXISTING_PRODUCT_ID_REQUIRED", "existingProductId is required for existing product receipts");
            }
            ProductReceiptProductSnapshot existing = receiptRepository.findProductForUpdate(request.getCompanyId(), request.getExistingProductId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product was not found for this tenant"));
            TrackingType trackingType = TrackingType.defaultIfNull(existing.trackingType());
            validateTemplateAndSerializedUnits(request, trackingType, existing.businessLineKey(), existing.templateKey(), null);
            return existing;
        }

        ProductReceiptProductRequest productRequest = request.getProduct();
        if (productRequest == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_PAYLOAD_REQUIRED", "product is required when creating and receiving stock");
        }
        BranchTaxonomyResolver.ResolvedClassification classification =
                validateCreateProductPayload(request.getCompanyId(), request.getBranchId(), productRequest);

        // Validate template support, attribute schema and serialized units (e.g. IMEI format)
        // using the request payload BEFORE inserting the product row, so an invalid request
        // never has to create-then-roll-back a product.
        TrackingType trackingType = TrackingType.defaultIfNull(productRequest.getTrackingType());
        validateTemplateAndSerializedUnits(request, trackingType, productRequest.getBusinessLineKey(),
                productRequest.getTemplateKey(), productRequest.getAttributes());

        Product product = toProduct(productRequest, request.getReceipt().getSupplierId(), classification,
                request.getReceipt().getConditionCode());
        long productId = productCommandRepository.addProduct(product, String.valueOf(request.getBranchId()), request.getCompanyId());
        return receiptRepository.findProductForUpdate(request.getCompanyId(), productId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_CREATE_RELOAD_FAILED", "Created product could not be reloaded"));
    }

    /**
     * Resolves the active template for the given business line/template key, checks that the
     * template supports the requested tracking type, validates dynamic attributes for new
     * products (attributesForNewProduct == null skips this for existing products, whose
     * attributes were already validated when they were created), and validates any
     * serializedUnits (IMEI/serial) on the request. Called before any product row is written.
     */
    private void validateTemplateAndSerializedUnits(ProductReceiptRequest request,
                                                     TrackingType trackingType,
                                                     String businessLineKey,
                                                     String templateKey,
                                                     Map<String, Object> attributesForNewProduct) {
        InventoryTemplateDefinition template = receiptRepository.findActiveTemplate(
                        request.getCompanyId(),
                        normalizeBusinessLine(businessLineKey),
                        normalizeTemplate(templateKey))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_TEMPLATE_INVALID", "Product template is missing or inactive"));
        validateTrackingAgainstTemplate(trackingType, template);
        if (attributesForNewProduct != null) {
            validateAttributes(request.getCompanyId(), template, attributesForNewProduct);
        }
        validateSerializedUnits(request, trackingType);
    }

    private void validateRequestEnvelope(ProductReceiptRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_REQUIRED", "Request body is required");
        }
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        if (request.getOperationMode() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATION_MODE_REQUIRED", "operationMode is required");
        }
        if (request.getReceipt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_REQUIRED", "receipt is required");
        }
        ProductReceiptDetailsRequest receipt = request.getReceipt();
        AcquisitionSource acquisitionSource = AcquisitionSource.defaultIfNull(receipt.getAcquisitionSource());
        if (acquisitionSource.isClientTradeIn()) {
            if (receipt.getClientId() == null || receipt.getClientId() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CLIENT_ID_REQUIRED", "clientId is required for client trade-in receipts");
            }
            if (receipt.getSupplierId() > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_NOT_ALLOWED_FOR_TRADE_IN", "supplierId must be omitted for client trade-in receipts");
            }
        } else {
            TenantSqlIdentifiers.requirePositive(receipt.getSupplierId(), "supplierId");
            if (receipt.getClientId() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CLIENT_NOT_ALLOWED_FOR_SUPPLIER", "clientId must be omitted for supplier receipts");
            }
        }
        normalizeConditionCode(receipt.getConditionCode());
        if (receipt.getQuantity() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_QUANTITY_INVALID", "receipt.quantity must be greater than zero");
        }
        if (money(receipt.getUnitCost()).compareTo(BigDecimal.ZERO) < 0 || money(receipt.getPaidAmount()).compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_AMOUNT_INVALID", "Receipt amounts must be non-negative");
        }
    }

    private String normalizeConditionCode(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return "NEW";
        }
        normalized = normalized.trim().toUpperCase(Locale.ROOT);
        if (!"NEW".equals(normalized) && !"USED".equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONDITION_CODE_INVALID", "conditionCode must be NEW or USED");
        }
        return normalized;
    }

    private void validatePaymentOption(ProductReceiptDetailsRequest receipt, BigDecimal totalCost, BigDecimal paidAmount) {
        String option = blankToNull(receipt.getPaymentOption());
        if (option == null) {
            return;
        }
        option = option.trim().toUpperCase(Locale.ROOT);
        switch (option) {
            case "FULL" -> {
                requirePaymentMethod(receipt);
                if (paidAmount.compareTo(totalCost) != 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_FULL_AMOUNT_MISMATCH", "Pay in full requires paidAmount equal to the receipt total");
                }
            }
            case "PARTIAL" -> {
                requirePaymentMethod(receipt);
                if (paidAmount.compareTo(BigDecimal.ZERO) <= 0 || paidAmount.compareTo(totalCost) >= 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_PARTIAL_AMOUNT_INVALID", "Partial payment requires paidAmount greater than zero and less than the receipt total");
                }
            }
            case "LATER" -> {
                if (paidAmount.compareTo(BigDecimal.ZERO) != 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_LATER_AMOUNT_INVALID", "Pay later requires paidAmount of zero");
                }
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_OPTION_INVALID", "paymentOption must be FULL, PARTIAL, or LATER");
        }
    }

    private void requirePaymentMethod(ProductReceiptDetailsRequest receipt) {
        if (blankToNull(receipt.getPaymentMethod()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_METHOD_REQUIRED", "paymentMethod is required when paying now");
        }
    }

    private String derivePaymentStatus(BigDecimal totalCost, BigDecimal paidAmount, BigDecimal remainingAmount) {
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "PAID";
        }
        if (paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "UNPAID";
        }
        return "PARTIALLY_PAID";
    }

    private BranchTaxonomyResolver.ResolvedClassification validateCreateProductPayload(int companyId, int branchId, ProductReceiptProductRequest product) {
        normalizeRequired(product.getProductName(), "PRODUCT_NAME_REQUIRED", "productName is required");
        BigDecimal buyingPrice = money(product.getBuyingPrice());
        BigDecimal lowestPrice = money(product.getLowestPrice());
        BigDecimal retailPrice = money(product.getRetailPrice());
        if (retailPrice.compareTo(lowestPrice) < 0 || lowestPrice.compareTo(buyingPrice) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_PRICE_ORDER_INVALID", "retailPrice must be greater than or equal to lowestPrice, and lowestPrice must be greater than or equal to buyingPrice");
        }
        return branchTaxonomyResolver.resolveForProduct(companyId, branchId, product);
    }

    private void validateTrackingAgainstTemplate(TrackingType trackingType, InventoryTemplateDefinition template) {
        if ((trackingType == TrackingType.IMEI || trackingType == TrackingType.SERIAL) && !template.supportsSerial()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRACKING_TYPE_TEMPLATE_UNSUPPORTED", "Template does not support serial or IMEI tracking");
        }
        if (trackingType == TrackingType.BATCH && !template.supportsBatch()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRACKING_TYPE_TEMPLATE_UNSUPPORTED", "Template does not support batch tracking");
        }
    }

    private void validateAttributes(int companyId, InventoryTemplateDefinition template, Map<String, Object> attributes) {
        Map<String, Object> safeAttributes = attributes == null ? Map.of() : attributes;
        Map<String, InventoryTemplateAttributeDefinition> definitions = new HashMap<>();
        for (InventoryTemplateAttributeDefinition definition : receiptRepository.findTemplateAttributes(companyId, template.templateId())) {
            definitions.put(definition.attributeKey(), definition);
        }
        for (String key : safeAttributes.keySet()) {
            if (!definitions.containsKey(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ATTRIBUTE_NOT_IN_TEMPLATE", "Unsupported product attribute: " + key);
            }
        }
        for (InventoryTemplateAttributeDefinition definition : definitions.values()) {
            Object value = safeAttributes.get(definition.attributeKey());
            if (definition.required() && isBlankValue(value)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ATTRIBUTE_REQUIRED", "Missing required attribute: " + definition.attributeKey());
            }
            if (!isBlankValue(value)) {
                validateAttributeType(definition, value);
            }
        }
    }

    private void validateAttributeType(InventoryTemplateAttributeDefinition definition, Object value) {
        String dataType = definition.dataType() == null ? "TEXT" : definition.dataType().toUpperCase(Locale.ROOT);
        switch (dataType) {
            case "NUMBER" -> {
                if (!(value instanceof Number)) {
                    try {
                        new BigDecimal(value.toString());
                    } catch (NumberFormatException exception) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "ATTRIBUTE_TYPE_INVALID", definition.attributeKey() + " must be numeric");
                    }
                }
            }
            case "BOOLEAN" -> {
                if (!(value instanceof Boolean) && !"true".equalsIgnoreCase(value.toString()) && !"false".equalsIgnoreCase(value.toString())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "ATTRIBUTE_TYPE_INVALID", definition.attributeKey() + " must be boolean");
                }
            }
            case "DATE" -> {
                try {
                    LocalDate.parse(value.toString());
                } catch (RuntimeException exception) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "ATTRIBUTE_TYPE_INVALID", definition.attributeKey() + " must be yyyy-MM-dd date");
                }
            }
            default -> { }
        }
    }

    private void validateSerializedUnits(ProductReceiptRequest request, TrackingType trackingType) {
        List<SerializedUnitInput> units = request.getSerializedUnits() == null ? List.of() : request.getSerializedUnits();
        if (!trackingType.isSerialized()) {
            if (!units.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SERIALIZED_UNITS_NOT_ALLOWED", "serializedUnits are only accepted for IMEI or SERIAL products");
            }
            return;
        }
        if (units.size() != request.getReceipt().getQuantity()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERIALIZED_UNIT_COUNT_MISMATCH", "serializedUnits count must equal receipt.quantity");
        }
        Set<String> uniqueIdentifiers = new HashSet<>();
        for (SerializedUnitInput unit : units) {
            String identifier = trackingType == TrackingType.IMEI
                    ? normalizeRequired(unit.getImei(), "IMEI_REQUIRED", "imei is required")
                    : normalizeRequired(unit.getSerialNumber(), "SERIAL_NUMBER_REQUIRED", "serialNumber is required");
            if (trackingType == TrackingType.IMEI && !isValidImei(identifier)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "IMEI_INVALID", "IMEI must be a valid 15-digit number");
            }
            if (!uniqueIdentifiers.add(identifier.toLowerCase(Locale.ROOT))) {
                throw new ApiException(HttpStatus.CONFLICT, "SERIALIZED_UNIT_DUPLICATE_IN_REQUEST", "Duplicate IMEI or serial in request");
            }
        }
    }

    private void insertSerializedUnitsIfRequired(ProductReceiptRequest request,
                                                 ProductReceiptProductSnapshot product,
                                                 TrackingType trackingType,
                                                 String referenceId,
                                                 String actorName,
                                                 AcquisitionSource acquisitionSource,
                                                 String receiptConditionCode) {
        if (!trackingType.isSerialized()) {
            return;
        }
        boolean clientTradeIn = acquisitionSource.isClientTradeIn();
        for (SerializedUnitInput input : request.getSerializedUnits()) {
            ProductUnit unit = new ProductUnit();
            unit.setCompanyId(request.getCompanyId());
            unit.setBranchId(request.getBranchId());
            unit.setProductId(product.productId());
            unit.setTrackingType(trackingType);
            unit.setUnitIdentifier(trackingType == TrackingType.IMEI ? input.getImei().trim() : input.getSerialNumber().trim());
            unit.setImei(trackingType == TrackingType.IMEI ? input.getImei().trim() : blankToNull(input.getImei()));
            unit.setSerialNumber(trackingType == TrackingType.SERIAL ? input.getSerialNumber().trim() : blankToNull(input.getSerialNumber()));
            unit.setStatus(ProductUnitStatus.AVAILABLE);
            unit.setConditionCode(blankToNull(input.getConditionCode()) == null
                    ? receiptConditionCode
                    : normalizeConditionCode(input.getConditionCode()));
            unit.setConditionNotes(blankToNull(request.getReceipt().getConditionNotes()));
            unit.setSupplierId(clientTradeIn ? null : (long) request.getReceipt().getSupplierId());
            unit.setSourcePartyType(clientTradeIn ? "CLIENT" : "SUPPLIER");
            unit.setSourceClientId(clientTradeIn ? request.getReceipt().getClientId().longValue() : null);
            unit.setPurchaseReferenceType("PRODUCT_RECEIPT");
            unit.setPurchaseReferenceId(referenceId);
            long unitId = stockInOrReactivateProductUnit(unit);

            InventoryStockMovement movement = new InventoryStockMovement();
            movement.setCompanyId(request.getCompanyId());
            movement.setBranchId((long) request.getBranchId());
            movement.setProductId(product.productId());
            movement.setProductUnitId(unitId);
            movement.setMovementType(InventoryMovementType.STOCK_IN);
            movement.setQuantityDelta(BigDecimal.ONE);
            movement.setReferenceType("PRODUCT_RECEIPT");
            movement.setReferenceId(referenceId);
            movement.setSupplierId(clientTradeIn ? null : (long) request.getReceipt().getSupplierId());
            movement.setActorName(actorName);
            movement.setIdempotencyKey(limit(request.getIdempotencyKey() + ":" + unit.getUnitIdentifier(), 160));
            stockMovementRepository.insertMovement(movement);
        }
    }

    private long stockInOrReactivateProductUnit(ProductUnit unit) {
        try {
            return productUnitRepository.insertProductUnit(unit);
        } catch (DuplicateKeyException exception) {
            ProductUnit existingUnit = productUnitRepository.findByCompanyScanCode(unit.getCompanyId(), unit.getUnitIdentifier())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT,
                            "SERIALIZED_UNIT_DUPLICATE",
                            "This IMEI or serial already exists for the company"
                    ));

            validateExistingUnitCanBeReceivedAgain(existingUnit, unit);
            unit.setProductUnitId(existingUnit.getProductUnitId());
            int updated = productUnitRepository.reactivateForStockIn(unit, existingUnit.getStatus());
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

    private Product toProduct(ProductReceiptProductRequest request,
                              int supplierId,
                              BranchTaxonomyResolver.ResolvedClassification classification,
                              String receiptConditionCodeForProduct) {
        Product product = new Product();
        product.setProductName(limit(request.getProductName().trim(), 30));
        product.setBuyingDay(new Timestamp(System.currentTimeMillis()));
        product.setActivationPeriod("0");
        product.setBPrice(moneyToInt(request.getBuyingPrice()));
        product.setLPrice(moneyToInt(request.getLowestPrice()));
        product.setRPrice(moneyToInt(request.getRetailPrice()));
        product.setCompanyName(limit(firstNonBlank(request.getSku(), request.getProductName()), 30));
        product.setType(limit(firstNonBlank(classification.subcategoryName(), request.getSubcategoryId() == null ? "default" : String.valueOf(request.getSubcategoryId())), 15));
        product.setSerial(firstNonBlank(request.getBarcode(), request.getSku()));
        product.setDesc("");
        product.setBatteryLife(0);
        product.setQuantity(0);
        product.setPState("USED".equals(normalizeConditionCode(receiptConditionCodeForProduct)) ? "Used" : "New");
        product.setSupplierId(supplierId);
        product.setMajor(limit(firstNonBlank(classification.categoryName(), request.getCategoryId() == null ? normalizeBusinessLine(request.getBusinessLineKey()) : String.valueOf(request.getCategoryId())), 30));
        product.setGroupKey(classification.groupKey());
        product.setCategoryKey(classification.categoryKey());
        product.setSubcategoryKey(classification.subcategoryKey());
        product.setGroupName(limit(classification.groupName(), 100));
        product.setCategoryName(limit(classification.categoryName(), 100));
        product.setSubcategoryName(limit(classification.subcategoryName(), 100));
        product.setBrand(limit(blankToNull(request.getBrand()), 100));
        product.setModel(limit(blankToNull(request.getModel()), 100));
        product.setManufacturer(limit(blankToNull(request.getManufacturer()), 100));
        product.setTaxonomyVersion(classification.taxonomyVersion());
        product.setBusinessLineKey(normalizeBusinessLine(request.getBusinessLineKey()));
        product.setTemplateKey(normalizeTemplate(request.getTemplateKey()));
        product.setBaseUomCode(firstNonBlank(request.getBaseUomCode(), "PCS"));
        product.setPricingPolicyCode(firstNonBlank(request.getPricingPolicyCode(), "FIXED_RETAIL"));
        product.setTrackingType(TrackingType.defaultIfNull(request.getTrackingType()));
        product.setSku(blankToNull(request.getSku()));
        product.setBarcode(blankToNull(request.getBarcode()));
        ProductReceiptPublicCatalogRequest publicCatalog = request.getPublicCatalog();
        if (publicCatalog != null) {
            product.setShowOnline(publicCatalog.isShowOnline());
            product.setOnlineActive(publicCatalog.isOnlineActive());
            product.setOnlineDescription(publicCatalog.getOnlineDescription());
            product.setOnlineImageUrl(publicCatalog.getOnlineImageUrl());
            product.setOnlineOfferPrice(publicCatalog.getOnlineOfferPrice());
            product.setOnlineSortOrder(publicCatalog.getOnlineSortOrder() == null ? 0 : publicCatalog.getOnlineSortOrder());
        }
        try {
            product.setAttributes(objectMapper.writeValueAsString(request.getAttributes() == null ? Map.of() : request.getAttributes()));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_ATTRIBUTES_INVALID", "Product attributes could not be serialized");
        }
        return product;
    }

    private ProductReceiptResponse buildResponse(String operationId,
                                                 ProductReceiptProductSnapshot product,
                                                 ProductReceiptRequest request,
                                                 StockBalanceResult balance,
                                                 long ledgerId,
                                                 BigDecimal totalCost,
                                                 BigDecimal paidAmount,
                                                 BigDecimal remainingAmount,
                                                 int legacyTransactionId,
                                                 FinancePostingRequestItem financeRequest,
                                                 AcquisitionSource acquisitionSource,
                                                 String conditionCode,
                                                 String paymentStatus) {
        return new ProductReceiptResponse(
                operationId,
                false,
                new ProductReceiptResponse.ProductSummary(product.productId(), product.productName(), product.sku(), product.barcode(), product.trackingType().name()),
                new ProductReceiptResponse.ReceiptSummary(
                        legacyTransactionId,
                        request.getBranchId(),
                        request.getReceipt().getQuantity(),
                        balance.previousQuantity(),
                        balance.newQuantity(),
                        request.getReceipt().getSupplierId(),
                        totalCost,
                        paidAmount,
                        remainingAmount,
                        acquisitionSource.name(),
                        acquisitionSource.isClientTradeIn() ? request.getReceipt().getClientId() : null,
                        conditionCode,
                        paymentStatus),
                new ProductReceiptResponse.LedgerSummary(ledgerId, "PURCHASE_RECEIPT", request.getReceipt().getQuantity()),
                new ProductReceiptResponse.FinanceSummary(financeRequest == null ? "SKIPPED" : financeRequest.getStatus(), financeRequest == null || financeRequest.getPostingRequestId() == null ? null : financeRequest.getPostingRequestId().toString()),
                List.of()
        );
    }

    private String hashRequest(ProductReceiptRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : hashed) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_HASH_FAILED", "Receipt request could not be hashed");
        }
    }

    private boolean isBlankValue(Object value) {
        return value == null || value.toString().trim().isEmpty();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(4) : value.setScale(4, RoundingMode.HALF_UP);
    }

    private int moneyToInt(BigDecimal value) {
        return money(value).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private String normalizeBusinessLine(String value) {
        return firstNonBlank(value, "MOBILE").trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String normalizeTemplate(String value) {
        return firstNonBlank(value, "mobile_device").trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String normalizePaymentMethod(String value) {
        return firstNonBlank(value, "CASH").trim();
    }

    private String normalizeRequired(String value, String code, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return normalized;
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

    private String firstNonBlank(String preferred, String fallback) {
        String normalized = blankToNull(preferred);
        return normalized == null ? blankToNull(fallback) : normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
