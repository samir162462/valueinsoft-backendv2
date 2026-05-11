package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.dto.AiActionDto;
import com.example.valueinsoftbackend.ai.dto.AiChatRequest;
import com.example.valueinsoftbackend.ai.dto.AiSourceDto;
import com.example.valueinsoftbackend.ai.dto.AiToolCallDto;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.rag.AiKnowledgeSearchResult;
import com.example.valueinsoftbackend.ai.rag.AiKnowledgeSearchService;
import com.example.valueinsoftbackend.ai.sql.AiSqlAgentService;
import com.example.valueinsoftbackend.ai.sql.AiSqlValidationException;
import com.example.valueinsoftbackend.ai.tools.AiToolDateRange;
import com.example.valueinsoftbackend.ai.tools.CustomerAiDto;
import com.example.valueinsoftbackend.ai.tools.CustomerAiTools;
import com.example.valueinsoftbackend.ai.tools.CustomerBalanceAiDto;
import com.example.valueinsoftbackend.ai.tools.CustomerOrderAiDto;
import com.example.valueinsoftbackend.ai.tools.InventoryAiProductDto;
import com.example.valueinsoftbackend.ai.tools.InventoryAiTools;
import com.example.valueinsoftbackend.ai.tools.PaymentBreakdownDto;
import com.example.valueinsoftbackend.ai.tools.SalesAiCashierDto;
import com.example.valueinsoftbackend.ai.tools.SalesAiSummaryDto;
import com.example.valueinsoftbackend.ai.tools.SalesAiTools;
import com.example.valueinsoftbackend.ai.tools.SalesAiTopProductDto;
import com.example.valueinsoftbackend.ai.tools.ShiftAiSummaryDto;
import com.example.valueinsoftbackend.ai.tools.ShiftAiTools;
import com.example.valueinsoftbackend.ai.tools.SupplierAiDto;
import com.example.valueinsoftbackend.ai.tools.SupplierAiTools;
import com.example.valueinsoftbackend.ai.tools.SupplierInvoiceAiDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AiChatOrchestratorService {

    private final AiModelClient aiModelClient;
    private final AiPromptPolicyService promptPolicyService;
    private final AiResponseSanitizerService sanitizerService;
    private final AiKnowledgeSearchService knowledgeSearchService;
    private final AiSqlAgentService sqlAgentService;
    private final AiProperties aiProperties;
    private final InventoryAiTools inventoryAiTools;
    private final SalesAiTools salesAiTools;
    private final ShiftAiTools shiftAiTools;
    private final SupplierAiTools supplierAiTools;
    private final CustomerAiTools customerAiTools;

    public AiChatOrchestratorService(AiModelClient aiModelClient,
                                     AiPromptPolicyService promptPolicyService,
                                     AiResponseSanitizerService sanitizerService,
                                     AiKnowledgeSearchService knowledgeSearchService,
                                     AiSqlAgentService sqlAgentService,
                                     AiProperties aiProperties,
                                     InventoryAiTools inventoryAiTools,
                                     SalesAiTools salesAiTools,
                                     ShiftAiTools shiftAiTools,
                                     SupplierAiTools supplierAiTools,
                                     CustomerAiTools customerAiTools) {
        this.aiModelClient = aiModelClient;
        this.promptPolicyService = promptPolicyService;
        this.sanitizerService = sanitizerService;
        this.knowledgeSearchService = knowledgeSearchService;
        this.sqlAgentService = sqlAgentService;
        this.aiProperties = aiProperties;
        this.inventoryAiTools = inventoryAiTools;
        this.salesAiTools = salesAiTools;
        this.shiftAiTools = shiftAiTools;
        this.supplierAiTools = supplierAiTools;
        this.customerAiTools = customerAiTools;
    }

    public OrchestratedChatResult answer(AiChatRequest request,
                                         String normalizedMode,
                                         AiSecurityContext securityContext,
                                         UUID conversationId,
                                         String conversationContext) {
        if (promptPolicyService.isUnsafeRequest(request.message())) {
            return new OrchestratedChatResult(
                    "I cannot help with SQL, internal prompts, secrets, tokens, schemas, or infrastructure details.",
                    List.of("How do I add a product?", "How do I print a receipt?"),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        if (!"HELP".equals(normalizedMode)) {
            if (shouldUseSqlAgent(normalizedMode, request.message())) {
                Optional<OrchestratedChatResult> sqlAnswer = answerWithSqlAgent(request, securityContext, conversationId, conversationContext);
                if (sqlAnswer.isPresent()) {
                    return sqlAnswer.get();
                }
            }

            if (("INVENTORY".equals(normalizedMode) || "BUSINESS".equals(normalizedMode))
                    && isInventoryIntent(request.message())) {
                return answerInventory(request, securityContext, conversationId);
            }
            if (("SALES".equals(normalizedMode) || "BUSINESS".equals(normalizedMode))
                    && isSalesIntent(request.message())) {
                return answerSales(request, securityContext, conversationId);
            }
            if (("SHIFT".equals(normalizedMode) || "BUSINESS".equals(normalizedMode))
                    && isShiftIntent(request.message())) {
                return answerShift(request, securityContext, conversationId);
            }
            if (("SUPPLIERS".equals(normalizedMode) || "BUSINESS".equals(normalizedMode))
                    && isSupplierIntent(request.message())) {
                return answerSupplier(request, securityContext, conversationId);
            }
            if (("CUSTOMERS".equals(normalizedMode) || "BUSINESS".equals(normalizedMode))
                    && isCustomerIntent(request.message())) {
                return answerCustomer(request, securityContext, conversationId);
            }
            if (!promptPolicyService.requiresBusinessData(request.message())) {
                return answerWithModel(request, normalizedMode, conversationContext);
            }
            return new OrchestratedChatResult(
                    "This phase supports HELP mode and read-only Inventory, Sales, Shift, Supplier, and Customer tools only. Other business tools are not enabled yet.",
                    List.of("What are today's sales?", "Show supplier balance", "Search customer"),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        if (promptPolicyService.requiresBusinessData(request.message())) {
            return new OrchestratedChatResult(
                    "Business data tools are not enabled in HELP mode. Switch to Inventory mode for read-only inventory questions.",
                    List.of("How do I add a product?", "How do I use POS?", "How do I open or close a shift?"),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        if (!aiProperties.isRagEnabled()) {
            AiModelResponse modelResponse = generateWithTiming(new AiModelRequest(
                  promptPolicyService.systemPrompt(),
                  request.message(),
                  normalizedMode,
                  "",
                  conversationContext
          ));
            return new OrchestratedChatResult(
                    sanitizerService.sanitize(modelResponse.answer()),
                    List.of("How do I add a product?", "How do I import products?", "How do I print a receipt?"),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        long retrievalStartedAt = System.nanoTime();
        List<AiKnowledgeSearchResult> knowledgeResults = knowledgeSearchService.search(securityContext.companyId(), request.message(), 3);
        log.debug("AI retrieval durationMs={} resultCount={}", elapsedMs(retrievalStartedAt), knowledgeResults.size());
        if (knowledgeResults.isEmpty()) {
            return answerWithModel(request, normalizedMode, conversationContext);
        }

        String knowledgeContext = buildKnowledgeContext(knowledgeResults);
        AiModelResponse modelResponse = generateWithTiming(new AiModelRequest(
              promptPolicyService.systemPrompt(),
              request.message(),
              normalizedMode,
              knowledgeContext,
              conversationContext
      ));

        return new OrchestratedChatResult(
                sanitizerService.sanitize(modelResponse.answer()),
                List.of("How do I add a product?", "How do I import products?", "How do I print a receipt?"),
                List.of(),
                sourcesFrom(knowledgeResults),
                List.of()
        );
    }

    private OrchestratedChatResult answerSales(AiChatRequest request,
                                               AiSecurityContext securityContext,
                                               UUID conversationId) {
        Long branchId = request.branchId();
        if (branchId == null) {
            return branchRequiredResult("sales");
        }
        String message = normalize(request.message());
        Optional<AiToolDateRange> range = dateRangeFrom(message);
        if (range.isEmpty()) {
            return invalidDateRangeResult();
        }

        if (message.contains("top selling") || message.contains("best selling")) {
            List<SalesAiTopProductDto> products = salesAiTools.getTopSellingProducts(
                    securityContext, conversationId, branchId, range.get(), extractLimit(message));
            return toolResult("getTopSellingProducts", formatTopProducts(products), products.size(), salesSuggestions(), salesReportActions(branchId, range.get()));
        }

        if (message.contains("cashier")) {
            List<SalesAiCashierDto> rows = salesAiTools.getSalesByCashier(
                    securityContext, conversationId, branchId, range.get());
            return toolResult("getSalesByCashier", formatCashiers(rows), rows.size(), salesSuggestions());
        }

        if (message.contains("payment breakdown") || message.contains("payment")) {
            List<PaymentBreakdownDto> rows = salesAiTools.getPaymentBreakdown(
                    securityContext, conversationId, branchId, range.get());
            return toolResult("getPaymentBreakdown", formatPaymentBreakdown(rows), rows.size(), salesSuggestions(), salesReportActions(branchId, range.get()));
        }

        SalesAiSummaryDto summary = message.contains("today")
                ? salesAiTools.getTodaySalesSummary(securityContext, conversationId, branchId)
                : salesAiTools.getSalesSummaryByDateRange(securityContext, conversationId, branchId, range.get());
        return toolResult("getSalesSummaryByDateRange", formatSalesSummary(summary), 1, salesSuggestions(), salesReportActions(branchId, range.get()));
    }

    private OrchestratedChatResult answerShift(AiChatRequest request,
                                               AiSecurityContext securityContext,
                                               UUID conversationId) {
        Long branchId = request.branchId();
        if (branchId == null) {
            return branchRequiredResult("shift");
        }
        String message = normalize(request.message());

        if (message.contains("payment breakdown") || message.contains("payment")) {
            Optional<AiToolDateRange> range = dateRangeFrom(message);
            if (range.isEmpty()) {
                return invalidDateRangeResult();
            }
            List<PaymentBreakdownDto> rows = shiftAiTools.getPaymentBreakdown(
                    securityContext, conversationId, branchId, range.get());
            return toolResult("getPaymentBreakdown", formatPaymentBreakdown(rows), rows.size(), shiftSuggestions(), salesReportActions(branchId, range.get()));
        }

        Optional<ShiftAiSummaryDto> shift = message.contains("open shift")
                ? shiftAiTools.getOpenShiftStatus(securityContext, conversationId, branchId)
                : shiftAiTools.getCurrentShiftSummary(securityContext, conversationId, branchId);
        return toolResult(
                message.contains("open shift") ? "getOpenShiftStatus" : "getCurrentShiftSummary",
                shift.map(this::formatShiftSummary).orElse("No open shift was found for this branch."),
                shift.isPresent() ? 1 : 0,
                shiftSuggestions(),
                shiftActions(branchId)
        );
    }

    private OrchestratedChatResult answerSupplier(AiChatRequest request,
                                                  AiSecurityContext securityContext,
                                                  UUID conversationId) {
        Long branchId = request.branchId();
        if (branchId == null) {
            return branchRequiredResult("supplier");
        }
        String message = normalize(request.message());
        String supplierName = extractNamedSubject(message, "supplier");

        if (message.contains("top") || message.contains("payable")) {
            List<SupplierAiDto> suppliers = supplierAiTools.getTopSuppliersByPayable(
                    securityContext, conversationId, branchId, extractLimit(message));
            return toolResult("getTopSuppliersByPayable", formatSuppliers(suppliers), suppliers.size(), supplierSuggestions());
        }

        if (message.contains("pending") || message.contains("invoice")) {
            if (supplierName.isBlank()) {
                return toolResult("getPendingSupplierInvoices", "Supplier name is required for pending supplier invoices.", 0, supplierSuggestions());
            }
            List<SupplierInvoiceAiDto> invoices = supplierAiTools.getPendingSupplierInvoices(
                    securityContext, conversationId, branchId, supplierName, extractLimit(message));
            return toolResult("getPendingSupplierInvoices", formatSupplierInvoices(invoices), invoices.size(), supplierSuggestions());
        }

        if (supplierName.isBlank()) {
            return toolResult("getSupplierBalance", "Supplier name is required for supplier balance.", 0, supplierSuggestions());
        }
        Optional<SupplierAiDto> supplier = supplierAiTools.getSupplierBalance(
                securityContext, conversationId, branchId, supplierName);
        return toolResult(
                "getSupplierBalance",
                supplier.map(this::formatSupplierBalance).orElse("No matching supplier was found."),
                supplier.isPresent() ? 1 : 0,
                supplierSuggestions(),
                supplier.map(value -> supplierActions(branchId, value.supplierId())).orElse(List.of())
        );
    }

    private OrchestratedChatResult answerCustomer(AiChatRequest request,
                                                  AiSecurityContext securityContext,
                                                  UUID conversationId) {
        Long branchId = request.branchId();
        if (branchId == null) {
            return branchRequiredResult("customer");
        }
        String message = normalize(request.message());
        Optional<Long> customerId = extractCustomerId(message);

        if (message.contains("balance")) {
            if (customerId.isEmpty()) {
                return toolResult("getCustomerBalance", "Customer ID is required for customer balance.", 0, customerSuggestions());
            }
            Optional<CustomerBalanceAiDto> balance = customerAiTools.getCustomerBalance(
                    securityContext, conversationId, branchId, customerId.get());
            return toolResult(
                    "getCustomerBalance",
                    balance.map(this::formatCustomerBalance).orElse("No matching customer was found."),
                    balance.isPresent() ? 1 : 0,
                    customerSuggestions(),
                    balance.map(value -> customerActions(branchId, value.customerId())).orElse(List.of())
            );
        }

        if (message.contains("last order") || message.contains("last orders") || message.contains("orders")) {
            if (customerId.isEmpty()) {
                return toolResult("getCustomerLastOrders", "Customer ID is required for customer last orders.", 0, customerSuggestions());
            }
            List<CustomerOrderAiDto> orders = customerAiTools.getCustomerLastOrders(
                    securityContext, conversationId, branchId, customerId.get(), extractLimit(message));
            return toolResult("getCustomerLastOrders", formatCustomerOrders(orders), orders.size(), customerSuggestions());
        }

        String query = extractNamedSubject(message, "customer");
        if (query.isBlank()) {
            query = message.replace("search customer", "").replace("find customer", "").trim();
        }
        if (query.isBlank()) {
            return toolResult("searchCustomer", "Customer name or phone is required for search.", 0, customerSuggestions());
        }
        List<CustomerAiDto> customers = customerAiTools.searchCustomer(securityContext, conversationId, branchId, query);
        return toolResult("searchCustomer", formatCustomers(customers), customers.size(), customerSuggestions());
    }

    private OrchestratedChatResult answerInventory(AiChatRequest request,
                                                   AiSecurityContext securityContext,
                                                   UUID conversationId) {
        Long branchId = request.branchId();
        if (branchId == null) {
            return new OrchestratedChatResult(
                    "Branch is required for inventory questions.",
                    List.of("Show low stock products", "Search product by name", "Check product stock by barcode"),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        String message = normalize(request.message());
        if (isInventoryCountIntent(message)) {
            long count = inventoryAiTools.countProductsInStock(securityContext, conversationId, branchId);
            return inventoryResult(
                    "countProductsInStock",
                    "This branch currently has " + count + " product(s) with stock on hand.",
                    1
            );
        }

        if (message.contains("low stock")) {
            List<InventoryAiProductDto> products = inventoryAiTools.getLowStockProducts(
                    securityContext,
                    conversationId,
                    branchId,
                    extractLimit(message)
            );
            return inventoryResult(
                    "getLowStockProducts",
                    products.isEmpty()
                            ? "No low stock products were found for this branch."
                            : "Low stock products:\n" + formatProducts(products),
                    products.size(),
                    lowStockActions(branchId)
            );
        }

        Optional<String> barcode = extractAfterKeyword(message, "barcode");
        if (barcode.isPresent()) {
            Optional<InventoryAiProductDto> product = inventoryAiTools.getProductByBarcode(
                    securityContext,
                    conversationId,
                    branchId,
                    barcode.get()
            );
            return inventoryResult(
                    "getProductByBarcode",
                    product.map(value -> "Product found:\n" + formatProduct(value))
                            .orElse("No product was found for that barcode."),
                    product.isPresent() ? 1 : 0,
                    product.map(value -> productActions(branchId, value.productId())).orElse(List.of())
            );
        }

        Optional<Long> productId = extractProductId(message);
        if (productId.isPresent() && message.contains("stock")) {
            Optional<InventoryAiProductDto> product = inventoryAiTools.getProductStock(
                    securityContext,
                    conversationId,
                    branchId,
                    productId.get()
            );
            return inventoryResult(
                    "getProductStock",
                    product.map(value -> "Product stock:\n" + formatProduct(value))
                            .orElse("No product stock was found for that product."),
                    product.isPresent() ? 1 : 0,
                    product.map(value -> productActions(branchId, value.productId())).orElse(List.of())
            );
        }

        Optional<String> productName = extractProductName(message);
        if (productName.isPresent()) {
            List<InventoryAiProductDto> products = inventoryAiTools.searchProductByName(
                    securityContext,
                    conversationId,
                    branchId,
                    productName.get(),
                    extractLimit(message)
            );
            return inventoryResult(
                    "searchProductByName",
                    products.isEmpty()
                            ? "No matching products were found."
                            : "Matching products:\n" + formatProducts(products),
                    products.size()
            );
        }

        return new OrchestratedChatResult(
                "I can help with read-only inventory questions like low stock, product name search, barcode lookup, and product stock by product ID.",
                List.of("Show low stock products", "Search product iPhone", "Check barcode 123"),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private OrchestratedChatResult inventoryResult(String toolName, String answer, int count) {
        return inventoryResult(toolName, answer, count, List.of());
    }

    private OrchestratedChatResult inventoryResult(String toolName, String answer, int count, List<AiActionDto> actions) {
        return toolResult(
                toolName,
                answer,
                count,
                List.of("Show low stock products", "Search product by name", "Check product stock by barcode"),
                actions
        );
    }

    private String buildKnowledgeContext(List<AiKnowledgeSearchResult> results) {
        return results.stream()
                .map(result -> "%s: %s".formatted(result.chunk().title(), result.chunk().content()))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private List<AiSourceDto> sourcesFrom(List<AiKnowledgeSearchResult> results) {
        Map<String, AiSourceDto> sources = new LinkedHashMap<>();
        for (AiKnowledgeSearchResult result : results) {
            String title = result.chunk().title();
            sources.putIfAbsent(
                    title,
                    new AiSourceDto(title, "HELP_ARTICLE", result.chunk().documentId().toString())
            );
        }
        return List.copyOf(sources.values());
    }

    private OrchestratedChatResult answerWithModel(AiChatRequest request, String normalizedMode, String conversationContext) {
        AiModelResponse modelResponse = generateWithTiming(new AiModelRequest(
                promptPolicyService.systemPrompt(),
                request.message(),
                normalizedMode,
                "",
                conversationContext
        ));
        return new OrchestratedChatResult(
                sanitizerService.sanitize(modelResponse.answer()),
                List.of("How do I add a product?", "How do I use POS?", "How do I open or close a shift?"),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private boolean shouldUseSqlAgent(String normalizedMode, String message) {
        if (!aiProperties.isSqlAgentEnabled()) {
            return false;
        }
        boolean dataQuestion = promptPolicyService.requiresBusinessData(message);
        return switch (normalizedMode) {
            case "INVENTORY" -> dataQuestion || isInventoryIntent(message);
            case "SALES" -> dataQuestion || isSalesIntent(message);
            case "SHIFT" -> dataQuestion || isShiftIntent(message);
            case "SUPPLIERS" -> dataQuestion || isSupplierIntent(message);
            case "CUSTOMERS" -> dataQuestion || isCustomerIntent(message);
            case "BUSINESS" -> isInventoryIntent(message)
                    || isSalesIntent(message)
                    || isShiftIntent(message)
                    || isSupplierIntent(message)
                    || isCustomerIntent(message)
                    || dataQuestion;
            default -> false;
        };
    }

    private Optional<OrchestratedChatResult> answerWithSqlAgent(AiChatRequest request,
                                                               AiSecurityContext securityContext,
                                                               UUID conversationId,
                                                               String conversationContext) {
        try {
            AiSqlAgentService.AiSqlAnswer answer = sqlAgentService.answer(
                    securityContext,
                    conversationId,
                    request.branchId(),
                    request.message(),
                    conversationContext
            );
            return Optional.of(new OrchestratedChatResult(
                    sanitizerService.sanitize(answer.answer()),
                    List.of("How many products are in stock?", "Show low stock products", "Search product iPhone"),
                    List.of(),
                    List.of(),
                    List.of(new AiToolCallDto("aiSqlSelect", "SUCCESS", "Returned " + answer.rowCount() + " row(s)"))
            ));
        } catch (AiSqlValidationException exception) {
            log.warn("AI SQL validation failed for conversation {}: {}", conversationId, exception.getMessage());
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("AI SQL agent failed for conversation {}", conversationId, exception);
            return Optional.empty();
        }
    }

    private boolean isInventoryIntent(String message) {
        String normalized = normalize(message);
        return normalized.contains("inventory")
                || normalized.contains("stock")
                || normalized.contains("product")
                || normalized.contains("barcode")
                || normalized.contains("serial");
    }

    private boolean isInventoryCountIntent(String message) {
        String normalized = normalize(message);
        return (normalized.contains("how many")
                || normalized.contains("count")
                || normalized.contains("total")
                || normalized.contains("number of"))
                && (normalized.contains("product") || normalized.contains("inventory") || normalized.contains("stock"));
    }

    private boolean isSalesIntent(String message) {
        String normalized = normalize(message);
        return normalized.contains("sale")
                || normalized.contains("sold")
                || normalized.contains("selling")
                || normalized.contains("cashier")
                || normalized.contains("order")
                || normalized.contains("revenue")
                || normalized.contains("income")
                || normalized.contains("payment breakdown")
                || normalized.contains("top selling");
    }

    private boolean isShiftIntent(String message) {
        String normalized = normalize(message);
        return normalized.contains("shift")
                || normalized.contains("open shift")
                || normalized.contains("current shift")
                || normalized.contains("payment breakdown");
    }

    private boolean isSupplierIntent(String message) {
        String normalized = normalize(message);
        return normalized.contains("supplier")
                || normalized.contains("payable")
                || normalized.contains("pending invoice");
    }

    private boolean isCustomerIntent(String message) {
        String normalized = normalize(message);
        return normalized.contains("customer")
                || normalized.contains("client")
                || normalized.contains("last orders");
    }

    private String formatProducts(List<InventoryAiProductDto> products) {
        return products.stream()
                .map(this::formatProduct)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatProduct(InventoryAiProductDto product) {
        return "- %s (ID %d): qty %d, reserved %d, available %d, status %s%s".formatted(
                product.productName(),
                product.productId(),
                product.quantityOnHand(),
                product.reservedQuantity(),
                product.availableQuantity(),
                product.stockStatus(),
                product.barcode() == null || product.barcode().isBlank() ? "" : ", barcode " + product.barcode()
        );
    }

    private String formatSalesSummary(SalesAiSummaryDto summary) {
        return """
                Sales summary (%s to %s):
                - Orders: %d
                - Gross sales: %s
                - Discounts: %s
                - Refunds: %s
                - Net sales: %s
                - Income: %s
                """.formatted(
                summary.fromDate(),
                summary.toDate(),
                summary.orderCount(),
                summary.grossSales(),
                summary.discountTotal(),
                summary.refundTotal(),
                summary.netSales(),
                summary.incomeTotal()
        ).trim();
    }

    private String formatTopProducts(List<SalesAiTopProductDto> products) {
        if (products.isEmpty()) {
            return "No top selling products were found for that period.";
        }
        return "Top selling products:\n" + products.stream()
                .map(row -> "- %s (ID %d): qty %d, sales %s".formatted(
                        row.productName(), row.productId(), row.quantitySold(), row.salesTotal()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatCashiers(List<SalesAiCashierDto> rows) {
        if (rows.isEmpty()) {
            return "No cashier sales were found for that period.";
        }
        return "Sales by cashier:\n" + rows.stream()
                .map(row -> "- %s: %d orders, sales %s, income %s".formatted(
                        row.cashierName(), row.orderCount(), row.salesTotal(), row.incomeTotal()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatPaymentBreakdown(List<PaymentBreakdownDto> rows) {
        if (rows.isEmpty()) {
            return "No payment activity was found for that period.";
        }
        return "Payment breakdown:\n" + rows.stream()
                .map(row -> "- %s: %d transaction(s), total %s".formatted(
                        row.paymentType(), row.transactionCount(), row.totalAmount()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatShiftSummary(ShiftAiSummaryDto shift) {
        return """
                Current shift:
                - Shift ID: %d
                - Status: %s
                - Opened at: %s
                - Cashier: %s
                - Orders: %d
                - Gross sales: %s
                - Net sales: %s
                - Expected cash: %s
                - Opening float: %s
                """.formatted(
                shift.shiftId(),
                shift.status(),
                shift.openedAt(),
                blankToUnknown(shift.assignedCashier()),
                shift.orderCount(),
                shift.grossSales(),
                shift.netSales(),
                shift.expectedCash(),
                shift.openingFloat()
        ).trim();
    }

    private String formatSupplierBalance(SupplierAiDto supplier) {
        return "- %s (ID %d): payable balance %s, phone %s%s".formatted(
                supplier.supplierName(),
                supplier.supplierId(),
                supplier.balance(),
                supplier.maskedPhone(),
                supplier.major() == null || supplier.major().isBlank() ? "" : ", major " + supplier.major()
        );
    }

    private String formatSuppliers(List<SupplierAiDto> suppliers) {
        if (suppliers.isEmpty()) {
            return "No suppliers with payable balance were found.";
        }
        return "Top suppliers by payable:\n" + suppliers.stream()
                .map(this::formatSupplierBalance)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatSupplierInvoices(List<SupplierInvoiceAiDto> invoices) {
        if (invoices.isEmpty()) {
            return "No pending supplier invoices were found.";
        }
        return "Pending supplier invoices:\n" + invoices.stream()
                .map(row -> "- Document %d: product %d, qty %d, total %s, paid %s, remaining %s, date %s".formatted(
                        row.documentId(),
                        row.productId(),
                        row.quantity(),
                        row.totalCost(),
                        row.paidAmount(),
                        row.remainingAmount(),
                        row.createdAt()
                ))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatCustomers(List<CustomerAiDto> customers) {
        if (customers.isEmpty()) {
            return "No matching customers were found.";
        }
        return "Matching customers:\n" + customers.stream()
                .map(row -> "- %s (ID %d): phone %s, registered %s".formatted(
                        row.customerName(), row.customerId(), row.maskedPhone(), row.registeredAt()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatCustomerBalance(CustomerBalanceAiDto balance) {
        return "- %s (ID %d): order total %s, receipts %s, balance %s".formatted(
                balance.customerName(),
                balance.customerId(),
                balance.orderTotal(),
                balance.receiptTotal(),
                balance.balance()
        );
    }

    private String formatCustomerOrders(List<CustomerOrderAiDto> orders) {
        if (orders.isEmpty()) {
            return "No recent customer orders were found.";
        }
        return "Customer last orders:\n" + orders.stream()
                .map(row -> "- Order %d: %s, type %s, total %s, discount %s, net %s, cashier %s".formatted(
                        row.orderId(),
                        row.orderTime(),
                        blankToUnknown(row.orderType()),
                        row.orderTotal(),
                        row.orderDiscount(),
                        row.netTotal(),
                        blankToUnknown(row.salesUser())
                ))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private Optional<String> extractProductName(String message) {
        String cleaned = message
                .replace("search product by name", "")
                .replace("search product", "")
                .replace("find product by name", "")
                .replace("find product", "")
                .replace("product name", "")
                .replace("product", "")
                .trim();
        cleaned = cleaned.replaceAll("\\blimit\\s+\\d+\\b", "").trim();
        return cleaned.isBlank() ? Optional.empty() : Optional.of(cleaned);
    }

    private Optional<String> extractAfterKeyword(String message, String keyword) {
        int index = message.indexOf(keyword);
        if (index < 0) {
            return Optional.empty();
        }
        String value = message.substring(index + keyword.length()).replaceAll("[^a-z0-9._\\-]", " ").trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.split("\\s+")[0]);
    }

    private Optional<Long> extractProductId(String message) {
        Matcher matcher = Pattern.compile("\\b(?:product\\s+id|product)\\s+(\\d+)\\b").matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private Optional<Long> extractCustomerId(String message) {
        Matcher matcher = Pattern.compile("\\b(?:customer|client)\\s+(?:id\\s+)?(\\d+)\\b").matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String extractNamedSubject(String message, String keyword) {
        String cleaned = message
                .replace("show", "")
                .replace("search", "")
                .replace("find", "")
                .replace("balance", "")
                .replace("pending", "")
                .replace("invoices", "")
                .replace("invoice", "")
                .replace("last orders", "")
                .replace("last order", "")
                .replace(keyword, "")
                .replace("for", "")
                .replace("by name", "")
                .replaceAll("\\blimit\\s+\\d+\\b", "")
                .trim();
        return cleaned;
    }

    private Integer extractLimit(String message) {
        Matcher matcher = Pattern.compile("\\blimit\\s+(\\d+)\\b").matcher(message);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Optional<AiToolDateRange> dateRangeFrom(String message) {
        LocalDate today = LocalDate.now();
        try {
            Matcher explicit = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}).*?(\\d{4}-\\d{2}-\\d{2})").matcher(message);
            if (explicit.find()) {
                return Optional.of(new AiToolDateRange(LocalDate.parse(explicit.group(1)), LocalDate.parse(explicit.group(2))));
            }
            Matcher single = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b").matcher(message);
            if (single.find()) {
                LocalDate date = LocalDate.parse(single.group(1));
                return Optional.of(new AiToolDateRange(date, date));
            }
            if (message.contains("week")) {
                LocalDate start = today.with(DayOfWeek.MONDAY);
                return Optional.of(new AiToolDateRange(start, today));
            }
            return Optional.of(AiToolDateRange.today());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private OrchestratedChatResult toolResult(String toolName, String answer, int count, List<String> suggestions) {
        return toolResult(toolName, answer, count, suggestions, List.of());
    }

    private OrchestratedChatResult toolResult(String toolName, String answer, int count, List<String> suggestions, List<AiActionDto> actions) {
        return new OrchestratedChatResult(
                sanitizerService.sanitize(answer),
                suggestions,
                actions,
                List.of(),
                List.of(new AiToolCallDto(toolName, "SUCCESS", "Returned " + count + " row(s)"))
        );
    }

    private AiModelResponse generateWithTiming(AiModelRequest request) {
        long modelStartedAt = System.nanoTime();
        AiModelResponse response = aiModelClient.generate(request);
        log.debug("AI model durationMs={} model={} fallback={}", elapsedMs(modelStartedAt), response.modelName(), response.fallback());
        return response;
    }

    private List<AiActionDto> lowStockActions(Long branchId) {
        return List.of(routeAction("Open Low Stock Report", "/inventory/low-stock", Map.of("branchId", branchId)));
    }

    private List<AiActionDto> productActions(Long branchId, Long productId) {
        return List.of(new AiActionDto("Open Product", "OPEN_PRODUCT", "/inventory/product", Map.of(
                "branchId", branchId,
                "productId", productId
        )));
    }

    private List<AiActionDto> salesReportActions(Long branchId, AiToolDateRange range) {
        return List.of(new AiActionDto("Open Sales Report", "FILTER_REPORT", "/sales/report", Map.of(
                "branchId", branchId,
                "fromDate", range.fromDate().toString(),
                "toDate", range.toDate().toString()
        )));
    }

    private List<AiActionDto> shiftActions(Long branchId) {
        return List.of(routeAction("Open POS Shift", "/pos/shift", Map.of("branchId", branchId)));
    }

    private List<AiActionDto> supplierActions(Long branchId, Long supplierId) {
        return List.of(new AiActionDto("Open Supplier", "OPEN_SUPPLIER", "/suppliers/profile", Map.of(
                "branchId", branchId,
                "supplierId", supplierId
        )));
    }

    private List<AiActionDto> customerActions(Long branchId, Long customerId) {
        return List.of(new AiActionDto("Open Customer", "OPEN_CUSTOMER", "/customers/profile", Map.of(
                "branchId", branchId,
                "customerId", customerId
        )));
    }

    private AiActionDto routeAction(String label, String route, Map<String, Object> params) {
        return new AiActionDto(label, "ROUTE", route, params);
    }

    private OrchestratedChatResult branchRequiredResult(String area) {
        return new OrchestratedChatResult(
                "Branch is required for " + area + " questions.",
                List.of("What are today's sales?", "Current shift summary", "Payment breakdown today"),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private OrchestratedChatResult invalidDateRangeResult() {
        return new OrchestratedChatResult(
                "Use a valid date range of 31 days or less.",
                List.of("What are today's sales?", "Top selling products this week", "Payment breakdown today"),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private List<String> salesSuggestions() {
        return List.of("What are today's sales?", "Top selling products this week", "Sales by cashier today");
    }

    private List<String> shiftSuggestions() {
        return List.of("Current shift summary", "Open shift status", "Payment breakdown today");
    }

    private List<String> supplierSuggestions() {
        return List.of("Show supplier balance", "Pending supplier invoices", "Top suppliers by payable");
    }

    private List<String> customerSuggestions() {
        return List.of("Search customer", "Customer balance by ID", "Customer last orders by ID");
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().trim();
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    public record OrchestratedChatResult(
            String answer,
            List<String> suggestedQuestions,
            List<AiActionDto> actions,
            List<AiSourceDto> sources,
            List<AiToolCallDto> toolCalls
    ) {
    }
}
