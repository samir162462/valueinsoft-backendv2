package com.example.valueinsoftbackend.Service.DashboardProviders;

import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class DashboardTopPerformerProvider {

    public DashboardTopPerformerProvider() {
    }

    @Cacheable(value = "dashboardTopPerformers", key = "#companyId + '_' + #branchId + '_' + #date")
    public DashboardSummaryResponse.DashboardTopPerformers getTopPerformers(Integer companyId, Integer branchId, String date) {
        // Placeholder for Top Performers logic.
        // In the future, this will query the database for the top products, customers, and staff based on sales.
        DashboardSummaryResponse.DashboardTopPerformers topPerformers = new DashboardSummaryResponse.DashboardTopPerformers();
        topPerformers.setProducts(new ArrayList<>());
        topPerformers.setCustomers(new ArrayList<>());
        topPerformers.setStaff(new ArrayList<>());
        return topPerformers;
    }
}
