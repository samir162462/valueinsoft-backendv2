package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Shift.Shift;
import com.example.valueinsoftbackend.Model.Shift.ShiftPeriod;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class DbPosShiftPeriod {

    private final JdbcTemplate jdbcTemplate;

    public DbPosShiftPeriod(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── row mapper for the full Shift model ────────────
    private static final RowMapper<Shift> SHIFT_ROW_MAPPER = (rs, rowNum) -> {
        Shift s = new Shift();
        s.setShiftId(rs.getInt("PosSOID"));
        s.setBranchId(rs.getInt("branchId"));
        s.setOpenedAt(rs.getTimestamp("ShiftStartTime"));
        s.setClosedAt(rs.getTimestamp("ShiftEndTime"));
        s.setStatus(rs.getString("status"));
        s.setOpenedByUserId(rs.getString("opened_by_user_id"));
        s.setAssignedCashierId(rs.getString("assigned_cashier_id"));
        s.setClosedByUserId(rs.getString("closed_by_user_id"));
        s.setRegisterCode(rs.getString("register_code"));
        s.setOpeningFloat(rs.getBigDecimal("opening_float"));
        s.setExpectedCash(rs.getBigDecimal("expected_cash"));
        s.setCountedCash(rs.getBigDecimal("counted_cash"));
        s.setVarianceAmount(rs.getBigDecimal("variance_amount"));
        s.setVarianceReason(rs.getString("variance_reason"));
        s.setCloseNote(rs.getString("close_note"));
        s.setOrderCount(rs.getInt("order_count"));
        s.setGrossSales(rs.getBigDecimal("gross_sales"));
        s.setNetSales(rs.getBigDecimal("net_sales"));
        s.setDiscountTotal(rs.getBigDecimal("discount_total"));
        s.setRefundTotal(rs.getBigDecimal("refund_total"));
        s.setVersion(rs.getInt("version"));
        s.setCreatedAt(rs.getTimestamp("created_at"));
        s.setUpdatedAt(rs.getTimestamp("updated_at"));
        return s;
    };

    private static final String ALL_SHIFT_COLUMNS =
            "\"PosSOID\", \"ShiftStartTime\", \"ShiftEndTime\", \"branchId\", " +
            "opened_by_user_id, assigned_cashier_id, closed_by_user_id, register_code, " +
            "status, opening_float, expected_cash, counted_cash, variance_amount, " +
            "variance_reason, close_note, order_count, gross_sales, net_sales, " +
            "discount_total, refund_total, version, created_at, updated_at";

    // ═══════════════════════════════════════════════════
    //  QUERIES
    // ═══════════════════════════════════════════════════

    public boolean hasOpenShift(int companyId, int branchId) {
        String sql = "SELECT EXISTS (" +
                "SELECT 1 FROM " + TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " WHERE \"branchId\" = ? AND status = 'OPEN')";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, branchId);
        return exists != null && exists;
    }

    public Shift getActiveShift(int companyId, int branchId) {
        String sql = "SELECT " + ALL_SHIFT_COLUMNS + " FROM " +
                TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " WHERE \"branchId\" = ? AND status = 'OPEN'" +
                " ORDER BY \"PosSOID\" DESC LIMIT 1";

        List<Shift> shifts = jdbcTemplate.query(sql, SHIFT_ROW_MAPPER, branchId);
        return shifts.isEmpty() ? null : shifts.get(0);
    }

    public Shift getShiftById(int companyId, int shiftId) {
        String sql = "SELECT " + ALL_SHIFT_COLUMNS + " FROM " +
                TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " WHERE \"PosSOID\" = ?";

        List<Shift> shifts = jdbcTemplate.query(sql, SHIFT_ROW_MAPPER, shiftId);
        if (shifts.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND", "Shift was not found");
        }
        return shifts.get(0);
    }

    public Shift getShiftForUpdate(int companyId, int shiftId) {
        String sql = "SELECT " + ALL_SHIFT_COLUMNS + " FROM " +
                TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " WHERE \"PosSOID\" = ? FOR UPDATE";

        List<Shift> shifts = jdbcTemplate.query(sql, SHIFT_ROW_MAPPER, shiftId);
        if (shifts.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND", "Shift was not found");
        }
        return shifts.get(0);
    }

    public int getShiftBranchId(int companyId, int shiftPeriodId) {
        String sql = "SELECT \"branchId\" FROM " + TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " WHERE \"PosSOID\" = ?";
        List<Integer> branchIds = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("branchId"), shiftPeriodId);
        if (branchIds.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND", "Shift was not found");
        }
        return branchIds.get(0);
    }

    public ArrayList<Shift> getBranchShifts(int companyId, int branchId) {
        String sql = "SELECT " + ALL_SHIFT_COLUMNS + " FROM " +
                TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " WHERE \"branchId\" = ? ORDER BY \"PosSOID\" DESC";

        return new ArrayList<>(jdbcTemplate.query(sql, SHIFT_ROW_MAPPER, branchId));
    }

    // ═══════════════════════════════════════════════════
    //  MUTATIONS
    // ═══════════════════════════════════════════════════

    public Shift insertShift(int companyId, int branchId, Timestamp startTime,
                             String openedByUserId, BigDecimal openingFloat,
                             String registerCode) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " (\"ShiftStartTime\", \"ShiftEndTime\", \"branchId\", " +
                "  opened_by_user_id, assigned_cashier_id, register_code, " +
                "  status, opening_float) " +
                "VALUES (?, NULL, ?, ?, ?, ?, 'OPEN', ?) " +
                "RETURNING " + ALL_SHIFT_COLUMNS;

        return jdbcTemplate.queryForObject(sql, SHIFT_ROW_MAPPER,
                startTime, branchId, openedByUserId, openedByUserId,
                registerCode, openingFloat);
    }

    public int closeShift(int companyId, int shiftId, Timestamp endTime,
                          String closedByUserId, String status,
                          BigDecimal expectedCash, BigDecimal countedCash,
                          BigDecimal varianceAmount, String varianceReason,
                          String closeNote,
                          int orderCount, BigDecimal grossSales, BigDecimal netSales,
                          BigDecimal discountTotal, BigDecimal refundTotal) {
        String sql = "UPDATE " + TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " SET \"ShiftEndTime\" = ?, closed_by_user_id = ?, status = ?, " +
                "    expected_cash = ?, counted_cash = ?, variance_amount = ?, " +
                "    variance_reason = ?, close_note = ?, " +
                "    order_count = ?, gross_sales = ?, net_sales = ?, " +
                "    discount_total = ?, refund_total = ?, " +
                "    version = version + 1, updated_at = CURRENT_TIMESTAMP " +
                "WHERE \"PosSOID\" = ? AND status IN ('OPEN', 'CLOSING')";

        return jdbcTemplate.update(sql,
                endTime, closedByUserId, status,
                expectedCash, countedCash, varianceAmount,
                varianceReason, closeNote,
                orderCount, grossSales, netSales,
                discountTotal, refundTotal,
                shiftId);
    }

    public int forceCloseShift(int companyId, int shiftId, Timestamp endTime,
                               String closedByUserId, String reason) {
        String sql = "UPDATE " + TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " SET \"ShiftEndTime\" = ?, closed_by_user_id = ?, status = 'FORCE_CLOSED', " +
                "    close_note = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP " +
                "WHERE \"PosSOID\" = ? AND status IN ('OPEN', 'CLOSING')";
        return jdbcTemplate.update(sql, endTime, closedByUserId, reason, shiftId);
    }

    // ═══════════════════════════════════════════════════
    //  SHIFT EVENTS
    // ═══════════════════════════════════════════════════

    public void insertShiftEvent(int companyId, int shiftId, int branchId,
                                 String eventType, String actorUserId,
                                 String actorRole, String reason, String metadata) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.shiftEventTable(companyId) +
                " (shift_id, branch_id, event_type, actor_user_id, actor_role, reason, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)";
        jdbcTemplate.update(sql, shiftId, branchId, eventType, actorUserId, actorRole, reason, metadata);
    }

    public List<Map<String, Object>> getShiftEvents(int companyId, int shiftId) {
        String sql = "SELECT event_id, shift_id, branch_id, event_type, event_time, " +
                "actor_user_id, actor_role, reference_type, reference_id, metadata, reason " +
                "FROM " + TenantSqlIdentifiers.shiftEventTable(companyId) +
                " WHERE shift_id = ? ORDER BY event_time";
        return jdbcTemplate.queryForList(sql, shiftId);
    }

    // ═══════════════════════════════════════════════════
    //  CASH MOVEMENTS
    // ═══════════════════════════════════════════════════

    public void insertCashMovement(int companyId, int shiftId, int branchId,
                                   String movementType, BigDecimal amount,
                                   String actorUserId, String note, Integer clientId, 
                                   String associatedUserId, String referenceType, String referenceId) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.shiftCashMovementTable(companyId) +
                " (shift_id, branch_id, movement_type, amount, actor_user_id, note, client_id, associated_user_id, reference_type, reference_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, shiftId, branchId, movementType, amount, actorUserId, note, clientId, associatedUserId, referenceType, referenceId);
    }

    public List<Map<String, Object>> getCashMovements(int companyId, int shiftId) {
        // First get branchId to join with the correct orders table
        Integer branchId = getShiftBranchId(companyId, shiftId);
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);

        String sql = "SELECT m.movement_id, m.shift_id, m.branch_id, m.movement_type, m.amount, " +
                "m.actor_user_id, m.note, m.created_at, " +
                "COALESCE(m.client_id, ord.\"clientId\") as client_id, " +
                "COALESCE(NULLIF(NULLIF(c.\"clientName\", ''), 'No Client'), " +
                "         NULLIF(NULLIF(ord.\"clientName\", ''), 'No Client'), " +
                "  CASE WHEN m.movement_type IN ('CASH_SALE', 'CASH_REFUND') THEN 'Guest' ELSE NULL END) as \"clientName\", " +
                "m.associated_user_id, " +
                "COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.\"firstName\", u.\"lastName\")), ''), " +
                "         NULLIF(TRIM(CONCAT_WS(' ', act.\"firstName\", act.\"lastName\")), ''), " +
                "         m.associated_user_id, " +
                "         m.actor_user_id) as \"staffName\" " +
                "FROM " + TenantSqlIdentifiers.shiftCashMovementTable(companyId) + " m " +
                "LEFT JOIN " + TenantSqlIdentifiers.clientTable(companyId) + " c ON m.client_id = c.c_id " +
                "LEFT JOIN " + TenantSqlIdentifiers.userTable(companyId) + " u ON (m.associated_user_id = u.\"userName\" OR m.associated_user_id LIKE (u.\"userName\" || ' : %')) " +
                "LEFT JOIN " + TenantSqlIdentifiers.userTable(companyId) + " act ON (m.actor_user_id = act.\"userName\" OR m.actor_user_id LIKE (act.\"userName\" || ' : %')) " +
                "LEFT JOIN " + orderTable + " ord ON (" +
                "  (m.reference_type = 'ORDER' AND m.reference_id ~ '^[0-9]+$' AND ord.\"orderId\" = CAST(m.reference_id AS INTEGER)) " +
                "  OR (m.reference_type IS NULL AND m.movement_type IN ('CASH_SALE', 'CASH_REFUND') AND m.note ~ '#\\s*[0-9]+' " +
                "      AND ord.\"orderId\" = CAST(SUBSTRING(m.note FROM '#\\s*([0-9]+)') AS INTEGER))" +
                ") " +
                " WHERE m.shift_id = ? ORDER BY m.created_at";
        return jdbcTemplate.queryForList(sql, shiftId);
    }

    public BigDecimal computeExpectedCash(int companyId, int shiftId) {
        String sql = "SELECT COALESCE(SUM(" +
                "  CASE movement_type " +
                "    WHEN 'OPENING_FLOAT' THEN amount " +
                "    WHEN 'CASH_SALE'     THEN amount " +
                "    WHEN 'PAID_IN'       THEN amount " +
                "    WHEN 'CASH_REFUND'   THEN -amount " +
                "    WHEN 'PAID_OUT'      THEN -amount " +
                "    WHEN 'SAFE_DROP'     THEN -amount " +
                "    ELSE 0 " +
                "  END), 0) " +
                "FROM " + TenantSqlIdentifiers.shiftCashMovementTable(companyId) +
                " WHERE shift_id = ?";
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, shiftId);
    }

    // ═══════════════════════════════════════════════════
    //  LEGACY COMPATIBILITY
    // ═══════════════════════════════════════════════════

    @Deprecated
    public ShiftPeriod insertShift(int companyId, int branchId, Timestamp startTime) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " (\"ShiftStartTime\", \"ShiftEndTime\", \"branchId\") VALUES (?, NULL, ?) " +
                "RETURNING \"PosSOID\", \"ShiftStartTime\", \"ShiftEndTime\"";

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ShiftPeriod(
                rs.getInt("PosSOID"),
                rs.getTimestamp("ShiftStartTime"),
                rs.getTimestamp("ShiftEndTime"),
                null
        ), startTime, branchId);
    }

    @Deprecated
    public int closeShift(int companyId, int shiftPeriodId, Timestamp endTime) {
        String sql = "UPDATE " + TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " SET \"ShiftEndTime\" = ?, status = 'CLOSED', " +
                "    version = version + 1, updated_at = CURRENT_TIMESTAMP " +
                "WHERE \"PosSOID\" = ?";
        return jdbcTemplate.update(sql, endTime, shiftPeriodId);
    }

    public ShiftPeriod getCurrentShift(int companyId, int branchId) {
        String sql = "SELECT \"PosSOID\", \"ShiftStartTime\", \"ShiftEndTime\" FROM " +
                TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " WHERE \"branchId\" = ? AND status = 'OPEN'";

        List<ShiftPeriod> shifts = jdbcTemplate.query(sql, (rs, rowNum) -> new ShiftPeriod(
                rs.getInt("PosSOID"),
                rs.getTimestamp("ShiftStartTime"),
                rs.getTimestamp("ShiftEndTime"),
                null
        ), branchId);

        return shifts.isEmpty() ? null : shifts.get(0);
    }

    @Deprecated
    public ArrayList<ShiftPeriod> getBranchShiftsLegacy(int companyId, int branchId) {
        String sql = "SELECT \"PosSOID\", \"ShiftStartTime\", \"ShiftEndTime\" FROM " +
                TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " WHERE \"branchId\" = ? ORDER BY \"PosSOID\" DESC";

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> new ShiftPeriod(
                rs.getInt("PosSOID"),
                rs.getTimestamp("ShiftStartTime"),
                rs.getTimestamp("ShiftEndTime"),
                null
        ), branchId));
    }
}
