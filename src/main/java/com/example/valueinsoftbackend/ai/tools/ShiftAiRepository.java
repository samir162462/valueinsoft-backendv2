package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ShiftAiRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ShiftAiRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ShiftAiSummaryDto> getCurrentShiftSummary(long companyId, long branchId) {
        String sql = """
                SELECT
                    sh."PosSOID" AS shift_id,
                    sh."branchId" AS branch_id,
                    sh.status,
                    sh.opened_by_user_id,
                    sh.assigned_cashier_id,
                    sh."ShiftStartTime" AS opened_at,
                    sh."ShiftEndTime" AS closed_at,
                    COALESCE(COUNT(ord."orderId"), 0) AS order_count,
                    COALESCE(SUM(ord."orderTotal"), 0) AS gross_sales,
                    COALESCE(SUM(ord."orderDiscount"), 0) AS discount_total,
                    COALESCE(SUM(ord."orderTotal" - COALESCE(ord."orderDiscount", 0) - COALESCE(ord."orderBouncedBack", 0)), 0) AS net_sales,
                    COALESCE((
                        SELECT SUM(CASE movement_type
                            WHEN 'OPENING_FLOAT' THEN amount
                            WHEN 'CASH_SALE' THEN amount
                            WHEN 'PAID_IN' THEN amount
                            WHEN 'CASH_REFUND' THEN -amount
                            WHEN 'PAID_OUT' THEN -amount
                            WHEN 'SAFE_DROP' THEN -amount
                            ELSE 0
                        END)
                        FROM %s cm
                        WHERE cm.shift_id = sh."PosSOID"
                    ), 0) AS expected_cash,
                    COALESCE(sh.opening_float, 0) AS opening_float
                FROM %s sh
                LEFT JOIN %s ord ON ord.shift_id = sh."PosSOID"
                WHERE sh."branchId" = :branchId
                  AND sh.status = 'OPEN'
                GROUP BY sh."PosSOID", sh."branchId", sh.status, sh.opened_by_user_id,
                         sh.assigned_cashier_id, sh."ShiftStartTime", sh."ShiftEndTime", sh.opening_float
                ORDER BY sh."PosSOID" DESC
                LIMIT 1
                """.formatted(
                TenantSqlIdentifiers.shiftCashMovementTable(Math.toIntExact(companyId)),
                TenantSqlIdentifiers.shiftPeriodTable(Math.toIntExact(companyId)),
                TenantSqlIdentifiers.orderTable(Math.toIntExact(companyId), Math.toIntExact(branchId))
        );
        List<ShiftAiSummaryDto> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("branchId", branchId),
                (rs, rowNum) -> new ShiftAiSummaryDto(
                        rs.getLong("shift_id"),
                        rs.getLong("branch_id"),
                        rs.getString("status"),
                        rs.getString("opened_by_user_id"),
                        rs.getString("assigned_cashier_id"),
                        rs.getTimestamp("opened_at") == null ? null : rs.getTimestamp("opened_at").toLocalDateTime(),
                        rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toLocalDateTime(),
                        rs.getLong("order_count"),
                        rs.getBigDecimal("gross_sales"),
                        rs.getBigDecimal("discount_total"),
                        rs.getBigDecimal("net_sales"),
                        nullToZero(rs.getBigDecimal("expected_cash")),
                        nullToZero(rs.getBigDecimal("opening_float"))
                )
        );
        return rows.stream().findFirst();
    }

    public List<PaymentBreakdownDto> getPaymentBreakdown(long companyId,
                                                         long branchId,
                                                         LocalDate fromDate,
                                                         LocalDate toDate) {
        String sql = """
                SELECT
                    movement_type AS payment_type,
                    COUNT(*) AS transaction_count,
                    COALESCE(SUM(amount), 0) AS total_amount
                FROM %s
                WHERE branch_id = :branchId
                  AND created_at >= :fromTime
                  AND created_at < :toTime
                GROUP BY movement_type
                ORDER BY total_amount DESC
                """.formatted(TenantSqlIdentifiers.shiftCashMovementTable(Math.toIntExact(companyId)));
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("fromTime", Timestamp.valueOf(fromDate.atStartOfDay()))
                        .addValue("toTime", Timestamp.valueOf(toDate.plusDays(1).atStartOfDay())),
                (rs, rowNum) -> new PaymentBreakdownDto(
                        rs.getString("payment_type"),
                        rs.getLong("transaction_count"),
                        rs.getBigDecimal("total_amount")
                )
        );
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
