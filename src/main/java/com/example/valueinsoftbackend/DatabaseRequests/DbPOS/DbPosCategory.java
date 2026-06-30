package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.MainMajor;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class DbPosCategory {

    private final JdbcTemplate jdbcTemplate;

    public DbPosCategory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int saveCategoryJson(int companyId, int branchId, String payload) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.categoryJsonTable(companyId) +
                " (\"CategoryData\", \"BranchId\") VALUES (?::json, ?)";
        return jdbcTemplate.update(sql, payload, branchId);
    }

    public String getCategoryJson(int branchId, int companyId) {
        String sql = "SELECT \"CategoryData\" FROM " + TenantSqlIdentifiers.categoryJsonTable(companyId) +
                " WHERE \"BranchId\" = ? ORDER BY \"CategoryJID\" DESC LIMIT 1";

        List<String> payloads = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("CategoryData"), branchId);
        return payloads.isEmpty() ? null : payloads.get(0);
    }

    public ArrayList<MainMajor> getMainMajors(int companyId) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.mainMajorTable(companyId);

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> new MainMajor(
                rs.getInt(1),
                rs.getString(2),
                rs.getString(3)
        )));
    }

    public java.util.Map<String, List<String>> getActiveProductsGroupedByBusinessLine(int companyId, int branchId) {
        String sql = "SELECT p.product_name, p.business_line_key FROM " + TenantSqlIdentifiers.inventoryProductTable(companyId) + " p " +
                     "INNER JOIN " + TenantSqlIdentifiers.inventoryBranchProductTable(companyId) + " ibp " +
                     "ON ibp.product_id = p.product_id AND ibp.branch_id = ? AND ibp.is_active = TRUE";

        return jdbcTemplate.query(sql, rs -> {
            java.util.Map<String, List<String>> map = new java.util.HashMap<>();
            while (rs.next()) {
                String name = rs.getString("product_name");
                if (name != null && !name.isBlank()) {
                    String blKey = rs.getString("business_line_key");
                    map.computeIfAbsent(blKey == null ? "UNKNOWN" : blKey, k -> new ArrayList<>()).add(name.trim());
                }
            }
            return map;
        }, branchId);
    }
}
