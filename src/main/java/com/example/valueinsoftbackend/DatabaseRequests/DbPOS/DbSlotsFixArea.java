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
                    rs.getBigDecimal("fees"),
                    rs.getString("imei"),
                    rs.getString("deviceCondition"),
                    rs.getString("accessories")
            );

            String clientData = rs.getString("data");
            if (clientData != null) {
                try {
                    JsonNode jsonNode = OBJECT_MAPPER.readTree(clientData);
                    slot.setClientData(jsonNode);
                } catch (Exception ignored) {
                }
            }
            
            if (rs.getObject("order_id") != null) {
                slot.setOrderId(rs.getInt("order_id"));
            }

            String partsData = rs.getString("parts");
            if (partsData != null && !partsData.isBlank() && !partsData.equals("[null]")) {
                try {
                    java.util.List<com.example.valueinsoftbackend.Model.Slots.FixAreaPart> partsList = 
                        OBJECT_MAPPER.readValue(partsData, OBJECT_MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, com.example.valueinsoftbackend.Model.Slots.FixAreaPart.class));
                    slot.setUsedParts(partsList);
                } catch (Exception e) {
                    System.err.println("Error parsing parts JSON: " + e.getMessage());
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
                "fa.fees::numeric AS fees, fa.imei, fa.\"deviceCondition\", fa.accessories, fa.order_id, " +
                "json_build_object('clientName', c.\"clientName\", 'clientPhone', c.\"clientPhone\") AS data, " +
                "fa.\"branchId\", (" +
                "  SELECT json_agg(json_build_object('id', p.id, 'faId', p.fa_id, 'productId', p.product_id, 'quantity', p.quantity, 'unitPrice', p.unit_price, 'total', p.total, 'isDeducted', p.is_deducted, 'productName', pr.product_name)) " +
                "  FROM " + TenantSqlIdentifiers.companySchema(companyId) + ".fix_area_parts p LEFT JOIN " + TenantSqlIdentifiers.inventoryProductTable(companyId) + " pr ON p.product_id = pr.product_id WHERE p.fa_id = fa.\"faId\"" +
                ") AS parts " +
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
                "\"userName_Recived\", status, \"desc\", \"branchId\", imei, \"deviceCondition\", accessories) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                slotsFixArea.getBranchId(),
                slotsFixArea.getImei(),
                slotsFixArea.getDeviceCondition(),
                slotsFixArea.getAccessories()
        );
    }

    public int updateFixAreaSlot(int companyId, SlotsFixArea slotsFixArea) {
        String sql = "UPDATE " + TenantSqlIdentifiers.fixAreaTable(companyId) +
                " SET \"dateFinished\" = ?, problem = ?, show = ?, status = ?, \"desc\" = ?, fees = ?, " +
                "imei = ?, \"deviceCondition\" = ?, accessories = ? " +
                "WHERE \"faId\" = ? AND \"branchId\" = ?";
        return jdbcTemplate.update(
                sql,
                slotsFixArea.getDateFinished(),
                slotsFixArea.getProblem(),
                slotsFixArea.isShow(),
                slotsFixArea.getStatus(),
                slotsFixArea.getDesc(),
                slotsFixArea.getFees(),
                slotsFixArea.getImei(),
                slotsFixArea.getDeviceCondition(),
                slotsFixArea.getAccessories(),
                slotsFixArea.getFaId(),
                slotsFixArea.getBranchId()
        );
    }

    public void saveParts(int companyId, int faId, List<com.example.valueinsoftbackend.Model.Slots.FixAreaPart> parts) {
        String schema = TenantSqlIdentifiers.companySchema(companyId);
        
        // Remove existing parts that are not deducted
        jdbcTemplate.update("DELETE FROM " + schema + ".fix_area_parts WHERE fa_id = ? AND is_deducted = false", faId);
        
        if (parts == null || parts.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO " + schema + ".fix_area_parts (fa_id, product_id, quantity, unit_price, total, is_deducted) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
                com.example.valueinsoftbackend.Model.Slots.FixAreaPart part = parts.get(i);
                ps.setInt(1, faId);
                ps.setInt(2, part.getProductId());
                ps.setInt(3, part.getQuantity());
                ps.setInt(4, part.getUnitPrice());
                ps.setInt(5, part.getTotal());
                ps.setBoolean(6, part.isDeducted());
            }

            @Override
            public int getBatchSize() {
                return parts.size();
            }
        });
    }

    public SlotsFixArea getFixSlotById(int companyId, int branchId, int faId) {
        String baseSql = "SELECT fa.\"faId\", fa.\"fixSlot\", fa.\"clientId\", fa.\"dateIn\", fa.\"dateFinished\", " +
                "fa.\"phoneName\", fa.problem, fa.show, fa.\"userName_Recived\", fa.status, fa.\"desc\", " +
                "fa.fees::numeric AS fees, fa.imei, fa.\"deviceCondition\", fa.accessories, fa.order_id, " +
                "json_build_object('clientName', c.\"clientName\", 'clientPhone', c.\"clientPhone\") AS data, " +
                "fa.\"branchId\", (" +
                "  SELECT json_agg(json_build_object('id', p.id, 'faId', p.fa_id, 'productId', p.product_id, 'quantity', p.quantity, 'unitPrice', p.unit_price, 'total', p.total, 'isDeducted', p.is_deducted, 'productName', pr.product_name)) " +
                "  FROM " + TenantSqlIdentifiers.companySchema(companyId) + ".fix_area_parts p LEFT JOIN " + TenantSqlIdentifiers.inventoryProductTable(companyId) + " pr ON p.product_id = pr.product_id WHERE p.fa_id = fa.\"faId\"" +
                ") AS parts " +
                "FROM " + TenantSqlIdentifiers.fixAreaTable(companyId) + " fa " +
                "LEFT JOIN " + TenantSqlIdentifiers.clientTable(companyId) + " c ON fa.\"clientId\" = c.c_id " +
                "WHERE fa.\"branchId\" = ? AND fa.\"faId\" = ?";
                
        List<SlotsFixArea> results = jdbcTemplate.query(baseSql, FIX_AREA_ROW_MAPPER, branchId, faId);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<SlotsFixArea> searchFixAreaSlots(int companyId, String query) {
        String baseSql = "SELECT fa.\"faId\", fa.\"fixSlot\", fa.\"clientId\", fa.\"dateIn\", fa.\"dateFinished\", " +
                "fa.\"phoneName\", fa.problem, fa.show, fa.\"userName_Recived\", fa.status, fa.\"desc\", " +
                "fa.fees::numeric AS fees, fa.imei, fa.\"deviceCondition\", fa.accessories, fa.order_id, " +
                "json_build_object('clientName', c.\"clientName\", 'clientPhone', c.\"clientPhone\") AS data, " +
                "fa.\"branchId\", (" +
                "  SELECT json_agg(json_build_object('id', p.id, 'faId', p.fa_id, 'productId', p.product_id, 'quantity', p.quantity, 'unitPrice', p.unit_price, 'total', p.total, 'isDeducted', p.is_deducted, 'productName', pr.product_name)) " +
                "  FROM " + TenantSqlIdentifiers.companySchema(companyId) + ".fix_area_parts p LEFT JOIN " + TenantSqlIdentifiers.inventoryProductTable(companyId) + " pr ON p.product_id = pr.product_id WHERE p.fa_id = fa.\"faId\"" +
                ") AS parts " +
                "FROM " + TenantSqlIdentifiers.fixAreaTable(companyId) + " fa " +
                "LEFT JOIN " + TenantSqlIdentifiers.clientTable(companyId) + " c ON fa.\"clientId\" = c.c_id " +
                "WHERE CAST(fa.\"faId\" AS TEXT) = ? OR fa.imei LIKE ? ORDER BY fa.\"faId\" DESC LIMIT 20";
                
        String likeQuery = "%" + query.trim() + "%";
        return jdbcTemplate.query(baseSql, FIX_AREA_ROW_MAPPER, query.trim(), likeQuery);
    }

    public int markSlotPaidAndSave(int companyId, int faId, int orderId) {
        String schema = TenantSqlIdentifiers.companySchema(companyId);
        
        // 1. Mark parts as deducted
        jdbcTemplate.update("UPDATE " + schema + ".fix_area_parts SET is_deducted = true WHERE fa_id = ?", faId);
        
        // 2. Mark slot as paid and save orderId
        return jdbcTemplate.update(
            "UPDATE " + TenantSqlIdentifiers.fixAreaTable(companyId) + " SET status = 'PS', show = false, order_id = ? WHERE \"faId\" = ?",
            orderId, faId
        );
    }

    public int reverseMarkPaid(int companyId, int orderId) {
        String schema = TenantSqlIdentifiers.companySchema(companyId);
        String fixTable = TenantSqlIdentifiers.fixAreaTable(companyId);

        // Find the faId linked to this orderId
        List<Integer> faIds = jdbcTemplate.queryForList(
            "SELECT \"faId\" FROM " + fixTable + " WHERE order_id = ?", Integer.class, orderId
        );
        if (faIds.isEmpty()) {
            return 0;
        }

        int faId = faIds.get(0);

        // 1. Un-deduct parts
        jdbcTemplate.update("UPDATE " + schema + ".fix_area_parts SET is_deducted = false WHERE fa_id = ?", faId);

        // 2. Restore slot to active: status back to 'D' (Done/completed), show=true, clear order_id
        return jdbcTemplate.update(
            "UPDATE " + fixTable + " SET status = 'D', show = true, order_id = NULL WHERE \"faId\" = ?",
            faId
        );
    }

    public int closeSlot(int companyId, int faId) {
        String schema = TenantSqlIdentifiers.companySchema(companyId);
        String fixTable = TenantSqlIdentifiers.fixAreaTable(companyId);

        // 1. Delete all associated parts
        jdbcTemplate.update("DELETE FROM " + schema + ".fix_area_parts WHERE fa_id = ?", faId);

        // 2. Hide the slot
        return jdbcTemplate.update(
            "UPDATE " + fixTable + " SET show = false, status = 'CL' WHERE \"faId\" = ?",
            faId
        );
    }
}
