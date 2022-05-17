/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;


import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.Model.Util.ProductUtilNames;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.*;
import java.util.ArrayList;

public class DbPosProduct {


    public static ArrayList<Product> getProductBySearchText(String[] text, String branchId, int companyId, ProductFilter productFilter) {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Product> productArrayList = new ArrayList<>();
            String sqlQuery = "";
            if (productFilter != null) {
                sqlQuery = productFilter.sqlString();
            } else {
                System.out.println("No Filter");
            }

            String query = "SELECT \"productId\", \"productName\", \"buyingDay\", \"activationPeriod\", \"rPrice\", \"lPrice\", \"bPrice\",\n" +
                    "\"companyName\", type, \"ownerName\", serial, \"desc\", \"batteryLife\", \"ownerPhone\", \"ownerNI\", quantity,\n" +
                    "\"pState\", \"supplierId\",\"major\" , \"imgFile\"" +
                    "\tFROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where " + sqlQuery + "  ";


            // create the java statement
            StringBuilder qy = new StringBuilder(query);

            if (text.length > 0) {
                for (int i = 0; i < text.length; i++) {
                    if (i == 0) {
                        String capital = text[i].substring(0, 1).toUpperCase() + text[i].substring(1);
                        String small = text[i].substring(0, 1).toLowerCase() + text[i].substring(1);
                        String s1 = "(\"productName\" LIKE '%" + capital + "%' or \"productName\" LIKE '%" + small + "%')";
                        qy.append(s1);
                        continue;
                    }
                    String s2 = " And \"productName\" LIKE '%" + text[i] + "%'";
                    qy.append(s2);
                }
                qy.append(";");
            }
            System.out.println(qy.toString());


            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(qy.toString());

            while (rs.next()) {
                System.out.println("add user connected to user " + rs.getString(1));

                Product prod = new Product(rs.getInt(1), rs.getString(2), rs.getTimestamp(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getInt(13), rs.getString(14), rs.getString(15), rs.getInt(16), rs.getString(17), rs.getInt(18), rs.getString(19));
                prod.setImage(rs.getString(20));
                productArrayList.add(prod);

                // print the results
            }

            rs.close();
            st.close();
            conn.close();
            return productArrayList;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return null;

        }

    }

    public static ArrayList<Product> getProductsAllRange(String branchId, int companyId, ProductFilter productFilter) {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Product> productArrayList = new ArrayList<>();
            String sqlQuery = "";
            if (productFilter != null) {
                sqlQuery = productFilter.sqlString();
            } else {
                System.out.println("No Filter");
            }

            String query = "SELECT \"productId\", \"productName\", \"buyingDay\", \"activationPeriod\", \"rPrice\", \"lPrice\", \"bPrice\",\n" +
                    "\"companyName\", type, \"ownerName\", serial, \"desc\", \"batteryLife\", \"ownerPhone\", \"ownerNI\", quantity,\n" +
                    "\"pState\", \"supplierId\",\"major\", \"imgFile\" " +
                    "\tFROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where " + sqlQuery + "   \"productId\" > 0 ;";
            // create the java statement
            StringBuilder qy = new StringBuilder(query);
            System.out.println(qy.toString());
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(qy.toString());

            while (rs.next()) {
                System.out.println("add user connected to user " + rs.getString(1));
                Product prod = new Product(rs.getInt(1), rs.getString(2), rs.getTimestamp(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getInt(13), rs.getString(14), rs.getString(15), rs.getInt(16), rs.getString(17), rs.getInt(18), rs.getString(19));
                prod.setImage(rs.getString(20));
                productArrayList.add(prod);
            }
            rs.close();
            st.close();
            conn.close();
            return productArrayList;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return null;

        }

    }

    public static Product getProductById(int supplierId, int branchId, int companyId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT * FROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where  \"productId\" = " + supplierId + ";";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            String payload = "";
            Product pt = null;

            try {
                while (rs.next()) {
                    pt = new Product(
                            rs.getInt(1),
                            rs.getString(2),
                            rs.getTimestamp(3),
                            rs.getString(4),
                            rs.getInt(5),
                            rs.getInt(6),
                            rs.getInt(7),
                            rs.getString(8),
                            rs.getString(9),
                            rs.getString(10),
                            rs.getString(11),
                            rs.getString(12),
                            rs.getInt(13),
                            rs.getString(14),
                            rs.getString(15),
                            rs.getInt(16),
                            rs.getString(17),
                            rs.getInt(18),
                            rs.getString(19)
                    );
                    pt.setImage(rs.getString(20));
                }
            } catch (Exception e) {
                System.out.println("getProductById " + e.getMessage());
            }

            rs.close();
            st.close();
            conn.close();

            return pt;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;
    }

