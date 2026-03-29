package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DbPosOrder {

    private static final Logger log = LoggerFactory.getLogger(DbPosOrder.class);
    private static final Type ORDER_DETAILS_LIST_TYPE = new TypeToken<ArrayList<OrderDetails>>() {
    }.getType();

    private final JdbcTemplate jdbcTemplate;
    private final Gson gson = new Gson();

    public DbPosOrder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int addOrder(Order order, int companyId) {
        validateOrder(order, companyId);

        int branchId = order.getBranchId();
        Timestamp orderTime = new Timestamp(System.currentTimeMillis());
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String sql = "INSERT INTO " + orderTable + " (" +
                "\"orderTime\", \"clientName\", \"orderType\", \"orderDiscount\", \"orderTotal\", \"salesUser\", \"clientId\", \"orderIncome\", \"orderBouncedBack\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING \"orderId\"";

        Integer orderId = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                orderTime,
                order.getClientName(),
                order.getOrderType(),
                order.getOrderDiscount(),
                order.getOrderTotal(),
                order.getSalesUser(),
                order.getClientId(),
                order.getOrderIncome(),
                0
        );

        if (orderId == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_INSERT_FAILED", "Order was not saved");
        }

        insertOrderDetails(orderId, order, companyId);
        updateProductQuantities(order, companyId);
        insertSoldInventoryTransactions(order, companyId, orderTime);

        log.debug("Inserted order {} for company {} branch {}", orderId, companyId, branchId);
        return orderId;
    }

    public ArrayList<Order> getOrdersByPeriod(int branchId, Timestamp startTime, Timestamp endTime, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }

        String sql = buildOrdersWithDetailsSelect(companyId, branchId) +
                " WHERE ord.\"orderTime\" BETWEEN ? AND ? ORDER BY ord.\"orderId\" DESC";
        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> mapOrder(rs, branchId), startTime, endTime));
    }

    public ArrayList<Order> getOrdersByShiftId(int companyId, int branchId, int spId) {
        TenantSqlIdentifiers.requirePositive(spId, "spId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");

        String shiftTable = TenantSqlIdentifiers.shiftPeriodTable(companyId);
        String sql = "WITH sales AS (" +
                " SELECT \"ShiftStartTime\", \"ShiftEndTime\" FROM " + shiftTable +
                " WHERE \"branchId\" = ? AND \"PosSOID\" = ?" +
                ") " +
                buildOrdersWithDetailsSelect(companyId, branchId) +
                " WHERE ord.\"orderTime\" BETWEEN (SELECT \"ShiftStartTime\" FROM sales) " +
                "AND COALESCE((SELECT \"ShiftEndTime\" FROM sales), CURRENT_TIMESTAMP) " +
                "ORDER BY ord.\"orderId\" DESC";

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> mapOrder(rs, branchId), branchId, spId));
    }

    public ArrayList<Order> getOrdersByClientId(int clientId, int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(clientId, "clientId");

        String sql = "SELECT \"orderId\", \"orderTime\", \"clientName\", \"orderType\", \"orderDiscount\", \"orderTotal\", " +
                "\"salesUser\", \"clientId\", \"orderIncome\", \"orderBouncedBack\" " +
                "FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " WHERE \"clientId\" = ? ORDER BY \"orderId\" DESC";

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> new Order(
                rs.getInt("orderId"),
                rs.getTimestamp("orderTime"),
                rs.getString("clientName"),
                rs.getString("orderType"),
                rs.getInt("orderDiscount"),
                rs.getInt("orderTotal"),
                rs.getString("salesUser"),
                branchId,
                rs.getInt("clientId"),
                rs.getInt("orderIncome"),
                rs.getInt("orderBouncedBack"),
                null
        ), clientId));
    }

    public ArrayList<OrderDetails> getOrdersDetailsByOrderId(int orderId, int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(orderId, "orderId");

        String sql = "SELECT \"orderDetailsId\", \"itemId\", \"itemName\", quantity, price, total, \"productId\", \"bouncedBack\" " +
                "FROM " + TenantSqlIdentifiers.orderDetailTable(companyId, branchId) +
                " WHERE \"orderId\" = ? ORDER BY \"orderDetailsId\" ASC";

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> new OrderDetails(
                rs.getInt("orderDetailsId"),
                rs.getInt("itemId"),
                rs.getString("itemName"),
                rs.getInt("quantity"),
                rs.getInt("price"),
                rs.getInt("total"),
                rs.getInt("productId"),
                rs.getInt("bouncedBack")
        ), orderId));
    }

    public OrderBounceBackContext getBounceBackContext(int odId, int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(odId, "odId");

        String detailTable = TenantSqlIdentifiers.orderDetailTable(companyId, branchId);
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String productTable = TenantSqlIdentifiers.productTable(companyId, branchId);
        String sql = "SELECT od.\"orderDetailsId\", od.\"orderId\", od.quantity, od.total, od.\"productId\", " +
                "COALESCE(od.\"bouncedBack\", 0) AS \"bouncedBack\", ord.\"salesUser\", COALESCE(ord.\"orderDiscount\", 0) AS \"orderDiscount\", " +
                "COALESCE(prod.\"bPrice\", 0) AS \"buyingPrice\", " +
                "EXISTS (" +
                " SELECT 1 FROM " + detailTable + " other " +
                " WHERE other.\"orderId\" = od.\"orderId\" AND other.\"orderDetailsId\" <> od.\"orderDetailsId\" AND COALESCE(other.\"bouncedBack\", 0) = 0" +
                ") AS \"hasOtherActiveItems\" " +
                "FROM " + detailTable + " od " +
                "JOIN " + orderTable + " ord ON ord.\"orderId\" = od.\"orderId\" " +
                "LEFT JOIN " + productTable + " prod ON prod.\"productId\" = od.\"productId\" " +
                "WHERE od.\"orderDetailsId\" = ?";

        List<OrderBounceBackContext> contexts = jdbcTemplate.query(sql, (rs, rowNum) -> new OrderBounceBackContext(
                rs.getInt("orderDetailsId"),
                rs.getInt("orderId"),
                rs.getInt("quantity"),
                rs.getInt("total"),
                rs.getInt("productId"),
                rs.getInt("bouncedBack"),
                rs.getString("salesUser"),
                rs.getInt("orderDiscount"),
                rs.getInt("buyingPrice"),
                rs.getBoolean("hasOtherActiveItems")
        ), odId);

        return contexts.isEmpty() ? null : contexts.get(0);
    }

    public int markOrderDetailBouncedBack(int odId, int branchId, int companyId, int toWho) {
        String sql = "UPDATE " + TenantSqlIdentifiers.orderDetailTable(companyId, branchId) +
                " SET \"bouncedBack\" = ? WHERE \"orderDetailsId\" = ? AND COALESCE(\"bouncedBack\", 0) = 0";
        return jdbcTemplate.update(sql, toWho, odId);
    }

    public int restoreInventoryQuantity(int productId, int quantity, int branchId, int companyId) {
        String sql = "UPDATE " + TenantSqlIdentifiers.productTable(companyId, branchId) +
                " SET quantity = quantity + ? WHERE \"productId\" = ?";
        return jdbcTemplate.update(sql, quantity, productId);
    }

    public void insertBounceBackInventoryTransaction(OrderBounceBackContext context, int branchId, int companyId) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId) +
                " (\"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)";
        jdbcTemplate.update(
                sql,
                context.getProductId(),
                context.getSalesUser(),
                0,
                "BounceBackInv",
                context.getQuantity(),
                context.getTotal(),
                "BounceBack",
                0
        );
    }

    public int updateOrderBounceBackTotals(int orderId, int branchId, int companyId, int bouncedAmount, int incomeReduction) {
        String sql = "UPDATE " + TenantSqlIdentifiers.orderTable(companyId, branchId) +
                " SET \"orderBouncedBack\" = COALESCE(\"orderBouncedBack\", 0) + ?, " +
                "\"orderIncome\" = COALESCE(\"orderIncome\", 0) - ? WHERE \"orderId\" = ?";
        return jdbcTemplate.update(sql, bouncedAmount, incomeReduction, orderId);
    }

    private void insertOrderDetails(int orderId, Order order, int companyId) {
        ArrayList<OrderDetails> details = order.getOrderDetails();
        String sql = "INSERT INTO " + TenantSqlIdentifiers.orderDetailTable(companyId, order.getBranchId()) +
                " (\"itemId\", \"itemName\", quantity, price, total, \"orderId\", \"productId\", \"bouncedBack\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                OrderDetails detail = details.get(i);
                ps.setInt(1, detail.getItemId());
                ps.setString(2, detail.getItemName());
                ps.setInt(3, detail.getQuantity());
                ps.setInt(4, detail.getPrice());
                ps.setInt(5, detail.getTotal());
                ps.setInt(6, orderId);
                ps.setInt(7, detail.getProductId());
            }

            @Override
            public int getBatchSize() {
                return details.size();
            }
        });
    }

    private void updateProductQuantities(Order order, int companyId) {
        String sql = "UPDATE " + TenantSqlIdentifiers.productTable(companyId, order.getBranchId()) +
                " SET quantity = quantity - ? WHERE \"productId\" = ?";

        for (OrderDetails detail : order.getOrderDetails()) {
            int updatedRows = jdbcTemplate.update(sql, detail.getQuantity(), detail.getProductId());
            if (updatedRows != 1) {
                throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found for order item " + detail.getProductId());
            }
        }
    }

    private void insertSoldInventoryTransactions(Order order, int companyId, Timestamp orderTime) {
        ArrayList<OrderDetails> details = order.getOrderDetails();
        String sql = "INSERT INTO " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, order.getBranchId()) +
                " (\"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                OrderDetails detail = details.get(i);
                ps.setInt(1, detail.getProductId());
                ps.setString(2, order.getSalesUser());
                ps.setInt(3, 0);
                ps.setString(4, "Sold");
                ps.setInt(5, detail.getQuantity() * -1);
                ps.setInt(6, detail.getTotal());
                ps.setString(7, "Sale");
                ps.setTimestamp(8, orderTime);
                ps.setInt(9, 0);
            }

            @Override
            public int getBatchSize() {
                return details.size();
            }
        });
    }

    private String buildOrdersWithDetailsSelect(int companyId, int branchId) {
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String detailTable = TenantSqlIdentifiers.orderDetailTable(companyId, branchId);
        return "SELECT ord.\"orderId\", ord.\"orderTime\", ord.\"clientName\", ord.\"orderType\", ord.\"orderDiscount\", ord.\"orderTotal\", " +
                "ord.\"salesUser\", ord.\"clientId\", ord.\"orderIncome\", ord.\"orderBouncedBack\", details.orderDetails " +
                "FROM " + orderTable + " ord " +
                "LEFT JOIN (" +
                " SELECT array_to_json(array_agg(json_build_object(" +
                "   'odId', detail.\"orderDetailsId\", " +
                "   'itemId', detail.\"itemId\", " +
                "   'itemName', detail.\"itemName\", " +
                "   'quantity', detail.quantity, " +
                "   'price', detail.price, " +
                "   'total', detail.total, " +
                "   'productId', detail.\"productId\", " +
                "   'bouncedBack', detail.\"bouncedBack\"" +
                " ))) AS orderDetails, detail.\"orderId\" AS order_id " +
                " FROM " + detailTable + " detail GROUP BY detail.\"orderId\"" +
                ") details ON details.order_id = ord.\"orderId\"";
    }

    private Order mapOrder(java.sql.ResultSet rs, int branchId) throws SQLException {
        return new Order(
                rs.getInt("orderId"),
                rs.getTimestamp("orderTime"),
                rs.getString("clientName"),
                rs.getString("orderType"),
                rs.getInt("orderDiscount"),
                rs.getInt("orderTotal"),
                rs.getString("salesUser"),
                branchId,
                rs.getInt("clientId"),
                rs.getInt("orderIncome"),
                rs.getInt("orderBouncedBack"),
                parseOrderDetails(rs.getString("orderDetails"))
        );
    }

    private ArrayList<OrderDetails> parseOrderDetails(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return new ArrayList<>();
        }
        ArrayList<OrderDetails> details = gson.fromJson(detailsJson, ORDER_DETAILS_LIST_TYPE);
        return details == null ? new ArrayList<>() : details;
    }

    private void validateOrder(Order order, int companyId) {
        if (order == null) {
            throw new IllegalArgumentException("order is required");
        }
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(order.getBranchId(), "branchId");
        if (order.getSalesUser() == null || order.getSalesUser().isBlank()) {
            throw new IllegalArgumentException("salesUser is required");
        }
        if (order.getOrderType() == null || order.getOrderType().isBlank()) {
            throw new IllegalArgumentException("orderType is required");
        }
        if (order.getOrderDetails() == null || order.getOrderDetails().isEmpty()) {
            throw new IllegalArgumentException("orderDetails must contain at least one item");
        }
    }

    public static final class OrderBounceBackContext {
        private final int orderDetailId;
        private final int orderId;
        private final int quantity;
        private final int total;
        private final int productId;
        private final int bouncedBack;
        private final String salesUser;
        private final int orderDiscount;
        private final int buyingPrice;
        private final boolean hasOtherActiveItems;

        public OrderBounceBackContext(int orderDetailId, int orderId, int quantity, int total, int productId,
                                      int bouncedBack, String salesUser, int orderDiscount, int buyingPrice,
                                      boolean hasOtherActiveItems) {
            this.orderDetailId = orderDetailId;
            this.orderId = orderId;
            this.quantity = quantity;
            this.total = total;
            this.productId = productId;
            this.bouncedBack = bouncedBack;
            this.salesUser = salesUser;
            this.orderDiscount = orderDiscount;
            this.buyingPrice = buyingPrice;
            this.hasOtherActiveItems = hasOtherActiveItems;
        }

        public int getOrderDetailId() {
            return orderDetailId;
        }

        public int getOrderId() {
            return orderId;
        }

        public int getQuantity() {
            return quantity;
        }

        public int getTotal() {
            return total;
        }

        public int getProductId() {
            return productId;
        }

        public int getBouncedBack() {
            return bouncedBack;
        }

        public String getSalesUser() {
            return salesUser;
        }

        public int getOrderDiscount() {
            return orderDiscount;
        }

        public int getBuyingPrice() {
            return buyingPrice;
        }

        public boolean hasOtherActiveItems() {
            return hasOtherActiveItems;
        }
    }
}
