/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales;

import com.example.valueinsoftbackend.Model.DataVisualizationModels.DVSalesYearly;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.Model.Sales.SalesProduct;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class DbDvSales {

    private final JdbcTemplate jdbcTemplate;

    public DbDvSales(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DvSales> getMonthlySales(Integer companyId, Integer branchId, String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        LocalDateTime monthStart = date.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);

        String sql = "SELECT DATE(\"orderTime\") AS salesDate, " +
                "CAST(date_trunc('month', ?::timestamp) AS date) AS firstDay, " +
                "(date_trunc('month', ?::timestamp) + interval '1 month' - interval '1 day')::date AS lastDay, " +
                "COALESCE(SUM(\"orderTotal\"), 0) - COALESCE(SUM(\"orderBouncedBack\"), 0) AS sum, " +
                "COALESCE(SUM(\"orderIncome\"), 0)::integer AS income, " +
                "COUNT(\"orderId\")::integer AS count " +
                "FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " " +
                "WHERE \"orderTime\" >= ? AND \"orderTime\" < ? " +
                "GROUP BY DATE(\"orderTime\") " +
                "ORDER BY DATE(\"orderTime\") ASC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> new DvSales(
                rs.getDate("firstDay"),
                rs.getDate("lastDay"),
                rs.getDate("salesDate"),
                rs.getInt("sum"),
                rs.getInt("income"),
                rs.getInt("count")
        ), Timestamp.valueOf(monthStart), Timestamp.valueOf(monthStart), Timestamp.valueOf(monthStart), Timestamp.valueOf(monthEnd));
    }

    public DvSales getDailyKpis(Integer companyId, Integer branchId, String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        String sql = "SELECT " +
                "COALESCE(SUM(\"orderTotal\"), 0) - COALESCE(SUM(\"orderBouncedBack\"), 0) AS total, " +
                "COALESCE(SUM(\"orderIncome\"), 0)::integer AS income, " +
                "COUNT(\"orderId\")::integer AS count " +
                "FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " " +
                "WHERE \"orderTime\" >= ? AND \"orderTime\" < ?";

        try {
            Timestamp lastOrder = jdbcTemplate.queryForObject("SELECT MAX(\"orderTime\") FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId), Timestamp.class);
            System.out.println("DEBUG: Latest Order in DB for Branch " + branchId + " is: " + lastOrder);
        } catch (Exception e) {}

        Timestamp start = Timestamp.valueOf(dayStart);
        Timestamp end = Timestamp.valueOf(dayEnd);
        System.out.println("DEBUG: SQL Range -> Start: " + start + " End: " + end);

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new DvSales(
                rs.getInt("total"),
                rs.getInt("income"),
                rs.getInt("count")
        ), start, end);
    }

    public List<DVSalesYearly> getYearlySales(int companyId, int currentYear, int branchId) {
        String sql = "SELECT COALESCE(SUM(\"orderTotal\"), 0) - COALESCE(SUM(\"orderBouncedBack\"), 0) AS sum, " +
                "to_char(date_trunc('month', \"orderTime\"), 'Mon ') AS month, " +
                "EXTRACT(MONTH FROM date_trunc('month', \"orderTime\"))::integer AS num, " +
                "COALESCE(SUM(\"orderIncome\"), 0)::integer AS income " +
                "FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " " +
                "WHERE EXTRACT(YEAR FROM \"orderTime\") = ? " +
                "GROUP BY month, num ORDER BY num";
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new DVSalesYearly(rs.getString("month"), rs.getInt("sum"), rs.getInt("num"), rs.getInt("income")),
                currentYear
        );
    }

    public List<SalesProduct> getSalesProductsByPeriod(int companyId, int branchId, String startTime, String endTime) {
        String sql = "SELECT od.\"itemName\", COUNT(od.\"itemId\")::integer AS NumberOfOrders, " +
                "COALESCE(SUM(od.quantity), 0)::integer AS SumQuantity, " +
                "COALESCE(SUM(od.\"total\"), 0)::integer AS sumTotal " +
                "FROM " + TenantSqlIdentifiers.orderDetailTable(companyId, branchId) + " od " +
                "JOIN " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " o ON od.\"orderId\" = o.\"orderId\" " +
                "WHERE od.\"bouncedBack\" < 1 AND o.\"orderTime\" >= ?::timestamp AND o.\"orderTime\" <= ?::timestamp " +
                "GROUP BY od.\"itemName\" ORDER BY SumQuantity DESC, NumberOfOrders";
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SalesProduct(
                        rs.getString("itemName"),
                        rs.getInt("NumberOfOrders"),
                        rs.getInt("SumQuantity"),
                        rs.getInt("sumTotal")
                ),
                startTime,
                endTime
        );
    }
}
