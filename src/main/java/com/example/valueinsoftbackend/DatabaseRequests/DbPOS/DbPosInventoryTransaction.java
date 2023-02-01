/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DbPosInventoryTransaction {


    JdbcTemplate jdbcTemplate;

    @Autowired
    public DbPosInventoryTransaction(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public class InventoryTransactionMapper implements RowMapper<InventoryTransaction> {
        @Override
        public InventoryTransaction mapRow(ResultSet rs, int rowNum) throws SQLException {
            InventoryTransaction inventoryTransaction = new InventoryTransaction(
                    rs.getInt("transId"),
                    rs.getInt("productId"),
                    rs.getString("userName"),
                    rs.getInt("supplierId"),
                    rs.getString("transactionType"),
                    rs.getInt("NumItems"),
                    rs.getInt("transTotal"),
                    rs.getString("payType"),
                    rs.getTimestamp("time"),
                    rs.getInt("RemainingAmount"));
            return inventoryTransaction;
        }
    }

    public ArrayList<InventoryTransaction> getInventoryTrans(int companyId , int branchId , String startDate, String endDate  )
    {
        List<InventoryTransaction> inventoryTransactionList ;
        String query = "SELECT \"transId\", \"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\"\n" +
                "\tFROM C_"+companyId+".\"InventoryTransactions_"+branchId+"\" " +
                "where \"time\" >= date_trunc('month', '"+startDate+"'::timestamp)\n" +
                "  \tand \"time\" < date_trunc('month', '"+endDate+"'::timestamp) + interval '1 month'";
        inventoryTransactionList = jdbcTemplate.query(query, new Object[] {}, new InventoryTransactionMapper());
        log.info("Inside Get inventoryTransactionList");
        return (ArrayList<InventoryTransaction>) inventoryTransactionList;

    }


     public String AddTransactionToInv(int productId, String userName, int supplierId, String transactionType , int NumItems, int transTotal , String payType, Timestamp time , int remainingAmount, int branchId ,int companyId)
    {
        String sql  ="BEGIN;\n" +
                "INSERT INTO C_"+companyId+".\"InventoryTransactions_"+branchId+"\"(\n" +
                "\t \"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\")\n" +
                "\tVALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?);" +
                "UPDATE C_"+companyId+".supplier_"+branchId+"\n" +
                "SET  \"supplierRemainig\" = \"supplierRemainig\" + "+transTotal+", \"supplierTotalSales\" = \"supplierTotalSales\" + "+remainingAmount+" "+
                "\tWHERE \"supplierId\"= " +supplierId+";" +
                "COMMIT;\n";
        log.info("Inside Add TransactionToInv");
        try {
            jdbcTemplate.update(sql,
                    productId,
                    userName,
                    supplierId,
                    transactionType,
                    NumItems,
                    transTotal,
                    payType,
                    time,
                    remainingAmount);

        }catch (Exception e )
        {
            System.out.println(e.getMessage());
            return "the supplier not added bs error!";
        }
        return "the supplier added! ok 200";
    }


}
