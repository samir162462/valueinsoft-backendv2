/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbMoney;

import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DBMClientReceipt {

    private final JdbcTemplate jdbcTemplate;

    public DBMClientReceipt(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ArrayList<ClientReceipt> getClientReceipts(int companyId, int clientId) {
        String sql = "SELECT \"crId\", type, amount::money::numeric::float8, \"time\", \"userName\", \"clientId\", \"branchId\" " +
                "FROM " + TenantSqlIdentifiers.clientReceiptsTable(companyId) + " WHERE \"clientId\" = ?";

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> new ClientReceipt(
                rs.getInt("crId"),
                rs.getString("type"),
                rs.getBigDecimal(3),
                rs.getTimestamp("time"),
                rs.getString("userName"),
                rs.getInt("clientId"),
                rs.getInt("branchId")
        ), clientId));
    }

    public ArrayList<ClientReceipt> getClientReceiptsByTime(int companyId, int branchId, Timestamp startTime, Timestamp endTime) {
        String sql = "SELECT \"crId\", type, amount::money::numeric::float8, \"time\", \"userName\", \"clientId\", \"branchId\" " +
                "FROM " + TenantSqlIdentifiers.clientReceiptsTable(companyId) +
                " WHERE \"branchId\" = ? AND \"time\" BETWEEN ? AND ?";

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> new ClientReceipt(
                rs.getInt("crId"),
                rs.getString("type"),
                rs.getBigDecimal(3),
                rs.getTimestamp("time"),
                rs.getString("userName"),
                rs.getInt("clientId"),
                rs.getInt("branchId")
        ), branchId, startTime, endTime));
    }

    public int insertClientReceipt(int companyId, ClientReceipt clientReceipt) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.clientReceiptsTable(companyId) +
                " (type, amount, \"time\", \"userName\", \"clientId\", \"branchId\") VALUES (?, ?, ?, ?, ?, ?)";

        return jdbcTemplate.update(
                sql,
                clientReceipt.getType(),
                clientReceipt.getAmount(),
                clientReceipt.getTime(),
                clientReceipt.getUserName(),
                clientReceipt.getClientId(),
                clientReceipt.getBranchId()
        );
    }
}
