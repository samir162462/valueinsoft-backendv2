package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.Model.Inventory.InventoryMovementType;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.Model.Inventory.ProductTrackingMetadata;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnit;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@Slf4j
public class DbPosOrder {
    private static final Type ORDER_DETAILS_LIST_TYPE = new TypeToken<ArrayList<OrderDetails>>() {
    }.getType();

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DbInventoryProductTrackingRepository productTrackingRepository;
    private final DbInventoryProductUnitRepository productUnitRepository;
    private final DbInventoryStockMovementRepository stockMovementRepository;
    private final Gson gson = new Gson();
    private final boolean isH2;

    public DbPosOrder(JdbcTemplate jdbcTemplate,
                      DbInventoryProductTrackingRepository productTrackingRepository,
                      DbInventoryProductUnitRepository productUnitRepository,
                      DbInventoryStockMovementRepository stockMovementRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.productTrackingRepository = productTrackingRepository;
        this.productUnitRepository = productUnitRepository;
        this.stockMovementRepository = stockMovementRepository;

        boolean checkH2 = false;
        try (java.sql.Connection conn = java.util.Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            checkH2 = "H2".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName());
        } catch (Exception ignored) {
            // Default to false (production PG)
        }
        this.isH2 = checkH2;
    }

    @Transactional
    public com.example.valueinsoftbackend.Model.Response.CreateOrderResult addOrder(Order order, int companyId) {
        validateOrder(order, companyId);
        validateAndResolveSerializedSaleLines(order, companyId);

        int branchId = order.getBranchId();
        Timestamp orderTime = new Timestamp(System.currentTimeMillis());
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        
        if (order.getIdempotencyKey() != null && !order.getIdempotencyKey().isBlank()) {
            String lockScope = companyId + "-" + branchId + "-" + order.getIdempotencyKey();
            int lockId1 = lockScope.hashCode();
            int lockId2 = order.getIdempotencyKey().hashCode();
            jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + lockId1 + ", " + lockId2 + ")");

            String checkSql = "SELECT \"orderId\", \"orderTime\", shift_id, receipt_number FROM " + orderTable + " WHERE idempotency_key = ?";
            java.util.List<com.example.valueinsoftbackend.Model.Response.CreateOrderResult> existing = jdbcTemplate.query(checkSql, (rs, rowNum) -> new com.example.valueinsoftbackend.Model.Response.CreateOrderResult(
                    rs.getInt("orderId"),
                    rs.getString("receipt_number"),
                    true,
                    rs.getObject("shift_id") != null ? rs.getInt("shift_id") : null,
                    rs.getTimestamp("orderTime")
            ), order.getIdempotencyKey());

            if (!existing.isEmpty()) {
                log.info("Idempotency hit for company {} branch {} key {}", companyId, branchId, order.getIdempotencyKey());
                return existing.get(0);
            }
        }

        Integer shiftId = null;
        try {
            String shiftTable = TenantSqlIdentifiers.shiftPeriodTable(companyId);
            if (order.getRequestedShiftId() != null && order.getRequestedShiftId() > 0) {
                String shiftSql = "SELECT \"PosSOID\" FROM " + shiftTable + " WHERE \"PosSOID\" = ? AND \"branchId\" = ? AND status = 'OPEN' LIMIT 1";
                shiftId = jdbcTemplate.queryForObject(shiftSql, Integer.class, order.getRequestedShiftId(), branchId);
            } else {
                String shiftSql = "SELECT \"PosSOID\" FROM " + shiftTable + " WHERE \"branchId\" = ? AND status = 'OPEN' ORDER BY \"PosSOID\" DESC LIMIT 1";
                shiftId = jdbcTemplate.queryForObject(shiftSql, Integer.class, branchId);
            }
        } catch (Exception e) {
            log.warn("No open shift found for company {} branch {} when creating order. Continuing with null shift_id.", companyId, branchId);
        }

        String sequenceTable = TenantSqlIdentifiers.posReceiptSequencesTable(companyId);
        String yymm = java.time.ZonedDateTime.now(java.time.ZoneId.of("Africa/Cairo")).format(java.time.format.DateTimeFormatter.ofPattern("yyMM"));
        
        int sequenceNo;
        if (isH2) {
            String selectSql = "SELECT last_sequence_no FROM " + sequenceTable + " WHERE branch_id = ? AND period_yymm = ? FOR UPDATE";
            java.util.List<Integer> existingSeq = jdbcTemplate.query(selectSql, (rs, rowNum) -> rs.getInt(1), branchId, yymm);
            if (existingSeq.isEmpty()) {
                String insertSql = "INSERT INTO " + sequenceTable + " (branch_id, period_yymm, last_sequence_no) VALUES (?, ?, 1)";
                jdbcTemplate.update(insertSql, branchId, yymm);
                sequenceNo = 1;
            } else {
                sequenceNo = existingSeq.get(0) + 1;
                if (sequenceNo > 999999) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_LIMIT_EXCEEDED", "Monthly POS receipt limit exceeded for this branch.");
                }
                String updateSql = "UPDATE " + sequenceTable + " SET last_sequence_no = ?, updated_at = NOW() WHERE branch_id = ? AND period_yymm = ?";
                jdbcTemplate.update(updateSql, sequenceNo, branchId, yymm);
            }
        } else {
            String seqSql = "INSERT INTO " + sequenceTable + " (branch_id, period_yymm, last_sequence_no) " +
                    "VALUES (?, ?, 1) ON CONFLICT (branch_id, period_yymm) DO UPDATE SET " +
                    "last_sequence_no = " + sequenceTable + ".last_sequence_no + 1, updated_at = NOW() " +
                    "WHERE " + sequenceTable + ".last_sequence_no < 999999 RETURNING last_sequence_no";
            
            java.util.List<Integer> seqResults = jdbcTemplate.query(seqSql, (rs, rowNum) -> rs.getInt(1), branchId, yymm);
            if (seqResults.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_LIMIT_EXCEEDED", "Monthly POS receipt limit exceeded for this branch.");
            }
            sequenceNo = seqResults.get(0);
        }
        String branchCode = String.format(java.util.Locale.ROOT, "%04d", branchId);
        String receiptNumber = yymm + branchCode + String.format(java.util.Locale.ROOT, "%06d", sequenceNo);
        order.setReceiptNumber(receiptNumber);

        String sql = "INSERT INTO " + orderTable + " (" +
                "\"orderTime\", \"clientName\", \"orderType\", \"orderDiscount\", \"orderTotal\", \"salesUser\", \"clientId\", \"orderIncome\", \"orderBouncedBack\", shift_id, receipt_number, idempotency_key) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder orderKeyHolder = new GeneratedKeyHolder();
        Integer resolvedShiftId = shiftId;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"orderId"});
            ps.setTimestamp(1, orderTime);
            ps.setString(2, order.getClientName());
            ps.setString(3, order.getOrderType());
            ps.setInt(4, order.getOrderDiscount());
            ps.setInt(5, order.getOrderTotal());
            ps.setString(6, order.getSalesUser());
            ps.setInt(7, order.getClientId());
            ps.setInt(8, order.getOrderIncome());
            ps.setInt(9, 0);
            if (resolvedShiftId == null) ps.setObject(10, null); else ps.setInt(10, resolvedShiftId);
            ps.setString(11, receiptNumber);
            ps.setString(12, order.getIdempotencyKey());
            return ps;
        }, orderKeyHolder);

        Number orderKey = orderKeyHolder.getKey();
        Integer orderId = orderKey == null ? null : orderKey.intValue();

        if (orderId == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_INSERT_FAILED", "Order was not saved");
        }

        insertOrderDetails(orderId, order, companyId);
        markSerializedUnitsSold(orderId, order, companyId, orderTime);
        updateProductQuantities(order, companyId);
        insertSoldInventoryTransactions(order, companyId, orderTime);
        insertSoldLedgerEntries(orderId, order, companyId, orderTime);

        log.debug("Inserted order {} for company {} branch {}", orderId, companyId, branchId);
        return new com.example.valueinsoftbackend.Model.Response.CreateOrderResult(orderId, receiptNumber, false, shiftId, orderTime);
    }

    public List<OrderFinanceCostLine> getOrderFinanceCostLines(int orderId, int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(orderId, "orderId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");

        String sql = "SELECT od.\"productId\", od.quantity, COALESCE(prod.buying_price, 0) AS unit_cost, " +
                "(COALESCE(prod.buying_price, 0) * od.quantity) AS total_cost " +
                "FROM " + TenantSqlIdentifiers.orderDetailTable(companyId, branchId) + " od " +
                "LEFT JOIN " + TenantSqlIdentifiers.inventoryProductTable(companyId) + " prod ON prod.product_id = od.\"productId\" " +
                "WHERE od.\"orderId\" = ? " +
                "AND od.\"productId\" > 0 " +
                "AND COALESCE(od.\"bouncedBack\", 0) = 0 " +
                "ORDER BY od.\"orderDetailsId\" ASC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> new OrderFinanceCostLine(
                rs.getInt("productId"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_cost"),
                rs.getBigDecimal("total_cost")
        ), orderId);
    }

    public record OrderFinanceCostLine(int productId,
                                       int quantity,
                                       BigDecimal unitCost,
                                       BigDecimal totalCost) {}

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

        // Using direct shift_id linkage is more reliable than time-based BETWEEN
        String sql = buildOrdersWithDetailsSelect(companyId, branchId) +
                " WHERE ord.shift_id = ? ORDER BY ord.\"orderId\" DESC";

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> mapOrder(rs, branchId), spId));
    }

    public Order getOrderByReceiptNumber(int companyId, int branchId, String receiptNumber) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        if (receiptNumber == null || receiptNumber.isBlank()) {
            return null;
        }

        String sql = buildOrdersWithDetailsSelect(companyId, branchId) +
                " WHERE ord.receipt_number = ? LIMIT 1";

        List<Order> results = jdbcTemplate.query(sql, (rs, rowNum) -> mapOrder(rs, branchId), receiptNumber.trim());
        return results.isEmpty() ? null : results.get(0);
    }

    public ArrayList<Order> getOrdersByClientId(int clientId, int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(clientId, "clientId");

        String sql = "SELECT \"orderId\", \"orderTime\", \"clientName\", \"orderType\", \"orderDiscount\", \"orderTotal\", " +
                "\"salesUser\", \"clientId\", \"orderIncome\", \"orderBouncedBack\", receipt_number, idempotency_key " +
                "FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " WHERE \"clientId\" = ? ORDER BY \"orderId\" DESC";

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> {
            Order o = new Order(
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
            );
            o.setReceiptNumber(rs.getString("receipt_number"));
            o.setIdempotencyKey(rs.getString("idempotency_key"));
            return o;
        }, clientId));
    }

    public ArrayList<OrderDetails> getOrdersDetailsByOrderId(int orderId, int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(orderId, "orderId");

        String sql = "SELECT detail.\"orderDetailsId\", detail.\"itemId\", detail.\"itemName\", detail.quantity, detail.price, detail.total, " +
                "detail.\"productId\", detail.\"bouncedBack\", " +
                "COALESCE(serialized.product_unit_ids, ARRAY[]::bigint[]) AS product_unit_ids, " +
                "COALESCE(serialized.unit_identifiers, ARRAY[]::text[]) AS unit_identifiers " +
                "FROM " + TenantSqlIdentifiers.orderDetailTable(companyId, branchId) + " detail " +
                "LEFT JOIN LATERAL (" +
                " SELECT array_agg(unit.product_unit_id ORDER BY unit.product_unit_id) AS product_unit_ids, " +
                "        array_agg(unit.unit_identifier ORDER BY unit.product_unit_id) AS unit_identifiers " +
                " FROM " + TenantSqlIdentifiers.inventoryProductUnitTable(companyId) + " unit " +
                " WHERE unit.branch_id = ? AND unit.sale_order_detail_id = detail.\"orderDetailsId\"" +
                ") serialized ON true " +
                "WHERE detail.\"orderId\" = ? ORDER BY detail.\"orderDetailsId\" ASC";

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> new OrderDetails(
                rs.getInt("orderDetailsId"),
                rs.getInt("itemId"),
                rs.getString("itemName"),
                rs.getInt("quantity"),
                rs.getInt("price"),
                rs.getInt("total"),
                rs.getInt("productId"),
                rs.getInt("bouncedBack"),
                toLongList(rs.getArray("product_unit_ids")),
                toStringList(rs.getArray("unit_identifiers"))
        ), branchId, orderId));
    }

    public OrderBounceBackContext getBounceBackContext(int odId, int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(odId, "odId");

        String detailTable = TenantSqlIdentifiers.orderDetailTable(companyId, branchId);
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String sql = "SELECT od.\"orderDetailsId\", od.\"orderId\", od.quantity, od.total, od.\"productId\", " +
                "COALESCE(od.\"bouncedBack\", 0) AS \"bouncedBack\", ord.\"salesUser\", ord.\"clientId\", COALESCE(ord.\"orderDiscount\", 0) AS \"orderDiscount\", " +
                "COALESCE(ord.\"orderTotal\", 0) AS \"orderTotal\", " +
                "COALESCE(prod.buying_price, 0) AS \"buyingPrice\", " +
                "EXISTS (" +
                " SELECT 1 FROM " + detailTable + " other " +
                " WHERE other.\"orderId\" = od.\"orderId\" AND other.\"orderDetailsId\" <> od.\"orderDetailsId\" AND COALESCE(other.\"bouncedBack\", 0) = 0" +
                ") AS \"hasOtherActiveItems\" " +
                "FROM " + detailTable + " od " +
                "JOIN " + orderTable + " ord ON ord.\"orderId\" = od.\"orderId\" " +
                "LEFT JOIN " + TenantSqlIdentifiers.inventoryProductTable(companyId) + " prod ON prod.product_id = od.\"productId\" " +
                "WHERE od.\"orderDetailsId\" = ?";

        List<OrderBounceBackContext> contexts = jdbcTemplate.query(sql, (rs, rowNum) -> new OrderBounceBackContext(
                rs.getInt("orderDetailsId"),
                rs.getInt("orderId"),
                rs.getInt("quantity"),
                rs.getInt("total"),
                rs.getInt("productId"),
                rs.getInt("bouncedBack"),
                rs.getString("salesUser"),
                rs.getObject("clientId") != null ? rs.getInt("clientId") : null,
                rs.getInt("orderDiscount"),
                rs.getInt("orderTotal"),
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
        String sql = "UPDATE " + TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId) + " " +
                "SET quantity = quantity + ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE branch_id = ? AND product_id = ?";
        return jdbcTemplate.update(sql, quantity, branchId, productId);
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

    public Long insertBounceBackLedgerEntry(OrderBounceBackContext context, int branchId, int companyId) {
        String sql = """
                INSERT INTO %s (
                    branch_id, product_id, quantity_delta, movement_type, reference_type, reference_id,
                    actor_name, note, supplier_id, trans_total, pay_type, remaining_amount, created_at
                ) VALUES (
                    :branchId, :productId, :quantityDelta, :movementType, :referenceType, :referenceId,
                    :actorName, :note, :supplierId, :transTotal, :payType, :remainingAmount, CURRENT_TIMESTAMP
                )
                RETURNING stock_ledger_id
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(companyId));

        return namedParameterJdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("productId", context.getProductId())
                        .addValue("quantityDelta", context.getQuantity())
                        .addValue("movementType", "BOUNCE_BACK_IN")
                        .addValue("referenceType", "ORDER_BOUNCE_BACK")
                        .addValue("referenceId", String.valueOf(context.getOrderDetailId()))
                        .addValue("actorName", context.getSalesUser())
                        .addValue("note", "Restored stock from order bounce back")
                        .addValue("supplierId", 0)
                        .addValue("transTotal", context.getTotal())
                        .addValue("payType", "BounceBack")
                        .addValue("remainingAmount", 0),
                Long.class);
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

        for (OrderDetails detail : details) {
            KeyHolder detailKeyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, new String[]{"orderDetailsId"});
                ps.setInt(1, detail.getItemId());
                ps.setString(2, detail.getItemName());
                ps.setInt(3, detail.getQuantity());
                ps.setInt(4, detail.getPrice());
                ps.setInt(5, detail.getTotal());
                ps.setInt(6, orderId);
                ps.setInt(7, detail.getProductId());
                return ps;
            }, detailKeyHolder);

            Number detailKey = detailKeyHolder.getKey();
            Integer orderDetailsId = detailKey == null ? null : detailKey.intValue();
            if (orderDetailsId == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_DETAIL_INSERT_FAILED", "Order detail was not saved");
            }
            detail.setOdId(orderDetailsId);
        }
    }

    private void updateProductQuantities(Order order, int companyId) {
        String sql = "UPDATE " + TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId) + " " +
                "SET quantity = quantity - ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE branch_id = ? AND product_id = ? AND quantity >= ?";

        for (OrderDetails detail : order.getOrderDetails()) {
            if (detail.getProductId() <= 0) {
                continue; // Skip inventory updates for non-inventory items (like Repair Fees)
            }
            if (isSerializedProduct(companyId, detail.getProductId())) {
                continue;
            }
            int updatedRows = jdbcTemplate.update(
                    sql,
                    detail.getQuantity(),
                    order.getBranchId(),
                    detail.getProductId(),
                    detail.getQuantity()
            );
            if (updatedRows != 1) {
                log.warn("Product not found or not enough quantity for product {}. Expected: {}", detail.getProductId(), detail.getQuantity());
                throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found or low stock for item " + detail.getProductId());
            }
        }
    }

    private void insertSoldInventoryTransactions(Order order, int companyId, Timestamp orderTime) {
        ArrayList<OrderDetails> details = new ArrayList<>();
        for (OrderDetails d : order.getOrderDetails()) {
            if (d.getProductId() > 0) details.add(d);
        }
        if (details.isEmpty()) return;

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

    private void insertSoldLedgerEntries(int orderId, Order order, int companyId, Timestamp orderTime) {
        ArrayList<OrderDetails> details = new ArrayList<>();
        for (OrderDetails d : order.getOrderDetails()) {
            if (d.getProductId() > 0 && !isSerializedProduct(companyId, d.getProductId())) details.add(d);
        }
        if (details.isEmpty()) return;

        String sql = """
                INSERT INTO %s (
                    branch_id, product_id, quantity_delta, movement_type, reference_type, reference_id,
                    actor_name, note, supplier_id, trans_total, pay_type, remaining_amount, created_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(companyId));

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                OrderDetails detail = details.get(i);
                ps.setInt(1, order.getBranchId());
                ps.setInt(2, detail.getProductId());
                ps.setInt(3, detail.getQuantity() * -1);
                ps.setString(4, "SALE_OUT");
                ps.setString(5, "ORDER");
                ps.setString(6, String.valueOf(orderId));
                ps.setString(7, order.getSalesUser());
                ps.setString(8, "Posted from POS sale flow");
                ps.setInt(9, 0);
                ps.setInt(10, detail.getTotal());
                ps.setString(11, "Sale");
                ps.setInt(12, 0);
                ps.setTimestamp(13, orderTime);
            }

            @Override
            public int getBatchSize() {
                return details.size();
            }
        });
    }

    private void validateAndResolveSerializedSaleLines(Order order, int companyId) {
        Set<Long> unitsInOrder = new HashSet<>();
        for (OrderDetails detail : order.getOrderDetails()) {
            if (detail.getProductId() <= 0 || !isSerializedProduct(companyId, detail.getProductId())) {
                continue;
            }

            ArrayList<Long> resolvedUnitIds = new ArrayList<>();
            if (detail.getProductUnitIds() != null) {
                for (Long productUnitId : detail.getProductUnitIds()) {
                    if (productUnitId != null && productUnitId > 0) {
                        resolvedUnitIds.add(productUnitId);
                    }
                }
            }
            if (detail.getUnitIdentifiers() != null) {
                for (String unitIdentifier : detail.getUnitIdentifiers()) {
                    if (unitIdentifier == null || unitIdentifier.trim().isEmpty()) {
                        continue;
                    }
                    ProductUnit unit = productUnitRepository.findByScanCode(companyId, order.getBranchId(), unitIdentifier.trim())
                            .orElseThrow(() -> new ApiException(
                                    HttpStatus.NOT_FOUND,
                                    "SERIALIZED_UNIT_NOT_FOUND",
                                    "Serialized unit was not found in this branch"
                            ));
                    resolvedUnitIds.add(unit.getProductUnitId());
                }
            }

            Set<Long> uniqueLineUnitSet = new HashSet<>(resolvedUnitIds);
            ArrayList<Long> uniqueLineUnits = new ArrayList<>(uniqueLineUnitSet);
            for (Long productUnitId : uniqueLineUnits) {
                if (!unitsInOrder.add(productUnitId)) {
                    throw new ApiException(HttpStatus.CONFLICT, "SERIALIZED_UNIT_DUPLICATE_IN_ORDER", "The same serialized unit appears more than once in the order");
                }
            }
            if (uniqueLineUnits.size() != detail.getQuantity()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SERIALIZED_UNIT_QUANTITY_MISMATCH", "Serialized sale quantity must match selected IMEI/serial unit count");
            }
            detail.setProductUnitIds(uniqueLineUnits);
        }
    }

    private void markSerializedUnitsSold(int orderId, Order order, int companyId, Timestamp orderTime) {
        for (OrderDetails detail : order.getOrderDetails()) {
            if (detail.getProductId() <= 0 || !isSerializedProduct(companyId, detail.getProductId())) {
                continue;
            }
            for (Long productUnitId : detail.getProductUnitIds()) {
                ProductUnit unit = productUnitRepository.findAvailableForSaleForUpdate(
                                companyId,
                                order.getBranchId(),
                                detail.getProductId(),
                                productUnitId
                        )
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.CONFLICT,
                                "SERIALIZED_UNIT_NOT_AVAILABLE",
                                "Serialized unit is not available for sale in this branch"
                        ));

                int updated = productUnitRepository.markSold(
                        companyId,
                        order.getBranchId(),
                        detail.getProductId(),
                        productUnitId,
                        orderId,
                        (long) detail.getOdId(),
                        order.getClientId() > 0 ? (long) order.getClientId() : null
                );
                if (updated != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, "SERIALIZED_UNIT_SALE_CONFLICT", "Serialized unit was already sold or moved");
                }

                InventoryStockMovement movement = new InventoryStockMovement();
                movement.setCompanyId(companyId);
                movement.setBranchId((long) order.getBranchId());
                movement.setProductId(detail.getProductId());
                movement.setProductUnitId(unit.getProductUnitId());
                movement.setMovementType(InventoryMovementType.SALE);
                movement.setQuantityDelta(BigDecimal.ONE.negate());
                movement.setReferenceType("ORDER");
                movement.setReferenceId(String.valueOf(orderId));
                movement.setReferenceLineId((long) detail.getOdId());
                movement.setCustomerId(order.getClientId() > 0 ? (long) order.getClientId() : null);
                movement.setActorName(order.getSalesUser());
                stockMovementRepository.insertMovement(movement);
                insertSerializedSaleLedgerEntry(orderId, detail, unit, order, companyId, orderTime);
            }
        }
    }

    private Long insertSerializedSaleLedgerEntry(int orderId,
                                                 OrderDetails detail,
                                                 ProductUnit unit,
                                                 Order order,
                                                 int companyId,
                                                 Timestamp orderTime) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, product_id, product_unit_id, quantity_delta, movement_type,
                    reference_type, reference_id, actor_name, note, supplier_id, trans_total,
                    pay_type, remaining_amount, idempotency_key, created_at
                ) VALUES (
                    :companyId, :branchId, :productId, :productUnitId, :quantityDelta, :movementType,
                    :referenceType, :referenceId, :actorName, :note, :supplierId, :transTotal,
                    :payType, :remainingAmount, :idempotencyKey, :createdAt
                )
                ON CONFLICT DO NOTHING
                RETURNING stock_ledger_id
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(companyId));

        List<Long> inserted = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", order.getBranchId())
                        .addValue("productId", detail.getProductId())
                        .addValue("productUnitId", unit.getProductUnitId())
                        .addValue("quantityDelta", -1)
                        .addValue("movementType", "SALE")
                        .addValue("referenceType", "ORDER")
                        .addValue("referenceId", String.valueOf(orderId))
                        .addValue("actorName", order.getSalesUser())
                        .addValue("note", "Serialized unit sold from POS order detail " + detail.getOdId())
                        .addValue("supplierId", 0)
                        .addValue("transTotal", detail.getPrice())
                        .addValue("payType", "Sale")
                        .addValue("remainingAmount", 0)
                        .addValue("idempotencyKey", "ORDER:" + orderId + ":DETAIL:" + detail.getOdId() + ":UNIT:" + unit.getProductUnitId() + ":SALE")
                        .addValue("createdAt", orderTime),
                (rs, rowNum) -> rs.getLong("stock_ledger_id")
        );

        return inserted.isEmpty() ? null : inserted.getFirst();
    }

    public void returnSerializedUnitsForOrderDetail(OrderBounceBackContext context, int branchId, int companyId) {
        List<ProductUnit> units = productUnitRepository.findBySaleOrderDetail(
                companyId,
                branchId,
                context.getProductId(),
                context.getOrderDetailId()
        );
        if (units.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SERIALIZED_RETURN_UNITS_NOT_FOUND",
                    "No serialized units are linked to this order detail"
            );
        }

        for (ProductUnit unit : units) {
            int updated = productUnitRepository.updateStatus(
                    companyId,
                    branchId,
                    unit.getProductUnitId(),
                    com.example.valueinsoftbackend.Model.Inventory.ProductUnitStatus.SOLD,
                    com.example.valueinsoftbackend.Model.Inventory.ProductUnitStatus.AVAILABLE,
                    null
            );
            if (updated != 1) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "SERIALIZED_RETURN_CONFLICT",
                        "Serialized unit could not be returned because its status changed"
                );
            }

            InventoryStockMovement movement = new InventoryStockMovement();
            movement.setCompanyId(companyId);
            movement.setBranchId((long) branchId);
            movement.setProductId(context.getProductId());
            movement.setProductUnitId(unit.getProductUnitId());
            movement.setMovementType(InventoryMovementType.RETURN);
            movement.setQuantityDelta(BigDecimal.ONE);
            movement.setReferenceType("ORDER_BOUNCE_BACK");
            movement.setReferenceId(String.valueOf(context.getOrderDetailId()));
            movement.setReferenceLineId((long) context.getOrderDetailId());
            movement.setCustomerId(context.getClientId() == null || context.getClientId() <= 0 ? null : context.getClientId().longValue());
            movement.setActorName(context.getSalesUser());
            movement.setNote("Returned serialized unit from order bounce back");
            stockMovementRepository.insertMovement(movement);
        }
    }

    public boolean isSerializedProduct(int companyId, int productId) {
        return productTrackingRepository.findTrackingMetadata(companyId, productId)
                .map(ProductTrackingMetadata::getTrackingType)
                .map(TrackingType::isSerialized)
                .orElse(false);
    }

    private String buildOrdersWithDetailsSelect(int companyId, int branchId) {
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String detailTable = TenantSqlIdentifiers.orderDetailTable(companyId, branchId);
        return "SELECT ord.\"orderId\", ord.\"orderTime\", ord.\"clientName\", ord.\"orderType\", ord.\"orderDiscount\", ord.\"orderTotal\", " +
                "ord.\"salesUser\", ord.\"clientId\", ord.\"orderIncome\", ord.\"orderBouncedBack\", ord.receipt_number, ord.idempotency_key, details.orderDetails " +
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
                "   'bouncedBack', detail.\"bouncedBack\", " +
                "   'productUnitIds', COALESCE(serialized.product_unit_ids, ARRAY[]::bigint[]), " +
                "   'unitIdentifiers', COALESCE(serialized.unit_identifiers, ARRAY[]::text[])" +
                " ))) AS orderDetails, detail.\"orderId\" AS order_id " +
                " FROM " + detailTable + " detail " +
                " LEFT JOIN LATERAL (" +
                "   SELECT array_agg(unit.product_unit_id ORDER BY unit.product_unit_id) AS product_unit_ids, " +
                "          array_agg(unit.unit_identifier ORDER BY unit.product_unit_id) AS unit_identifiers " +
                "   FROM " + TenantSqlIdentifiers.inventoryProductUnitTable(companyId) + " unit " +
                "   WHERE unit.branch_id = " + branchId + " AND unit.sale_order_detail_id = detail.\"orderDetailsId\"" +
                " ) serialized ON true " +
                " GROUP BY detail.\"orderId\"" +
                ") details ON details.order_id = ord.\"orderId\"";
    }

    private Order mapOrder(java.sql.ResultSet rs, int branchId) throws SQLException {
        Order o = new Order(
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
        o.setReceiptNumber(rs.getString("receipt_number"));
        o.setIdempotencyKey(rs.getString("idempotency_key"));
        return o;
    }

    private ArrayList<OrderDetails> parseOrderDetails(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return new ArrayList<>();
        }
        ArrayList<OrderDetails> details = gson.fromJson(detailsJson, ORDER_DETAILS_LIST_TYPE);
        return details == null ? new ArrayList<>() : details;
    }

    private List<Long> toLongList(java.sql.Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return new ArrayList<>();
        }
        Object[] values = (Object[]) sqlArray.getArray();
        ArrayList<Long> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Number number) {
                result.add(number.longValue());
            }
        }
        return result;
    }

    private List<String> toStringList(java.sql.Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return new ArrayList<>();
        }
        Object[] values = (Object[]) sqlArray.getArray();
        ArrayList<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                result.add(value.toString());
            }
        }
        return result;
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
        private final Integer clientId;
        private final int orderDiscount;
        private final int orderTotal;
        private final int buyingPrice;
        private final boolean hasOtherActiveItems;

        public OrderBounceBackContext(int orderDetailId, int orderId, int quantity, int total, int productId,
                                      int bouncedBack, String salesUser, Integer clientId, int orderDiscount, int orderTotal, int buyingPrice,
                                      boolean hasOtherActiveItems) {
            this.orderDetailId = orderDetailId;
            this.orderId = orderId;
            this.quantity = quantity;
            this.total = total;
            this.productId = productId;
            this.bouncedBack = bouncedBack;
            this.salesUser = salesUser;
            this.clientId = clientId;
            this.orderDiscount = orderDiscount;
            this.orderTotal = orderTotal;
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

        public Integer getClientId() {
            return clientId;
        }

        public int getOrderDiscount() {
            return orderDiscount;
        }

        public int getOrderTotal() {
            return orderTotal;
        }

        public int getBuyingPrice() {
            return buyingPrice;
        }

        public boolean hasOtherActiveItems() {
            return hasOtherActiveItems;
        }
    }
}


