/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;

@Service
public class DbPosDamagedList {

    JdbcTemplate jdbcTemplate;

    @Autowired
    public DbPosDamagedList(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public class DbPosDamagedListMapper implements RowMapper<DamagedItem> {
        @Override
        public DamagedItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            DamagedItem damagedItem = new DamagedItem(
                    rs.getInt("DId"),
                    rs.getInt("ProductId"),
                    rs.getString("ProductName"),
                    rs.getTimestamp("Time"),
                    rs.getString("Reason"),
                    rs.getString("Damaged by"),
                    rs.getString("Cashier user"),
                    rs.getInt("AmountTP"),
                    rs.getBoolean("Paid"),
                    rs.getInt("branchId"),
                    rs.getInt("quantity")
            );
            return damagedItem;
        }
    }

    public  ArrayList<DamagedItem> getDamagedList(int branchId,String companyName) {
        ArrayList<DamagedItem> damagedItems ;
        String query = "SELECT \"DId\", \"ProductId\", \"ProductName\", \"Time\", \"Reason\", \"Damaged by\", \"Cashier user\", \"AmountTP\", \"Paid\", \"branchId\",  \"quantity\"\n" +
                "\tFROM c_"+companyName+".\"DamagedList\" where \"branchId\"  ="+branchId+" ;";
        try {
            damagedItems = (ArrayList<DamagedItem>) jdbcTemplate.query(query, new Object[] {}, new DbPosDamagedListMapper());
            return damagedItems;
        } catch (Exception e) {
            System.out.println("err in get DamagedList : " + e.getMessage());
        }
        return null;
    }


     public String AddDamagedItem(int branchId,String companyName,DamagedItem damagedItem) {
        String query = "INSERT INTO c_"+companyName+".\"DamagedList\"(\n" +
                " \"ProductId\", \"ProductName\", \"Time\", \"Reason\", \"Damaged by\", \"Cashier user\", \"AmountTP\", \"Paid\", \"branchId\" ,  \"quantity\")\n" +
                "\tVALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?,?);";
        try {
            jdbcTemplate.update(query,
                    damagedItem.getProductId(),
                    damagedItem.getProductName(),
                    damagedItem.getTime(),
                    damagedItem.getReason(),
                    damagedItem.getDamagedBy(),
                    damagedItem.getCashierUser(),
                    damagedItem.getAmountTP(),
                    damagedItem.isPaid(),
                    damagedItem.getBranchId(),
                    damagedItem.getQuantity()
            );
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the DamagedItem not added by error!";
        }
        return "the DamagedItem added! ok 200";
    }

    //todo Update Not Yet
    static public String updateSupplier(Supplier supplier, int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE public.supplier_" + branchId + "\n" +
                    "\tSET \"supplierId\"=?, \"SupplierName\"=?, \"supplierPhone1\"=?, \"supplierPhone2\"=?, \"SupplierLocation\"=?, \"suplierMajor\"=?\n" +
                    "\tWHERE \"supplierId\" = " + supplier.getSupplierId() + ";");

            stmt.setInt(1, supplier.getSupplierId());
            stmt.setString(2, supplier.getSupplierName());
            stmt.setString(3, supplier.getSupplierPhone1());
            stmt.setString(4, supplier.getSupplierPhone2());
            stmt.setString(5, supplier.getSuplierLocation());
            stmt.setString(6, supplier.getSuplierMajor());
            int i = stmt.executeUpdate();
            System.out.println(i + " supplier update record ");
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the supplier not updates by error!";
        }
        return "the supplier updates with (ok 200)";
    }

    //todo -- delete
    public static boolean deleteDamagedItem( int branchId,String companyName,int DId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "DELETE FROM public.\"DamagedList\"\n" +
                    "\tWHERE \"DId\" = " + DId + ";";

            PreparedStatement pstmt = null;
            pstmt = conn.prepareStatement(query);
            pstmt.executeUpdate();
            // create the java statement
            pstmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("err in get user : " + e.getMessage());
            return false;
        }
        return true;
    }


}
