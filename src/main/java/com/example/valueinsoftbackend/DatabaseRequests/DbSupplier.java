/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.Model.SupplierBProduct;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.google.gson.JsonObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class DbSupplier {


//todo Get
    public static ArrayList<Supplier> getSuppliers(int branchId,int companyId) {
        ArrayList<Supplier> supList = new ArrayList<>();
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"supplierId\", \"SupplierName\", \"supplierPhone1\", \"supplierPhone2\", \"SupplierLocation\" , \"suplierMajor\" , \"supplierTotalSales\" , \"supplierRemainig\"\n" +
                    "\tFROM C_"+companyId+".\"supplier_" + branchId + "\";";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                Supplier sup = new Supplier(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),rs.getInt(7),rs.getInt(8));
                supList.add(sup);
            }
            rs.close();
            st.close();
            conn.close();
            return supList;
        } catch (Exception e) {
            System.out.println("err in get user : " + e.getMessage());
        }
        return null;
    }


    static public String AddSupplier(String name, String phone1, String phone2, String loaction, String major, int branchId,int companyId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO C_"+companyId+".supplier_" + branchId + "(\n" +
                    "\t \"SupplierName\", \"supplierPhone1\", \"supplierPhone2\", \"SupplierLocation\",\"suplierMajor\")\n" +
                    "\tVALUES ( ?, ?, ?, ?,?);");

            stmt.setString(1, name);
            stmt.setString(2, phone1);
            stmt.setString(3, phone2);
            stmt.setString(4, loaction);
            stmt.setString(5, major);
            int i = stmt.executeUpdate();
            System.out.println(i + " supplier added records inserted");
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the supplier not added bs error!";
        }
        return "the supplier added! ok 200";
    }

    //todo Update
    static public String updateSupplier(Supplier supplier, int branchId,int companyId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE C_"+companyId+".supplier_" + branchId + "\n" +
                    "\tSET  \"SupplierName\"=?, \"supplierPhone1\"=?, \"supplierPhone2\"=?, \"SupplierLocation\"=?, \"suplierMajor\"=?\n" +
                    "\tWHERE \"supplierId\" = " + supplier.getSupplierId() + ";");

            stmt.setString(1, supplier.getSupplierName());
            stmt.setString(2, supplier.getSupplierPhone1());
            stmt.setString(3, supplier.getSupplierPhone2());
            stmt.setString(4, supplier.getSuplierLocation());
            stmt.setString(5, supplier.getSuplierMajor());
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
    public static boolean deleteSupp(int suppId, int branchId,int companyId) {
        System.out.println("text -> " + suppId + " " + branchId);
        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "DELETE FROM C_"+companyId+".supplier_" + branchId + "\n" +
                    "\tWHERE \"supplierId\" = " + suppId + ";";

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

    //Supplier service -------------
    public static JsonObject getRemainingSupplierAmountByProductId(int id, int branchId, int companyId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT C_"+companyId+".\"PosProduct_" + branchId + "\".\"productId\",C_"+companyId+".\"InventoryTransactions_" + branchId + "\".\"time\",C_"+companyId+".\"InventoryTransactions_" + branchId + "\".\"payType\" as payType , C_"+companyId+".\"InventoryTransactions_" + branchId + "\".\"RemainingAmount\" as remainingAmount\n" +
                    "FROM C_"+companyId+".\"PosProduct_" + branchId + "\" \n" +
                    "INNER JOIN\n" +
                    "    C_"+companyId+".\"InventoryTransactions_" + branchId + "\" \n" +
                    "ON\n" +
                    "   C_"+companyId+".\"PosProduct_" + branchId + "\".\"productId\" = C_"+companyId+".\"InventoryTransactions_" + branchId + "\".\"productId\" where C_"+companyId+".\"PosProduct_" + branchId + "\".\"productId\" = " + id + " ORDER BY C_"+companyId+".\"InventoryTransactions_" + branchId + "\".\"time\" DESC LIMIT 1 ; ";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            JsonObject json = new JsonObject();

            while (rs.next()) {
                // print the results
                json.addProperty("productId", rs.getInt(1));
                json.addProperty("time", rs.getString(2));
                json.addProperty("payType", rs.getString(3));
                json.addProperty("remainingAmount", rs.getInt(4));
            }
            System.out.println(json.toString());
            rs.close();
            st.close();
            conn.close();
            return json;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return null;

        }

    }

    //todo get Suppliers sales
    public static ArrayList<InventoryTransaction> getSupplierSales(int branchId, int supplierId,int companyId) {
        ArrayList<InventoryTransaction> suppliersSales = new ArrayList<>();
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"transId\", \"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\"\n" +
                    "\tFROM C_"+companyId+".\"InventoryTransactions_" + branchId + "\"  where \"supplierId\" =" + supplierId + " ;";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            System.out.println(query);
            while (rs.next()) {
                InventoryTransaction inventoryTransaction = new InventoryTransaction(
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getString(3),
                        rs.getInt(4),
                        rs.getString(5),
                        rs.getInt(6),
                        rs.getInt(7),
                        rs.getString(8),
                        rs.getTimestamp(9),
                        rs.getInt(10)
                );
                // print the results
                suppliersSales.add(inventoryTransaction);
            }
            rs.close();
            st.close();
            conn.close();
            return suppliersSales;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return null;

        }


    }

    //todo  Suppliers BProduct
    public static ArrayList<SupplierBProduct> getSupplierBProduct(int branchId, int supplierId,int companyId) {
        ArrayList<SupplierBProduct> supplierBProducts = new ArrayList<>();
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"sBPId\", \"productId\", quantity, cost, \"userName\", \"sPaid\", \"time\", \"desc\"\n" +
                    "\tFROM c_"+companyId+".\"SupplierBProduct\" where  \"branchId\" = "+branchId+" AND \"supplierId\" = "+supplierId+" ;";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            System.out.println(query);
            while (rs.next()) {
                SupplierBProduct supplierBProduct = new SupplierBProduct(
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getInt(3),
                        rs.getInt(4),
                        rs.getString(5),
                        rs.getInt(6),
                        rs.getTimestamp(7),
                        rs.getString(8)
                );
                // print the results
                supplierBProducts.add(supplierBProduct);
            }
            rs.close();
            st.close();
            conn.close();
            return supplierBProducts;

        } catch (Exception e) {
            System.out.println("err  supplierBProducts : " + e.getMessage());
            return null;

        }
    }

    static public String AddSupplierBProduct(SupplierBProduct supplierBProduct, int productId, int branchId, int companyId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();

            PreparedStatement stmt = conn.prepareStatement("INSERT INTO c_"+companyId+".\"SupplierBProduct\"(\n" +
                    " \"productId\", quantity, cost, \"userName\", \"sPaid\", \"time\", \"desc\", \"supplierId\", \"branchId\")\n" +
                    " VALUES (?, ?, ?, ?, ?, ?, ?,  ( SELECT \"supplierId\" FROM c_"+companyId+".\"PosProduct_"+branchId+"\" where \"productId\" = "+productId+") , "+branchId+");");

            stmt.setInt(1, productId);
            stmt.setInt(2, supplierBProduct.getQuantity());
            stmt.setInt(3, supplierBProduct.getCost());
            stmt.setString(4, supplierBProduct.getUserName());
            stmt.setInt(5, supplierBProduct.getsPaid());
            stmt.setTimestamp(6, supplierBProduct.getTime());
            stmt.setString(7, supplierBProduct.getDesc());
            System.out.println(stmt.toString());
            int i = stmt.executeUpdate();
            System.out.println(i + " supplier added records inserted");
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the supplier BProduct not added by error!";
        }
        return "the supplier BProduct added! ok 200";
    }
}
