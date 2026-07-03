package com.example.valueinsoftbackend.companyinsights.activity;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Near-real-time branch activity signals for BRANCH_NO_ACTIVITY. Reads current-day POS
 * orders directly (not the daily KPI snapshot) so alerts reflect today.
 */
@Component
public class LiveBranchActivityReader {

    private final JdbcTemplate jdbcTemplate;

    public LiveBranchActivityReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Number of POS orders for a branch since the start of {@code businessDate}.
     */
    public int todayOrderCount(int companyId, int branchId, LocalDate businessDate) {
        String sql = "SELECT COUNT(*)::integer FROM " + TenantSqlIdentifiers.orderTable(companyId, branchId)
                + " WHERE \"orderTime\" >= ?";
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class,
                    Timestamp.valueOf(businessDate.atStartOfDay()));
            return count == null ? 0 : count;
        } catch (RuntimeException exception) {
            // Unknown / query failure -> report a sentinel so the rule can suppress rather than
            // raise a false "no activity" alert.
            return -1;
        }
    }
}
