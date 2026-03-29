/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.DataVisualizationModels.CompanyAnalysis;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
public class DbDVCompanyAnalysis {

    private static final RowMapper<CompanyAnalysis> COMPANY_ANALYSIS_ROW_MAPPER = (rs, rowNum) -> new CompanyAnalysis(
            rs.getInt("sales"),
            rs.getInt("income"),
            rs.getInt("clientsIn"),
            rs.getInt("invShortage"),
            rs.getInt("discountByUsers"),
            rs.getInt("damagedProducts"),
            rs.getInt("returnPurchases"),
            rs.getInt("shiftEndsEarly"),
            rs.getDate("dateM"),
            rs.getInt("numOfDays")
    );

    private final JdbcTemplate jdbcTemplate;

    public DbDVCompanyAnalysis(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasTodayRecord(int companyId, int branchId) {
        String sql = "SELECT EXISTS(SELECT 1 FROM " + TenantSqlIdentifiers.companyAnalysisTable(companyId) +
                " WHERE \"branchId\" = ? AND DATE_TRUNC('day', date)::date = CURRENT_DATE)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, branchId);
        return Boolean.TRUE.equals(exists);
    }

    public int insertTodayRecord(int companyId, int branchId) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.companyAnalysisTable(companyId) +
                " (sales, \"Income\", \"clientsIn\", \"invShortage\", \"discountByUsers\", \"damagedProducts\", " +
                "\"returnPurchases\", \"shiftEndsEarly\", date, \"branchId\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?)";
        return jdbcTemplate.update(sql, 0, 0, 0, 0, 0, 0, 0, 0, branchId);
    }

    public int incrementTodayRecord(int companyId,
                                    int branchId,
                                    int sales,
                                    int income,
                                    int clientIn,
                                    int invShortage,
                                    int discountByUser,
                                    int damagedProducts,
                                    int returnPurchases,
                                    int shiftEndsEarly) {
        String sql = "UPDATE " + TenantSqlIdentifiers.companyAnalysisTable(companyId) +
                " SET sales = sales + ?, \"Income\" = \"Income\" + ?, \"clientsIn\" = \"clientsIn\" + ?, " +
                "\"invShortage\" = \"invShortage\" + ?, \"discountByUsers\" = \"discountByUsers\" + ?, " +
                "\"damagedProducts\" = \"damagedProducts\" + ?, \"returnPurchases\" = \"returnPurchases\" + ?, " +
                "\"shiftEndsEarly\" = \"shiftEndsEarly\" + ? " +
                "WHERE DATE_TRUNC('day', date)::date = CURRENT_DATE AND \"branchId\" = ?";
        return jdbcTemplate.update(
                sql,
                sales,
                income,
                clientIn,
                invShortage,
                discountByUser,
                damagedProducts,
                returnPurchases,
                shiftEndsEarly,
                branchId
        );
    }

    public List<CompanyAnalysis> getCompanyAnalysis(int companyId, int branchId, String byWhat, LocalDate date) {
        String truncUnit = normalizeDateTrunc(byWhat);
        String dateFilter = date == null ? null : date.toString();

        if (branchId > 0) {
            String sql = "SELECT COALESCE(SUM(sales), 0)::integer AS sales, " +
                    "COALESCE(SUM(\"Income\"), 0)::integer AS income, " +
                    "COALESCE(SUM(\"clientsIn\"), 0)::integer AS \"clientsIn\", " +
                    "COALESCE(SUM(\"invShortage\"), 0)::integer AS \"invShortage\", " +
                    "COALESCE(SUM(\"discountByUsers\"), 0)::integer AS \"discountByUsers\", " +
                    "COALESCE(SUM(\"damagedProducts\"), 0)::integer AS \"damagedProducts\", " +
                    "COALESCE(SUM(\"returnPurchases\"), 0)::integer AS \"returnPurchases\", " +
                    "COALESCE(SUM(\"shiftEndsEarly\"), 0)::integer AS \"shiftEndsEarly\", " +
                    "DATE_TRUNC('" + truncUnit + "', date)::date AS dateM, COUNT(\"branchId\")::integer AS numOfDays " +
                    "FROM " + TenantSqlIdentifiers.companyAnalysisTable(companyId) +
                    " WHERE \"branchId\" = ? AND DATE_TRUNC('" + truncUnit + "', date)::date = ?::date " +
                    "GROUP BY dateM";
            return jdbcTemplate.query(sql, COMPANY_ANALYSIS_ROW_MAPPER, branchId, dateFilter);
        }

        String sql = "SELECT COALESCE(SUM(sales), 0)::integer AS sales, " +
                "COALESCE(SUM(\"Income\"), 0)::integer AS income, " +
                "COALESCE(SUM(\"clientsIn\"), 0)::integer AS \"clientsIn\", " +
                "COALESCE(SUM(\"invShortage\"), 0)::integer AS \"invShortage\", " +
                "COALESCE(SUM(\"discountByUsers\"), 0)::integer AS \"discountByUsers\", " +
                "COALESCE(SUM(\"damagedProducts\"), 0)::integer AS \"damagedProducts\", " +
                "COALESCE(SUM(\"returnPurchases\"), 0)::integer AS \"returnPurchases\", " +
                "COALESCE(SUM(\"shiftEndsEarly\"), 0)::integer AS \"shiftEndsEarly\", " +
                "DATE_TRUNC('" + truncUnit + "', date)::date AS dateM, COUNT(\"branchId\")::integer AS numOfDays " +
                "FROM " + TenantSqlIdentifiers.companyAnalysisTable(companyId) +
                " WHERE DATE_TRUNC('" + truncUnit + "', date)::date = ?::date GROUP BY dateM";
        return jdbcTemplate.query(sql, COMPANY_ANALYSIS_ROW_MAPPER, dateFilter);
    }

    private String normalizeDateTrunc(String byWhat) {
        if ("day".equalsIgnoreCase(byWhat) || "year".equalsIgnoreCase(byWhat)) {
            return byWhat.toLowerCase();
        }
        return "month";
    }
}
