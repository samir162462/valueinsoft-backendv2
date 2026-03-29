/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvCompanyChartSalesIncome;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DbDvCompany {

    private final JdbcTemplate jdbcTemplate;

    public DbDvCompany(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getShiftTotalAndIncomeOfAllBranches(int companyId, ArrayList<Branch> branchArrayList, String hours) {
        List<Map<String, Object>> response = new ArrayList<>();
        for (Branch branch : branchArrayList) {
            String sql = "SELECT COALESCE(SUM(\"orderTotal\"), 0)::integer AS sumTotal, " +
                    "COALESCE(SUM(\"orderIncome\"), 0)::integer AS sumIncome, " +
                    "COUNT(\"orderId\")::integer AS countOrders, " +
                    "(now()::date + (?::interval))::text AS fromTime " +
                    "FROM " + TenantSqlIdentifiers.orderTable(companyId, branch.getBranchID()) +
                    " WHERE \"orderTime\" >= now()::date + (?::interval)";

            Map<String, Object> metrics = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("sumTotal", rs.getInt("sumTotal"));
                data.put("sumIncome", rs.getInt("sumIncome"));
                data.put("countOrders", rs.getInt("countOrders"));
                data.put("fromTime", rs.getString("fromTime"));
                data.put("branchLocation", branch.getBranchLocation());
                return data;
            }, hours, hours);

            Map<String, Object> branchNode = new LinkedHashMap<>();
            branchNode.put(branch.getBranchName(), metrics);
            response.add(branchNode);
        }
        return response;
    }

    public List<DvCompanyChartSalesIncome> getShiftTotalAndIncomeOfAllBranchesPerDay(int companyId,
                                                                                      ArrayList<Branch> branchArrayList,
                                                                                      String hours) {
        List<DvCompanyChartSalesIncome> response = new ArrayList<>();
        for (Branch branch : branchArrayList) {
            String sql = "SELECT CONCAT(Date_Part('month', \"orderTime\"), '-', Date_Part('day', \"orderTime\")) AS daym, " +
                    "COALESCE(SUM(\"orderTotal\"), 0)::integer AS orderTotal, " +
                    "COALESCE(SUM(\"orderIncome\"), 0)::integer AS orderIncome " +
                    "FROM " + TenantSqlIdentifiers.orderTable(companyId, branch.getBranchID()) +
                    " WHERE \"orderTime\" >= now()::date + (?::interval) " +
                    "GROUP BY DATE(\"orderTime\"), daym ORDER BY DATE(\"orderTime\")";

            List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("daym", rs.getString("daym"));
                data.put("orderTotal", rs.getInt("orderTotal"));
                data.put("orderIncome", rs.getInt("orderIncome"));
                return data;
            }, hours);

            ArrayList<String> dates = new ArrayList<>();
            ArrayList<Integer> sumTotal = new ArrayList<>();
            ArrayList<Integer> sumIncome = new ArrayList<>();

            for (Map<String, Object> row : rows) {
                dates.add((String) row.get("daym"));
                sumTotal.add((Integer) row.get("orderTotal"));
                sumIncome.add((Integer) row.get("orderIncome"));
            }

            response.add(new DvCompanyChartSalesIncome(branch.getBranchID(), sumTotal, sumIncome, dates));
        }
        return response;
    }
}
