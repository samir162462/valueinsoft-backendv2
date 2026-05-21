package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.sql.AiSqlAgentService;
import com.example.valueinsoftbackend.ai.tools.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiToolRegistry {

    private final InventoryAiTools inventoryAiTools;
    private final SalesAiTools salesAiTools;
    private final ShiftAiTools shiftAiTools;
    private final SupplierAiTools supplierAiTools;
    private final CustomerAiTools customerAiTools;
    private final AiSqlAgentService sqlAgentService;
    private final AiWorkflowTools workflowTools;
    private final Gson gson = new Gson();

    @FunctionalInterface
    public interface ToolListener {
        void onToolCall(String toolName, String inputJson);
    }

    public AiToolRegistry(InventoryAiTools inventoryAiTools,
                           SalesAiTools salesAiTools,
                           ShiftAiTools shiftAiTools,
                           SupplierAiTools supplierAiTools,
                           CustomerAiTools customerAiTools,
                           AiSqlAgentService sqlAgentService,
                           AiWorkflowTools workflowTools) {
        this.inventoryAiTools = inventoryAiTools;
        this.salesAiTools = salesAiTools;
        this.shiftAiTools = shiftAiTools;
        this.supplierAiTools = supplierAiTools;
        this.customerAiTools = customerAiTools;
        this.sqlAgentService = sqlAgentService;
        this.workflowTools = workflowTools;
    }

    private AiToolDefinition.ToolExecutor wrap(String toolName, ToolListener listener, AiToolDefinition.ToolExecutor original) {
        return input -> {
            if (listener != null) {
                if (input == null) {
                    input = "{}";
                }
                listener.onToolCall(toolName, input);
            }
            return original.execute(input);
        };
    }

    public List<ToolCallback> getTools(AiSecurityContext context, UUID conversationId) {
        return getTools(context, conversationId, null);
    }

    /**
     * Builds and returns a fresh list of tool callbacks captured within the request's
     * context (securityContext & conversationId), notifying the listener on execution.
     */
    public List<ToolCallback> getTools(AiSecurityContext context, UUID conversationId, ToolListener listener) {
        List<ToolCallback> tools = new ArrayList<>();

        // --- SALES TOOLS ---
        tools.add(new AiToolDefinition(
                "getTodaySalesSummary",
                "Get the total sales, gross, net, refund, discount, order count and income for today.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID to query sales summary for\"}},\"required\":[\"branchId\"]}",
                wrap("getTodaySalesSummary", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    SalesAiSummaryDto summary = salesAiTools.getTodaySalesSummary(context, conversationId, branchId);
                    return gson.toJson(summary);
                })
        ));

        tools.add(new AiToolDefinition(
                "getSalesSummaryByDateRange",
                "Get sales summary (total sales, gross, net, refund, discount, income) for a specific date range (maximum 31 days).",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"fromDate\":{\"type\":\"string\",\"description\":\"Start date in YYYY-MM-DD format\"},\"toDate\":{\"type\":\"string\",\"description\":\"End date in YYYY-MM-DD format\"}},\"required\":[\"branchId\",\"fromDate\",\"toDate\"]}",
                wrap("getSalesSummaryByDateRange", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    LocalDate from = LocalDate.parse(args.get("fromDate").getAsString());
                    LocalDate to = LocalDate.parse(args.get("toDate").getAsString());
                    AiToolDateRange range = new AiToolDateRange(from, to);
                    SalesAiSummaryDto summary = salesAiTools.getSalesSummaryByDateRange(context, conversationId, branchId, range);
                    return gson.toJson(summary);
                })
        ));

        tools.add(new AiToolDefinition(
                "getTopSellingProducts",
                "Get list of top selling products in a date range with quantities sold and total sales.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"fromDate\":{\"type\":\"string\",\"description\":\"Start date in YYYY-MM-DD format\"},\"toDate\":{\"type\":\"string\",\"description\":\"End date in YYYY-MM-DD format\"},\"limit\":{\"type\":\"integer\",\"description\":\"Optional limit of products (default 5)\"}},\"required\":[\"branchId\",\"fromDate\",\"toDate\"]}",
                wrap("getTopSellingProducts", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    LocalDate from = LocalDate.parse(args.get("fromDate").getAsString());
                    LocalDate to = LocalDate.parse(args.get("toDate").getAsString());
                    AiToolDateRange range = new AiToolDateRange(from, to);
                    Integer limit = args.has("limit") ? args.get("limit").getAsInt() : 5;
                    List<SalesAiTopProductDto> products = salesAiTools.getTopSellingProducts(context, conversationId, branchId, range, limit);
                    return gson.toJson(products);
                })
        ));

        tools.add(new AiToolDefinition(
                "getSalesByCashier",
                "Get sales and income contribution broken down by cashier in a date range.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"fromDate\":{\"type\":\"string\",\"description\":\"Start date in YYYY-MM-DD format\"},\"toDate\":{\"type\":\"string\",\"description\":\"End date in YYYY-MM-DD format\"}},\"required\":[\"branchId\",\"fromDate\",\"toDate\"]}",
                wrap("getSalesByCashier", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    LocalDate from = LocalDate.parse(args.get("fromDate").getAsString());
                    LocalDate to = LocalDate.parse(args.get("toDate").getAsString());
                    AiToolDateRange range = new AiToolDateRange(from, to);
                    List<SalesAiCashierDto> cashiers = salesAiTools.getSalesByCashier(context, conversationId, branchId, range);
                    return gson.toJson(cashiers);
                })
        ));

        tools.add(new AiToolDefinition(
                "getPaymentBreakdown",
                "Get breakdown of transactions by payment methods (Cash, Card, Wallet etc.) in a date range.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"fromDate\":{\"type\":\"string\",\"description\":\"Start date in YYYY-MM-DD format\"},\"toDate\":{\"type\":\"string\",\"description\":\"End date in YYYY-MM-DD format\"}},\"required\":[\"branchId\",\"fromDate\",\"toDate\"]}",
                wrap("getPaymentBreakdown", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    LocalDate from = LocalDate.parse(args.get("fromDate").getAsString());
                    LocalDate to = LocalDate.parse(args.get("toDate").getAsString());
                    AiToolDateRange range = new AiToolDateRange(from, to);
                    List<PaymentBreakdownDto> breakdown = salesAiTools.getPaymentBreakdown(context, conversationId, branchId, range);
                    return gson.toJson(breakdown);
                })
        ));

        // --- INVENTORY TOOLS ---
        tools.add(new AiToolDefinition(
                "getLowStockProducts",
                "Get products with low stock levels that need reordering.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"limit\":{\"type\":\"integer\",\"description\":\"Optional maximum products to return (default 10)\"}},\"required\":[\"branchId\"]}",
                wrap("getLowStockProducts", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    Integer limit = args.has("limit") ? args.get("limit").getAsInt() : 10;
                    List<InventoryAiProductDto> products = inventoryAiTools.getLowStockProducts(context, conversationId, branchId, limit);
                    return gson.toJson(products);
                })
        ));

        tools.add(new AiToolDefinition(
                "getProductByBarcode",
                "Find product stock details by its barcode.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"barcode\":{\"type\":\"string\",\"description\":\"The barcode of the product\"}},\"required\":[\"branchId\",\"barcode\"]}",
                wrap("getProductByBarcode", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    String barcode = args.get("barcode").getAsString();
                    return inventoryAiTools.getProductByBarcode(context, conversationId, branchId, barcode)
                            .map(gson::toJson)
                            .orElse("{\"status\":\"NOT_FOUND\",\"message\":\"No product found with barcode: " + barcode + "\"}");
                })
        ));

        tools.add(new AiToolDefinition(
                "getProductStock",
                "Get quantity on hand and availability for a specific product ID.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"productId\":{\"type\":\"integer\",\"description\":\"The unique ID of the product\"}},\"required\":[\"branchId\",\"productId\"]}",
                wrap("getProductStock", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    long productId = args.get("productId").getAsLong();
                    return inventoryAiTools.getProductStock(context, conversationId, branchId, productId)
                            .map(gson::toJson)
                            .orElse("{\"status\":\"NOT_FOUND\",\"message\":\"No product found with ID: " + productId + "\"}");
                })
        ));

        tools.add(new AiToolDefinition(
                "searchProductByName",
                "Search inventory products by matching a keyword against product names.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"name\":{\"type\":\"string\",\"description\":\"Product name search keyword\"},\"limit\":{\"type\":\"integer\",\"description\":\"Optional maximum matches to return (default 10)\"}},\"required\":[\"branchId\",\"name\"]}",
                wrap("searchProductByName", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    String name = args.get("name").getAsString();
                    Integer limit = args.has("limit") ? args.get("limit").getAsInt() : 10;
                    List<InventoryAiProductDto> products = inventoryAiTools.searchProductByName(context, conversationId, branchId, name, limit);
                    return gson.toJson(products);
                })
        ));

        // --- SHIFT TOOLS ---
        tools.add(new AiToolDefinition(
                "getCurrentShiftSummary",
                "Get sales summary, cashier, and start details for the current active shift.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"}},\"required\":[\"branchId\"]}",
                wrap("getCurrentShiftSummary", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    return shiftAiTools.getCurrentShiftSummary(context, conversationId, branchId)
                            .map(gson::toJson)
                            .orElse("{\"status\":\"NO_SHIFT\",\"message\":\"No current shift found for branch " + branchId + "\"}");
                })
        ));

        tools.add(new AiToolDefinition(
                "getOpenShiftStatus",
                "Check if there is an active open shift right now at the branch.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"}},\"required\":[\"branchId\"]}",
                wrap("getOpenShiftStatus", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    return shiftAiTools.getOpenShiftStatus(context, conversationId, branchId)
                            .map(gson::toJson)
                            .orElse("{\"status\":\"CLOSED\",\"message\":\"Shift is currently closed for branch " + branchId + "\"}");
                })
        ));

        // --- SUPPLIER TOOLS ---
        tools.add(new AiToolDefinition(
                "getTopSuppliersByPayable",
                "Get suppliers with highest payable balances that the business owes.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"limit\":{\"type\":\"integer\",\"description\":\"Optional limit of suppliers (default 5)\"}},\"required\":[\"branchId\"]}",
                wrap("getTopSuppliersByPayable", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    Integer limit = args.has("limit") ? args.get("limit").getAsInt() : 5;
                    List<SupplierAiDto> suppliers = supplierAiTools.getTopSuppliersByPayable(context, conversationId, branchId, limit);
                    return gson.toJson(suppliers);
                })
        ));

        tools.add(new AiToolDefinition(
                "getPendingSupplierInvoices",
                "Get list of pending unpaid invoices for a specific supplier by name.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"supplierName\":{\"type\":\"string\",\"description\":\"Supplier name\"},\"limit\":{\"type\":\"integer\",\"description\":\"Optional limit of invoices (default 5)\"}},\"required\":[\"branchId\",\"supplierName\"]}",
                wrap("getPendingSupplierInvoices", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    String supplierName = args.get("supplierName").getAsString();
                    Integer limit = args.has("limit") ? args.get("limit").getAsInt() : 5;
                    List<SupplierInvoiceAiDto> invoices = supplierAiTools.getPendingSupplierInvoices(context, conversationId, branchId, supplierName, limit);
                    return gson.toJson(invoices);
                })
        ));

        tools.add(new AiToolDefinition(
                "getSupplierBalance",
                "Get the balance and details of a single supplier by name.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"supplierName\":{\"type\":\"string\",\"description\":\"Supplier name\"}},\"required\":[\"branchId\",\"supplierName\"]}",
                wrap("getSupplierBalance", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    String supplierName = args.get("supplierName").getAsString();
                    return supplierAiTools.getSupplierBalance(context, conversationId, branchId, supplierName)
                            .map(gson::toJson)
                            .orElse("{\"status\":\"NOT_FOUND\",\"message\":\"Supplier not found: " + supplierName + "\"}");
                })
        ));

        // --- CUSTOMER TOOLS ---
        tools.add(new AiToolDefinition(
                "searchCustomer",
                "Search customers by matching a name or phone query.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"query\":{\"type\":\"string\",\"description\":\"Customer name or phone search query\"}},\"required\":[\"branchId\",\"query\"]}",
                wrap("searchCustomer", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    String query = args.get("query").getAsString();
                    List<CustomerAiDto> customers = customerAiTools.searchCustomer(context, conversationId, branchId, query);
                    return gson.toJson(customers);
                })
        ));

        tools.add(new AiToolDefinition(
                "getCustomerBalance",
                "Get outstanding balance and credit limit of a customer by ID.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"customerId\":{\"type\":\"integer\",\"description\":\"The Customer ID\"}},\"required\":[\"branchId\",\"customerId\"]}",
                wrap("getCustomerBalance", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    long customerId = args.get("customerId").getAsLong();
                    return customerAiTools.getCustomerBalance(context, conversationId, branchId, customerId)
                            .map(gson::toJson)
                            .orElse("{\"status\":\"NOT_FOUND\",\"message\":\"Customer balance not found for ID: " + customerId + "\"}");
                })
        ));

        tools.add(new AiToolDefinition(
                "getCustomerLastOrders",
                "Get the list of recent orders made by a customer.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"customerId\":{\"type\":\"integer\",\"description\":\"The Customer ID\"},\"limit\":{\"type\":\"integer\",\"description\":\"Optional order count limit (default 5)\"}},\"required\":[\"branchId\",\"customerId\"]}",
                wrap("getCustomerLastOrders", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    long customerId = args.get("customerId").getAsLong();
                    Integer limit = args.has("limit") ? args.get("limit").getAsInt() : 5;
                    List<CustomerOrderAiDto> orders = customerAiTools.getCustomerLastOrders(context, conversationId, branchId, customerId, limit);
                    return gson.toJson(orders);
                })
        ));

        // --- SQL AGENT TOOL ---
        tools.add(new AiToolDefinition(
                "executeSqlQuery",
                "Runs a safe, direct read-only SQL query on the business database to answer ad-hoc questions when standard tools are insufficient.",
                "{\"type\":\"object\",\"properties\":{\"branchId\":{\"type\":\"integer\",\"description\":\"The branch ID\"},\"sqlQuery\":{\"type\":\"string\",\"description\":\"The direct SELECT SQL statement to execute.\"}},\"required\":[\"branchId\",\"sqlQuery\"]}",
                wrap("executeSqlQuery", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    long branchId = args.get("branchId").getAsLong();
                    String query = args.get("sqlQuery").getAsString();
                    AiSqlAgentService.AiSqlAnswer sqlAnswer = sqlAgentService.answer(context, conversationId, branchId, query, "");
                    return gson.toJson(sqlAnswer);
                })
        ));

        // --- NAVIGATION TOOL ---
        tools.add(new AiToolDefinition(
                "navigateToScreen",
                "Triggers frontend navigation to a specific screen based on user command.",
                "{\"type\":\"object\",\"properties\":{\"screenName\":{\"type\":\"string\",\"description\":\"The screen to navigate to. Must be one of: 'POS' (Point of Sale), 'SALES_REPORT', 'INVENTORY', 'SUPPLIERS', 'CUSTOMERS', 'DASHBOARD'.\"}},\"required\":[\"screenName\"]}",
                wrap("navigateToScreen", listener, input -> {
                    JsonObject args = gson.fromJson(input, JsonObject.class);
                    String screen = args.get("screenName").getAsString().toUpperCase();
                    return "{\"status\":\"NAVIGATE\",\"screen\":\"" + screen + "\",\"message\":\"Successfully prepared navigation to screen " + screen + "\"}";
                })
        ));

        // --- WORKFLOW TOOLS ---
        tools.add(new AiToolDefinition(
                "getStepsToCreateInvoice",
                "Get the detailed, step-by-step guided instructions on how to create a sales invoice or checkout in the POS.",
                "{\"type\":\"object\",\"properties\":{}}",
                wrap("getStepsToCreateInvoice", listener, input -> {
                    return "{\"steps\":\"" + workflowTools.getStepsToCreateInvoice().replace("\n", "\\n").replace("\"", "\\\"") + "\"}";
                })
        ));

        tools.add(new AiToolDefinition(
                "getStepsToAddProduct",
                "Get the detailed, step-by-step guided instructions on how to add a new product to the inventory.",
                "{\"type\":\"object\",\"properties\":{}}",
                wrap("getStepsToAddProduct", listener, input -> {
                    return "{\"steps\":\"" + workflowTools.getStepsToAddProduct().replace("\n", "\\n").replace("\"", "\\\"") + "\"}";
                })
        ));

        tools.add(new AiToolDefinition(
                "getStepsToOpenShift",
                "Get the step-by-step guided instructions on how to open a cashier shift in the POS screen.",
                "{\"type\":\"object\",\"properties\":{}}",
                wrap("getStepsToOpenShift", listener, input -> {
                    return "{\"steps\":\"" + workflowTools.getStepsToOpenShift().replace("\n", "\\n").replace("\"", "\\\"") + "\"}";
                })
        ));

        tools.add(new AiToolDefinition(
                "getStepsToRunPayroll",
                "Get the step-by-step guided instructions on how to run, calculate and process staff payroll.",
                "{\"type\":\"object\",\"properties\":{}}",
                wrap("getStepsToRunPayroll", listener, input -> {
                    return "{\"steps\":\"" + workflowTools.getStepsToRunPayroll().replace("\n", "\\n").replace("\"", "\\\"") + "\"}";
                })
        ));

        return tools;
    }
}
