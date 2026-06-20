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
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptDetailsRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptOperationMode;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptProductRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptPublicCatalogRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptRequest;
import com.example.valueinsoftbackend.Model.Response.InventoryReceipt.ProductReceiptResponse;
import com.example.valueinsoftbackend.Service.CategoryService;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.util.CustomPair;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
    private final ObjectMapper objectMapper;

    public InventoryProductReceiptService(DbInventoryProductReceiptRepository receiptRepository,
                                          DbPosProductCommandRepository productCommandRepository,
                                          DbInventoryProductUnitRepository productUnitRepository,
                                          DbInventoryStockMovementRepository stockMovementRepository,
                                          FinanceOperationalPostingService financeOperationalPostingService,
                                          CategoryService categoryService,
                                          ObjectMapper objectMapper) {
        this.receiptRepository = receiptRepository;
        this.productCommandRepository = productCommandRepository;
        this.productUnitRepository = productUnitRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.financeOperationalPostingService = financeOperationalPostingService;
        this.categoryService = categoryService;
        this.objectMapper = objectMapper;
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

        ProductReceiptProductSnapshot product = resolveProduct(request);
        TrackingType trackingType = TrackingType.defaultIfNull(product.trackingType());
        ProductReceiptDetailsRequest receipt = request.getReceipt();
        BigDecimal totalCost = money(receipt.getUnitCost()).multiply(BigDecimal.valueOf(receipt.getQuantity())).setScale(4, RoundingMode.HALF_UP);
        BigDecimal paidAmount = money(receipt.getPaidAmount());
        BigDecimal remainingAmount = totalCost.subtract(paidAmount).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        Timestamp receiptTime = Timestamp.valueOf((receipt.getReceiptDate() == null ? LocalDate.now() : receipt.getReceiptDate()).atStartOfDay());

        validateReceiptPayload(request, product, trackingType, totalCost, paidAmount);

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
                receipt.getSupplierId(),
                totalCost,
                normalizePaymentMethod(receipt.getPaymentMethod()),
                remainingAmount,
                actorName,
                referenceId,
                idempotencyKey,
                receipt.getNotes());

        insertSerializedUnitsIfRequired(request, product, trackingType, referenceId, actorName);

        int supplierRows = receiptRepository.updateSupplierPurchaseTotals(
                request.getCompanyId(),
                request.getBranchId(),
                receipt.getSupplierId(),
                totalCost,
                remainingAmount);
        if (supplierRows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "Supplier was not found in the selected branch");
        }

        int legacyTransactionId = receiptRepository.insertLegacyInventoryTransaction(
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

        FinancePostingRequestItem financeRequest = financeOperationalPostingService.enqueuePurchaseInventoryTransaction(
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

        ProductReceiptResponse response = buildResponse(referenceId, product, request, balance, ledgerId, totalCost, paidAmount, remainingAmount, legacyTransactionId, financeRequest);
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
            return receiptRepository.findProductForUpdate(request.getCompanyId(), request.getExistingProductId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product was not found for this tenant"));
        }

        ProductReceiptProductRequest productRequest = request.getProduct();
        if (productRequest == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_PAYLOAD_REQUIRED", "product is required when creating and receiving stock");
        }
        validateCreateProductPayload(request.getCompanyId(), request.getBranchId(), productRequest);
        Product product = toProduct(productRequest, request.getReceipt().getSupplierId());
        long productId = productCommandRepository.addProduct(product, String.valueOf(request.getBranchId()), request.getCompanyId());
        return receiptRepository.findProductForUpdate(request.getCompanyId(), productId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_CREATE_RELOAD_FAILED", "Created product could not be reloaded"));
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
        TenantSqlIdentifiers.requirePositive(receipt.getSupplierId(), "supplierId");
        if (receipt.getQuantity() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_QUANTITY_INVALID", "receipt.quantity must be greater than zero");
        }
        if (money(receipt.getUnitCost()).compareTo(BigDecimal.ZERO) < 0 || money(receipt.getPaidAmount()).compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_AMOUNT_INVALID", "Receipt amounts must be non-negative");
        }
    }

    private void validateCreateProductPayload(int companyId, int branchId, ProductReceiptProductRequest product) {
        normalizeRequired(product.getProductName(), "PRODUCT_NAME_REQUIRED", "productName is required");
        BigDecimal buyingPrice = money(product.getBuyingPrice());
        BigDecimal lowestPrice = money(product.getLowestPrice());
        BigDecimal retailPrice = money(product.getRetailPrice());
        if (retailPrice.compareTo(lowestPrice) < 0 || lowestPrice.compareTo(buyingPrice) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_PRICE_ORDER_INVALID", "retailPrice must be greater than or equal to lowestPrice, and lowestPrice must be greater than or equal to buyingPrice");
        }
        validateCategory(companyId, branchId, product);
    }

    private void validateReceiptPayload(ProductReceiptRequest request,
                                        ProductReceiptProductSnapshot product,
                                        TrackingType trackingType,
                                        BigDecimal totalCost,
                                        BigDecimal paidAmount) {
        if (paidAmount.compareTo(totalCost) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_PAID_AMOUNT_INVALID", "paidAmount cannot exceed receipt total cost");
        }
        InventoryTemplateDefinition template = receiptRepository.findActiveTemplate(
                        request.getCompanyId(),
                        normalizeBusinessLine(product.businessLineKey()),
                        normalizeTemplate(product.templateKey()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_TEMPLATE_INVALID", "Product template is missing or inactive"));
        validateTrackingAgainstTemplate(trackingType, template);
        if (request.getOperationMode() == ProductReceiptOperationMode.CREATE_PRODUCT_AND_RECEIVE) {
            validateAttributes(request.getCompanyId(), template, request.getProduct() == null ? Map.of() : request.getProduct().getAttributes());
        }
        validateSerializedUnits(request, trackingType);
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
            if (trackingType == TrackingType.IMEI && !identifier.matches("\\d{14,17}")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "IMEI_INVALID", "IMEI must be 14 to 17 digits");
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
                                                 String actorName) {
        if (!trackingType.isSerialized()) {
            return;
        }
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
            unit.setConditionCode(blankToNull(input.getConditionCode()) == null ? "NEW" : input.getConditionCode().trim());
            unit.setSupplierId((long) request.getReceipt().getSupplierId());
            unit.setPurchaseReferenceType("PRODUCT_RECEIPT");
            unit.setPurchaseReferenceId(referenceId);
            long unitId = productUnitRepository.insertProductUnit(unit);

            InventoryStockMovement movement = new InventoryStockMovement();
            movement.setCompanyId(request.getCompanyId());
            movement.setBranchId((long) request.getBranchId());
            movement.setProductId(product.productId());
            movement.setProductUnitId(unitId);
            movement.setMovementType(InventoryMovementType.STOCK_IN);
            movement.setQuantityDelta(BigDecimal.ONE);
            movement.setReferenceType("PRODUCT_RECEIPT");
            movement.setReferenceId(referenceId);
            movement.setSupplierId((long) request.getReceipt().getSupplierId());
            movement.setActorName(actorName);
            movement.setIdempotencyKey(limit(request.getIdempotencyKey() + ":" + unit.getUnitIdentifier(), 160));
            stockMovementRepository.insertMovement(movement);
        }
    }

    private void validateCategory(int companyId, int branchId, ProductReceiptProductRequest product) {
        String categoryName = firstNonBlank(product.getCategoryName(), product.getCategoryId() == null ? null : String.valueOf(product.getCategoryId()));
        if (categoryName == null) {
            return;
        }
        List<CustomPair> categories = categoryService.getCategoriesJson(companyId, branchId);
        CustomPair matched = findCategory(categories, categoryName);
        String subcategoryName = product.getSubcategoryName();

        if (matched == null && product.getCategoryId() == null && subcategoryName != null && !subcategoryName.isBlank()) {
            matched = findCategory(categories, subcategoryName);
            if (matched != null) {
                return;
            }
        }

        if (matched == null && product.getCategoryId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_INVALID", "Category does not exist for this branch");
        }
        if (matched != null && subcategoryName != null && !subcategoryName.isBlank()) {
            boolean found = matched.getValue() != null && matched.getValue().stream().anyMatch(value -> subcategoryName.equalsIgnoreCase(value));
            if (!found) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_TEMPLATE_COMBINATION_INVALID", "Subcategory does not belong to the selected category");
            }
        }
    }

    private CustomPair findCategory(List<CustomPair> categories, String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }

        return categories.stream()
                .filter(pair -> pair.getKey() != null && categoryName.trim().equalsIgnoreCase(pair.getKey()))
                .findFirst()
                .orElse(null);
    }

    private Product toProduct(ProductReceiptProductRequest request, int supplierId) {
        Product product = new Product();
        product.setProductName(limit(request.getProductName().trim(), 30));
        product.setBuyingDay(new Timestamp(System.currentTimeMillis()));
        product.setActivationPeriod("0");
        product.setBPrice(moneyToInt(request.getBuyingPrice()));
        product.setLPrice(moneyToInt(request.getLowestPrice()));
        product.setRPrice(moneyToInt(request.getRetailPrice()));
        product.setCompanyName(limit(firstNonBlank(request.getSku(), request.getProductName()), 30));
        product.setType(limit(firstNonBlank(request.getSubcategoryName(), request.getSubcategoryId() == null ? "default" : String.valueOf(request.getSubcategoryId())), 15));
        product.setSerial(firstNonBlank(request.getBarcode(), request.getSku()));
        product.setDesc("");
        product.setBatteryLife(0);
        product.setQuantity(0);
        product.setPState("New");
        product.setSupplierId(supplierId);
        product.setMajor(limit(firstNonBlank(request.getCategoryName(), request.getCategoryId() == null ? normalizeBusinessLine(request.getBusinessLineKey()) : String.valueOf(request.getCategoryId())), 30));
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
                                                 FinancePostingRequestItem financeRequest) {
        return new ProductReceiptResponse(
                operationId,
                false,
                new ProductReceiptResponse.ProductSummary(product.productId(), product.productName(), product.sku(), product.barcode(), product.trackingType().name()),
                new ProductReceiptResponse.ReceiptSummary(legacyTransactionId, request.getBranchId(), request.getReceipt().getQuantity(), balance.previousQuantity(), balance.newQuantity(), request.getReceipt().getSupplierId(), totalCost, paidAmount, remainingAmount),
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
