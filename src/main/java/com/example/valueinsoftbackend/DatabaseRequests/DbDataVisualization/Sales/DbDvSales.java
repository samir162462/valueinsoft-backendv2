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

import java.util.List;

@Repository
public class DbDvSales {

    private final JdbcTemplate jdbcTemplate;

    public DbDvSales(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DvSales> getMonthlySales(int companyId, String currentMonth, int branchId) {
        String sql = "SELECT DATE(\"orderTime\") AS salesDate, " +
                "CAST(date_trunc('month', ?::date) AS date) AS firstDay, " +
                "(date_trunc('month', ?::date) + interval '1 month' - interval '1 day')::date AS lastDay, " +
                "COALESCE((SUM(\"orderTotal\") - SUM(\"orderBouncedBack\")), 0)::integer AS sum " +
                "FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " " +
                "WHERE \"orderTime\"::date >= CAST(date_trunc('month', ?::date) AS date) " +
                "AND \"orderTime\"::date < (date_trunc('month', ?::date) + interval '1 month')::date " +
                "GROUP BY DATE(\"orderTime\") ORDER BY DATE(\"orderTime\") ASC";
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new DvSales(rs.getDate("firstDay"), rs.getDate("lastDay"), rs.getDate("salesDate"), rs.getInt("sum")),
                currentMonth,
                currentMonth,
                currentMonth,
                currentMonth
        );
    }

    public List<DVSalesYearly> getYearlySales(int companyId, int currentYear, int branchId) {
        String sql = "SELECT COALESCE((SUM(\"orderTotal\") - SUM(\"orderBouncedBack\")), 0)::integer AS sum, " +
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
                "WHERE od.\"bouncedBack\" < 1 AND o.\"orderTime\"::date >= ?::date AND o.\"orderTime\"::date <= ?::date " +
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
