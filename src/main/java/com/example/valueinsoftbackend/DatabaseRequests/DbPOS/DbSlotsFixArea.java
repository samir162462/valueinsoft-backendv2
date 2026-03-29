/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Slots.SlotsFixArea;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class DbSlotsFixArea {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final RowMapper<SlotsFixArea> FIX_AREA_ROW_MAPPER = new RowMapper<>() {
        @Override
        public SlotsFixArea mapRow(ResultSet rs, int rowNum) throws SQLException {
            SlotsFixArea slot = new SlotsFixArea(
                    rs.getInt("faId"),
                    rs.getInt("fixSlot"),
                    rs.getInt("clientId"),
                    rs.getDate("dateIn"),
                    rs.getDate("dateFinished"),
                    rs.getString("phoneName"),
                    rs.getString("problem"),
                    rs.getBoolean("show"),
                    rs.getString("userName_Recived"),
                    rs.getString("status"),
                    rs.getString("desc"),
                    rs.getInt("branchId"),
                    rs.getBigDecimal("fees")
            );

            String clientData = rs.getString("data");
            if (clientData != null) {
                try {
                    JsonNode jsonNode = OBJECT_MAPPER.readTree(clientData);
                    slot.setClientData(jsonNode);
                } catch (Exception ignored) {
                }
            }
            return slot;
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public DbSlotsFixArea(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SlotsFixArea> getFixAreaSlot(int companyId, int branchId, int prevMonth) {
        String baseSql = "SELECT fa.\"faId\", fa.\"fixSlot\", fa.\"clientId\", fa.\"dateIn\", fa.\"dateFinished\", " +
                "fa.\"phoneName\", fa.problem, fa.show, fa.\"userName_Recived\", fa.status, fa.\"desc\", " +
                "fa.fees::numeric AS fees, json_build_object('clientName', c.\"clientName\", 'clientPhone', c.\"clientPhone\") AS data, " +
                "fa.\"branchId\" " +
                "FROM " + TenantSqlIdentifiers.fixAreaTable(companyId) + " fa " +
                "LEFT JOIN " + TenantSqlIdentifiers.clientTable(companyId) + " c ON fa.\"clientId\" = c.c_id " +
                "WHERE fa.\"branchId\" = ? ";

        if (prevMonth > 0) {
            String sql = baseSql + "AND fa.\"dateIn\" >= date_trunc('month', current_date - (? * interval '1 month')) " +
                    "ORDER BY fa.\"dateIn\" DESC, fa.\"faId\" DESC";
            return jdbcTemplate.query(sql, FIX_AREA_ROW_MAPPER, branchId, prevMonth);
        }

        String sql = baseSql + "AND fa.show = true ORDER BY fa.\"faId\" DESC";
        return jdbcTemplate.query(sql, FIX_AREA_ROW_MAPPER, branchId);
    }

    public int insertFixAreaSlot(int companyId, SlotsFixArea slotsFixArea) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.fixAreaTable(companyId) +
                " (\"fixSlot\", \"clientId\", \"dateIn\", \"dateFinished\", \"phoneName\", problem, show, " +
                "\"userName_Recived\", status, \"desc\", \"branchId\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(
                sql,
                slotsFixArea.getFixSlot(),
                slotsFixArea.getClientId(),
                slotsFixArea.getDateIn(),
                slotsFixArea.getDateFinished(),
                slotsFixArea.getPhoneName(),
                slotsFixArea.getProblem(),
                slotsFixArea.isShow(),
                slotsFixArea.getUserName_Recived(),
                slotsFixArea.getStatus(),
                slotsFixArea.getDesc(),
                slotsFixArea.getBranchId()
        );
    }

    public int updateFixAreaSlot(int companyId, SlotsFixArea slotsFixArea) {
        String sql = "UPDATE " + TenantSqlIdentifiers.fixAreaTable(companyId) +
                " SET \"dateFinished\" = ?, problem = ?, show = ?, status = ?, \"desc\" = ?, fees = ? " +
                "WHERE \"faId\" = ? AND \"branchId\" = ?";
        return jdbcTemplate.update(
                sql,
                slotsFixArea.getDateFinished(),
                slotsFixArea.getProblem(),
                slotsFixArea.isShow(),
                slotsFixArea.getStatus(),
                slotsFixArea.getDesc(),
                slotsFixArea.getFees(),
                slotsFixArea.getFaId(),
                slotsFixArea.getBranchId()
        );
    }
}
