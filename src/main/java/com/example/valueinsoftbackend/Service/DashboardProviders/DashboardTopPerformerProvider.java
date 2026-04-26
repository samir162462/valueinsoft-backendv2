package com.example.valueinsoftbackend.Service.DashboardProviders;

import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DashboardTopPerformerProvider {

    private final JdbcTemplate jdbcTemplate;

    public DashboardTopPerformerProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable(value = "dashboardTopPerformers", key = "#companyId + '_' + #branchId + '_' + #date")
    public DashboardSummaryResponse.DashboardTopPerformers getTopPerformers(Integer companyId, Integer branchId, String date) {
        DashboardSummaryResponse.DashboardTopPerformers topPerformers = new DashboardSummaryResponse.DashboardTopPerformers();
        
        // Fetch top categories and their sub-categories
        topPerformers.setCategories(calculateTopCategories(companyId, branchId));
        
        topPerformers.setProducts(new ArrayList<>());
        topPerformers.setCustomers(new ArrayList<>());
        topPerformers.setStaff(new ArrayList<>());
        return topPerformers;
    }

    private List<DashboardSummaryResponse.CategoryPerformance> calculateTopCategories(Integer companyId, Integer branchId) {
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String orderDetailTable = TenantSqlIdentifiers.orderDetailTable(companyId, branchId);
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);

        // 1. Get Top 5 Categories (major) by revenue in last 30 days
        String catSql = "SELECT " +
                "  p.major as cat_name, " +
                "  SUM(od.quantity * od.price)::double precision as total_sales " +
                "FROM " + orderDetailTable + " od " +
                "JOIN " + orderTable + " o ON o.\"orderId\" = od.\"orderId\" " +
                "JOIN " + productTable + " p ON p.product_id = od.\"productId\" " +
                "WHERE o.\"orderTime\" >= (CURRENT_DATE - INTERVAL '30 days') " +
                "GROUP BY p.major " +
                "ORDER BY total_sales DESC " +
                "LIMIT 5";

        try {
            List<Map<String, Object>> catRows = jdbcTemplate.queryForList(catSql);
            List<DashboardSummaryResponse.CategoryPerformance> categories = new ArrayList<>();

            for (Map<String, Object> catRow : catRows) {
                String catName = (String) catRow.get("cat_name");
                Double catSales = (Double) catRow.get("total_sales");

                DashboardSummaryResponse.CategoryPerformance category = new DashboardSummaryResponse.CategoryPerformance();
                category.setName(catName);
                category.setTotalSales(catSales);

                // 2. For each category, get Top 3 Sub-Categories (product_type)
                String subCatSql = "SELECT " +
                        "  p.product_type as sub_cat_name, " +
                        "  SUM(od.quantity * od.price)::double precision as sub_total_sales " +
                        "FROM " + orderDetailTable + " od " +
                        "JOIN " + orderTable + " o ON o.\"orderId\" = od.\"orderId\" " +
                        "JOIN " + productTable + " p ON p.product_id = od.\"productId\" " +
                        "WHERE p.major = ? " +
                        "AND o.\"orderTime\" >= (CURRENT_DATE - INTERVAL '30 days') " +
                        "GROUP BY p.product_type " +
                        "ORDER BY sub_total_sales DESC " +
                        "LIMIT 3";

                List<Map<String, Object>> subRows = jdbcTemplate.queryForList(subCatSql, catName);
                List<DashboardSummaryResponse.SubCategoryPerformance> subCategories = new ArrayList<>();

                for (Map<String, Object> subRow : subRows) {
                    DashboardSummaryResponse.SubCategoryPerformance subCat = new DashboardSummaryResponse.SubCategoryPerformance();
                    subCat.setName((String) subRow.get("sub_cat_name"));
                    subCat.setTotalSales((Double) subRow.get("sub_total_sales"));
                    subCategories.add(subCat);
                }

                category.setSubCategories(subCategories);
                categories.add(category);
            }
            return categories;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