    public static ResponseEntity<Object> getProductNames(String text, int branchId, int companyId) {

        try {

            String capital = text.substring(0, 1).toUpperCase() + text.substring(1);
            String small = text.substring(0, 1).toLowerCase() + text.substring(1);
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT  DISTINCT ON (\"productName\") \"productName\" ,\"companyName\" , type ,major \n" +
                    "\tFROM c_"+companyId+".\"PosProduct_"+branchId+"\" where \"productName\" LIKE '%"+capital+"%' or \"productName\" Like '%"+small+"%' ORDER BY \n" +
                    "        \"productName\";";
            System.out.println(query);
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            ArrayList<ProductUtilNames> productNames = new ArrayList<>();

            try {
                while (rs.next()) {
                    ProductUtilNames productUtilNames = new ProductUtilNames(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4));
                    productNames.add(productUtilNames);
                }

            } catch (Exception e) {
                System.out.println("getProductById " + e.getMessage());
                return   ResponseEntity.status(406).body("errorIn getProductNames To array" + e.getMessage());
            }

            rs.close();
            st.close();
            conn.close();
            return ResponseEntity.status(200).body(productNames);

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return  ResponseEntity.status(406).body("errorIn getProductNames To array" + e.getMessage());

        }

    }

    public static ArrayList<Product> getProductBySearchCompanyName(String comName, String branchId, int companyId, ProductFilter productFilter) {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Product> productArrayList = new ArrayList<>();
            String query = "";

            String sqlQuery = "";
            if (productFilter != null) {
                sqlQuery = productFilter.sqlString();
            } else {
                System.out.println("No Filter");
            }
            if (comName.contains("All")) {
                query = "SELECT \"productId\", \"productName\", \"buyingDay\", \"activationPeriod\", \"rPrice\", \"lPrice\", \"bPrice\",\n" +
                        "\"companyName\", type, \"ownerName\", serial, \"desc\", \"batteryLife\", \"ownerPhone\", \"ownerNI\", quantity,\n" +
                        "\"pState\", \"supplierId\",\"major\" , \"imgFile\" " +
                        "\tFROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where  " + sqlQuery + " \"type\" = '" + comName.split(" ")[1] + "' ";
            } else {
                query = "SELECT \"productId\", \"productName\", \"buyingDay\", \"activationPeriod\", \"rPrice\", \"lPrice\", \"bPrice\",\n" +
                        "\"companyName\", type, \"ownerName\", serial, \"desc\", \"batteryLife\", \"ownerPhone\", \"ownerNI\", quantity,\n" +
                        "\"pState\", \"supplierId\",\"major\" , \"imgFile\" " +
                        "\tFROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where  " + sqlQuery + " \"companyName\" = '" + comName + "' ";
            }


            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            while (rs.next()) {
                System.out.println("add user connected to user " + rs.getString(1));
                Product prod = new Product(rs.getInt(1), rs.getString(2), rs.getTimestamp(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getInt(13), rs.getString(14), rs.getString(15), rs.getInt(16), rs.getString(17), rs.getInt(18),rs.getString(19));
                prod.setImage(rs.getString(20));

                productArrayList.add(prod);
                // print the results
            }
            rs.close();
            st.close();
            conn.close();
            return productArrayList;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return null;

        }
    }

