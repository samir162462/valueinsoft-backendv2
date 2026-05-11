package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class SupplierAiRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SupplierAiRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SupplierAiDto> getSupplierBalance(long companyId, long branchId, String supplierName) {
        List<SupplierAiDto> rows = searchSuppliers(companyId, branchId, supplierName, 1);
        return rows.stream().findFirst();
    }

    public List<SupplierAiDto> getTopSuppliersByPayable(long companyId, long branchId, int limit) {
        String sql = """
                SELECT "supplierId", "SupplierName", "supplierPhone1", "suplierMajor",
                       COALESCE("supplierRemainig", 0)::numeric AS balance
                FROM %s
                WHERE COALESCE("supplierRemainig", 0) > 0
                  AND COALESCE("supplierStatus", 'active') = 'active'
                ORDER BY COALESCE("supplierRemainig", 0) DESC, "SupplierName" ASC
                LIMIT :limit
                """.formatted(TenantSqlIdentifiers.supplierTable(Math.toIntExact(companyId), Math.toIntExact(branchId)));
        return jdbcTemplate.query(sql, new MapSqlParameterSource().addValue("limit", limit), (rs, rowNum) -> new SupplierAiDto(
                rs.getLong("supplierId"),
                rs.getString("SupplierName"),
                maskPhone(rs.getString("supplierPhone1")),
                rs.getString("suplierMajor"),
                rs.getBigDecimal("balance")
        ));
    }

    public List<SupplierInvoiceAiDto> getPendingSupplierInvoices(long companyId,
                                                                 long branchId,
                                                                 String supplierName,
                                                                 int limit) {
        Optional<SupplierAiDto> supplier = getSupplierBalance(companyId, branchId, supplierName);
        if (supplier.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT "sBPId", "supplierId", "productId", quantity,
                       COALESCE(cost, 0)::numeric AS total_cost,
                       COALESCE("sPaid", 0)::numeric AS paid_amount,
                       (COALESCE(cost, 0) - COALESCE("sPaid", 0))::numeric AS remaining_amount,
                       "time", "desc"
                FROM %s
                WHERE "branchId" = :branchId
                  AND "supplierId" = :supplierId
                  AND (COALESCE(cost, 0) - COALESCE("sPaid", 0)) > 0
                ORDER BY "time" DESC, "sBPId" DESC
                LIMIT :limit
                """.formatted(TenantSqlIdentifiers.supplierBoughtProductTable(Math.toIntExact(companyId)));
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("supplierId", supplier.get().supplierId())
                        .addValue("limit", limit),
                (rs, rowNum) -> new SupplierInvoiceAiDto(
                        rs.getLong("sBPId"),
                        rs.getLong("supplierId"),
                        rs.getLong("productId"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("total_cost"),
                        rs.getBigDecimal("paid_amount"),
                        rs.getBigDecimal("remaining_amount"),
                        rs.getTimestamp("time") == null ? null : rs.getTimestamp("time").toLocalDateTime(),
                        rs.getString("desc")
                )
        );
    }

    private List<SupplierAiDto> searchSuppliers(long companyId, long branchId, String supplierName, int limit) {
        String sql = """
                SELECT "supplierId", "SupplierName", "supplierPhone1", "suplierMajor",
                       COALESCE("supplierRemainig", 0)::numeric AS balance
                FROM %s
                WHERE "SupplierName" ILIKE :supplierName
                  AND COALESCE("supplierStatus", 'active') = 'active'
                ORDER BY "SupplierName" ASC
                LIMIT :limit
                """.formatted(TenantSqlIdentifiers.supplierTable(Math.toIntExact(companyId), Math.toIntExact(branchId)));
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("supplierName", "%" + nullSafe(supplierName) + "%")
                        .addValue("limit", limit),
                (rs, rowNum) -> new SupplierAiDto(
                        rs.getLong("supplierId"),
                        rs.getString("SupplierName"),
                        maskPhone(rs.getString("supplierPhone1")),
                        rs.getString("suplierMajor"),
                        rs.getBigDecimal("balance")
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
