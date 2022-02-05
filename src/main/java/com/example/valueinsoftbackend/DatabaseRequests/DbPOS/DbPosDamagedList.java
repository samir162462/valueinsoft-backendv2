/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

public class DbPosDamagedList {

    //todo Get
    public static ArrayList<DamagedItem> getDamagedList(int branchId,String companyName) {
        ArrayList<DamagedItem> damagedItems = new ArrayList<>();
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"DId\", \"ProductId\", \"ProductName\", \"Time\", \"Reason\", \"Damaged by\", \"Cashier user\", \"AmountTP\", \"Paid\", \"branchId\",  \"quantity\"\n" +
                    "\tFROM "+companyName+".\"DamagedList\" where \"branchId\"  ="+branchId+" ;";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                DamagedItem damagedItem = new DamagedItem(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getTimestamp(4), rs.getString(5), rs.getString(6),rs.getString(7),rs.getInt(8),rs.getBoolean(9),rs.getInt(10),rs.getInt(11));
                damagedItems.add(damagedItem);
            }
            rs.close();
            st.close();
            conn.close();
            return damagedItems;
        } catch (Exception e) {
            System.out.println("err in get DamagedList : " + e.getMessage());
        }
        return null;
    }


    static public String AddDamagedItem(int branchId,String companyName,DamagedItem damagedItem) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO "+companyName+".\"DamagedList\"(\n" +
                    " \"ProductId\", \"ProductName\", \"Time\", \"Reason\", \"Damaged by\", \"Cashier user\", \"AmountTP\", \"Paid\", \"branchId\" ,  \"quantity\")\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?,?);");

            stmt.setInt(1, damagedItem.getProductId());
            stmt.setString(2, damagedItem.getProductName());
            stmt.setTimestamp(3, damagedItem.getTime());
            stmt.setString(4, damagedItem.getReason());
            stmt.setString(5, damagedItem.getDamagedBy());
            stmt.setString(6, damagedItem.getCashierUser());
            stmt.setInt(7, damagedItem.getAmountTP());
            stmt.setBoolean(8, damagedItem.isPaid());
            stmt.setInt(9, damagedItem.getBranchId());
            stmt.setInt(10, damagedItem.getQuantity());
            int i = stmt.executeUpdate();
            System.out.println(i + " AddDamagedItem added records inserted");
            stmt.close();
            conn.close();
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
