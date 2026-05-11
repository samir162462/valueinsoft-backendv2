package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class CustomerAiRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CustomerAiRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CustomerAiDto> searchCustomer(long companyId, long branchId, String nameOrPhone, int limit) {
        String sql = """
                SELECT c_id, "clientName", "clientPhone", "branchId", "registeredTime"
                FROM %s
                WHERE "branchId" = :branchId
                  AND ("clientName" ILIKE :query OR "clientPhone" ILIKE :query)
                ORDER BY c_id DESC
                LIMIT :limit
                """.formatted(TenantSqlIdentifiers.clientTable(Math.toIntExact(companyId)));
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("query", "%" + nullSafe(nameOrPhone) + "%")
                        .addValue("limit", limit),
                (rs, rowNum) -> new CustomerAiDto(
                        rs.getLong("c_id"),
                        rs.getString("clientName"),
                        maskPhone(rs.getString("clientPhone")),
                        rs.getLong("branchId"),
                        rs.getTimestamp("registeredTime") == null ? null : rs.getTimestamp("registeredTime").toLocalDateTime()
                )
        );
    }

    public Optional<CustomerBalanceAiDto> getCustomerBalance(long companyId, long branchId, long customerId) {
        String sql = """
                SELECT c.c_id, c."clientName",
                       COALESCE(o.order_total, 0)::numeric AS order_total,
                       COALESCE(r.receipt_total, 0)::numeric AS receipt_total,
                       (COALESCE(o.order_total, 0) - COALESCE(r.receipt_total, 0))::numeric AS balance
                FROM %s c
                LEFT JOIN (
                    SELECT "clientId", SUM("orderTotal" - COALESCE("orderDiscount", 0) - COALESCE("orderBouncedBack", 0)) AS order_total
                    FROM %s
                    WHERE "clientId" = :customerId
                    GROUP BY "clientId"
                ) o ON o."clientId" = c.c_id
                LEFT JOIN (
                    SELECT "clientId", SUM(amount::money::numeric) AS receipt_total
                    FROM %s
                    WHERE "clientId" = :customerId AND "branchId" = :branchId
                    GROUP BY "clientId"
                ) r ON r."clientId" = c.c_id
                WHERE c.c_id = :customerId AND c."branchId" = :branchId
                LIMIT 1
                """.formatted(
                TenantSqlIdentifiers.clientTable(Math.toIntExact(companyId)),
                TenantSqlIdentifiers.orderTable(Math.toIntExact(companyId), Math.toIntExact(branchId)),
                TenantSqlIdentifiers.clientReceiptsTable(Math.toIntExact(companyId))
        );
        List<CustomerBalanceAiDto> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("customerId", customerId)
                        .addValue("branchId", branchId),
                (rs, rowNum) -> new CustomerBalanceAiDto(
                        rs.getLong("c_id"),
                        rs.getString("clientName"),
                        rs.getBigDecimal("order_total"),
                        rs.getBigDecimal("receipt_total"),
                        rs.getBigDecimal("balance")
                )
        );
        return rows.stream().findFirst();
    }

    public List<CustomerOrderAiDto> getCustomerLastOrders(long companyId, long branchId, long customerId, int limit) {
        String sql = """
                SELECT "orderId", "orderTime", "orderType",
                       COALESCE("orderTotal", 0)::numeric AS order_total,
                       COALESCE("orderDiscount", 0)::numeric AS order_discount,
                       (COALESCE("orderTotal", 0) - COALESCE("orderDiscount", 0) - COALESCE("orderBouncedBack", 0))::numeric AS net_total,
                       "salesUser"
                FROM %s
                WHERE "clientId" = :customerId
                ORDER BY "orderTime" DESC, "orderId" DESC
                LIMIT :limit
                """.formatted(TenantSqlIdentifiers.orderTable(Math.toIntExact(companyId), Math.toIntExact(branchId)));
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("customerId", customerId)
                        .addValue("limit", limit),
                (rs, rowNum) -> new CustomerOrderAiDto(
                        rs.getLong("orderId"),
                        rs.getTimestamp("orderTime") == null ? null : rs.getTimestamp("orderTime").toLocalDateTime(),
                        rs.getString("orderType"),
                        rs.getBigDecimal("order_total"),
                        rs.getBigDecimal("order_discount"),
                        rs.getBigDecimal("net_total"),
                        rs.getString("salesUser")
                )
        );
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "****" + digits.substring(digits.length() - 4);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }
}