    static public ResponseEntity<Object> AddProduct(Product prod, String branchId, int companyId) {
        try {


            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("INSERT INTO C_" + companyId + ".\"PosProduct_" + branchId + "\"(\n" +
                    "\"productName\", \"buyingDay\", \"activationPeriod\", \"rPrice\",\n" +
                    "\t\"lPrice\", \"bPrice\", \"companyName\", type, \"ownerName\", serial, \"desc\",\n" +
                    "\t\"batteryLife\", \"ownerPhone\", \"ownerNI\", quantity, \"pState\", \"supplierId\" ,\"major\" , \"imgFile\" )\n" +
                    "\tVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?);", Statement.RETURN_GENERATED_KEYS);

            stmt.setString(1, prod.getProductName());
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setInt(3, Integer.valueOf(prod.getActivationPeriod()));
            stmt.setInt(4, prod.getrPrice());
            stmt.setInt(5, prod.getlPrice());
            stmt.setInt(6, prod.getbPrice());
            stmt.setString(7, prod.getCompanyName());
            stmt.setString(8, prod.getType());
            stmt.setString(9, prod.getOwnerName());
            stmt.setString(10, prod.getSerial());
            stmt.setString(11, prod.getDesc());
            stmt.setInt(12, prod.getBatteryLife());
            stmt.setString(13, prod.getOwnerPhone());
            stmt.setString(14, prod.getOwnerNI());
            stmt.setInt(15, prod.getQuantity());
            stmt.setString(16, prod.getpState());
            stmt.setInt(17, prod.getSupplierId());
            stmt.setString(18, prod.getMajor());
            stmt.setString(19, prod.getImage());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }
            long id = 0;
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    id = generatedKeys.getLong(1);
                } else {
                    throw new SQLException("Creating user failed, no ID obtained.");
                }
            }

            stmt.close();
            conn.close();
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode json = mapper.createObjectNode();
            json.put("title", "The Product  Saved");
            json.put("id", id);
            json.put("numItems", prod.getQuantity());
            json.put("transTotal", prod.getbPrice() * prod.getQuantity());
            json.put("transactionType", "Add");
            return ResponseEntity.status(201).body(json.toString());

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;

        }

    }
    //Todo ----- BarCode --------------

    public static ArrayList<Product> getProductBySearchBarcode(String trim, String branchId, int companyId, Object o) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Product> productArrayList = new ArrayList<>();
            String query = "";
            query = "SELECT \"productId\", \"productName\", \"buyingDay\", \"activationPeriod\", \"rPrice\", \"lPrice\", \"bPrice\",\n" +
                    "\"companyName\", type, \"ownerName\", serial, \"desc\", \"batteryLife\", \"ownerPhone\", \"ownerNI\", quantity,\n" +
                    "\"pState\", \"supplierId\",\"major\" , \"imgFile\" " +
                    "\tFROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where  serial = '" + trim + "' ";


            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            while (rs.next()) {
                Product prod = new Product(rs.getInt(1), rs.getString(2), rs.getTimestamp(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getInt(13), rs.getString(14), rs.getString(15),
                        rs.getInt(16), rs.getString(17), rs.getInt(18),
                        rs.getString(19));
                prod.setImage(rs.getString(20));
                productArrayList.add(prod);
                // print the results
            }
            rs.close();
            st.close();
            conn.close();
            return productArrayList;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return null;

        }
    }

    //-------------------------------------------------------------
    //---------------------------Put-------------------------------
    //-------------------------------------------------------------
    static public ResponseEntity<Object> EditProduct(Product prod, String branchId, int companyId) {
        try {


            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("UPDATE C_" + companyId + ".\"PosProduct_" + branchId + "\"\n" +
                    "\tSET  \"productName\"=?, \"buyingDay\"=?, \"activationPeriod\"=?, \"rPrice\"=?, \"lPrice\"=?, \"bPrice\"=?, \"companyName\"=?, type=?, \"ownerName\"=?, serial=?, \"desc\"=?, \"batteryLife\"=?, \"ownerPhone\"=?, \"ownerNI\"=?, quantity=?, \"pState\"=?, \"supplierId\"=?, major=?, \"imgFile\"=? \n" +
                    "\tWHERE \"productId\"=?;");

            stmt.setString(1, prod.getProductName());
            stmt.setTimestamp(2,prod.getBuyingDay());
            stmt.setInt(3, Integer.valueOf(prod.getActivationPeriod()));
            stmt.setInt(4, prod.getrPrice());
            stmt.setInt(5, prod.getlPrice());
            stmt.setInt(6, prod.getbPrice());
            stmt.setString(7, prod.getCompanyName());
            stmt.setString(8, prod.getType());
            stmt.setString(9, prod.getOwnerName());
            stmt.setString(10, prod.getSerial());
            stmt.setString(11, prod.getDesc());
            stmt.setInt(12, prod.getBatteryLife());
            stmt.setString(13, prod.getOwnerPhone());
            stmt.setString(14, prod.getOwnerNI());
            stmt.setInt(15, prod.getQuantity());
            stmt.setString(16, prod.getpState());
            stmt.setInt(17, prod.getSupplierId());
            stmt.setString(18, prod.getMajor());
            stmt.setString(19, prod.getImage());
            //Id
            stmt.setInt(20, prod.getProductId());
            System.out.println(stmt);
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }


            stmt.close();
            conn.close();
            System.out.println("Conniction closed");
            // create `ObjectMapper` instance
            ObjectMapper mapper = new ObjectMapper();

            // create a JSON object
            ObjectNode json = mapper.createObjectNode();
            json.put("title", "The Product Edit Saved");
            json.put("id", prod.getProductId());
            json.put("numItems", prod.getQuantity());
            json.put("transTotal", prod.getbPrice() * prod.getQuantity());
            json.put("transactionType", "Update");

            // convert `ObjectNode` to pretty-print JSON
            // without pretty-print, use `user.toString()` method
            String jsonS = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);

            // print json
            System.out.println(jsonS);

            return ResponseEntity.status(HttpStatus.OK).body(json.toString());
            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("Nothing");

            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);

        }

    }


}
