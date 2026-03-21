/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;


import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.Model.ResponseModel.ResponsePagination;
import com.example.valueinsoftbackend.Model.Util.ProductUtilNames;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.example.valueinsoftbackend.util.PageHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;

@Slf4j
@Repository
public class DbPosProduct {

    JdbcTemplate jdbcTemplate;

    @Autowired
    public DbPosProduct(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public class ProductMapper implements RowMapper<Product> {
        boolean withImage;

        public ProductMapper(boolean withImage) {
            this.withImage = withImage;
        }

        @Override
        public Product mapRow(ResultSet rs, int rowNum) throws SQLException {
            Product product = new Product(
                    rs.getInt("productId"),
                    rs.getString("productName"),
                    rs.getTimestamp("buyingDay"),
                    rs.getString("activationPeriod"),
                    rs.getInt("rPrice"),
                    rs.getInt("lPrice"),
                    rs.getInt("bPrice"),
                    rs.getString("companyName"),
                    rs.getString("type"),
                    rs.getString("ownerName"),
                    rs.getString("serial"),
                    rs.getString("desc"),
                    rs.getInt("batteryLife"),
                    rs.getString("ownerPhone"),
                    rs.getString("ownerNI"),
                    rs.getInt("quantity"),
                    rs.getString("pState"),
                    rs.getInt("supplierId"),
                    rs.getString("major"),
                    withImage? rs.getString("imgFile"):null
            );
            return product;
        }
    }

    public int countSQL(String sql) {
        // Build a safe count query by wrapping the provided query as a subselect.
        // Trim any trailing semicolon first.
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String countQuery = "SELECT count(*) FROM (" + trimmed + ") AS sub_count";
        System.out.println(countQuery);
        return jdbcTemplate.queryForObject(countQuery, Integer.class);
    }

    // Overload to support count with parameters for prepared queries
    private int countSQL(String sql, Object[] args) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String countQuery = "SELECT count(*) FROM (" + trimmed + ") AS sub_count";
        System.out.println(countQuery);
        return jdbcTemplate.queryForObject(countQuery, args, Integer.class);
    }

    public ResponsePagination<Product> getProductBySearchText(String[] text, String branchId, int companyId, ProductFilter productFilter, PageHandler pageHandler) {
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
        log.info("Inside Get Product By Search Text : " + Arrays.toString(text));
=======
=======
>>>>>>> theirs
=======
>>>>>>> theirs
=======
>>>>>>> theirs
=======
>>>>>>> theirs
=======
>>>>>>> theirs
=======
>>>>>>> theirs
        log.info("Inside Get Product By Search Text : {}", Arrays.toString(text));
        int count = 0;
>>>>>>> theirs
        try {
            String sqlQuery = "";
            if (productFilter != null) {
                sqlQuery = productFilter.sqlString();
            } else {
                sqlQuery = "  \"quantity\" <> 0 AND ";
                log.info("Inside Get Product By Search Text : No Filter");
            }
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours

<<<<<<< ours
            String baseQuery = "SELECT * FROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where " + sqlQuery;
            StringBuilder qy = new StringBuilder(baseQuery);

            ArrayList<Object> params = new ArrayList<>();
=======
>>>>>>> theirs

            // Build conditions for tokens using parameterized LIKE and LOWER for case-insensitive matching
            ArrayList<String> conds = new ArrayList<>();
            if (text != null && text.length > 0) {
                for (int i = 0; i < text.length; i++) {
                    String token = text[i] == null ? "" : text[i].trim();
                    if (token.isEmpty()) continue;
                    // normalize and escape single quotes just in case (though we use parameters)
                    String safe = token.replace("'", "''").toLowerCase();
                    // Use ILIKE (case-insensitive) and pass raw token as parameter (JdbcTemplate will handle quoting)
                    conds.add("\"productName\" ILIKE ?");
                    params.add("%" + token + "%");
                }
            }
=======
>>>>>>> theirs

            if (!conds.isEmpty()) {
                // If baseQuery already ends with AND (as in default) we can append directly
                qy.append("(");
                qy.append(String.join(" AND ", conds));
                qy.append(")");

                // count with same params
                int count = countSQL(qy.toString(), params.toArray());

                // append paging
                qy.append(pageHandler.handlePageSqlQuery()).append(" ;");

                System.out.println("SQL: " + qy.toString());
                System.out.println("Params: " + params);
                log.debug("Final product search SQL: {}", qy.toString());

                ArrayList<Product> results = (ArrayList<Product>) jdbcTemplate.query(qy.toString(), params.toArray(), new ProductMapper(true));

                // Diagnostic: if no results, try logging some candidate matches to help debugging
                if ((results == null || results.isEmpty()) && !params.isEmpty()) {
                    try {
                        System.out.println("No results from search; running diagnostic queries for tokens: " + params);
                        for (Object p : params) {
                            String tok = p == null ? "" : p.toString();
                            // build a simple diagnostic query to show up to 5 product names that match the token
                            String diag = "SELECT \"productName\" FROM C_" + companyId + ".\"PosProduct_" + branchId + "\" WHERE \"productName\" ILIKE ? LIMIT 5;";
                            try {
                                java.util.List<String> names = jdbcTemplate.queryForList(diag, new Object[]{tok}, String.class);
                                System.out.println("Diagnostic matches for token '" + tok + "' -> " + names);
                            } catch (Exception ignore) {
                                System.out.println("Diagnostic query failed for token: " + tok + " -> " + ignore.getMessage());
                            }
                        }
                        // Also show first 20 product names (with quantity) to inspect stored values
                        try {
                            String sample = "SELECT \"productName\" FROM C_" + companyId + ".\"PosProduct_" + branchId + "\" WHERE \"quantity\" <> 0 LIMIT 20;";
                            java.util.List<String> sampleNames = jdbcTemplate.queryForList(sample, String.class);
                            System.out.println("Sample product names (quantity<>0): " + sampleNames);
                        } catch (Exception ignore) {
                            System.out.println("Diagnostic sample query failed -> " + ignore.getMessage());
                        }
                    } catch (Exception ignored) {}

                    // FALLBACK: if no results and we used the default quantity filter (i.e., productFilter == null),
                    // run the same token search WITHOUT the quantity filter and return those results so the client can see out-of-stock matches.
                    if (productFilter == null) {
                        try {
                            System.out.println("No in-stock matches found. Running fallback search without quantity filter.");
                            String altBase = "SELECT * FROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where ";
                            StringBuilder altQy = new StringBuilder(altBase);
                            altQy.append("(");
                            altQy.append(String.join(" AND ", conds));
                            altQy.append(")");

                            int altCount = countSQL(altQy.toString(), params.toArray());
                            altQy.append(pageHandler.handlePageSqlQuery()).append(" ;");
                            System.out.println("Fallback SQL: " + altQy.toString());
                            ArrayList<Product> altResults = (ArrayList<Product>) jdbcTemplate.query(altQy.toString(), params.toArray(), new ProductMapper(true));
                            System.out.println("Fallback results count: " + (altResults == null ? 0 : altResults.size()));
                            return new ResponsePagination<Product>(altResults, altCount);
                        } catch (Exception fallbackEx) {
                            System.out.println("Fallback search failed: " + fallbackEx.getMessage());
                        }
                    }
                }
=======
>>>>>>> theirs
=======
>>>>>>> theirs
=======
>>>>>>> theirs

                return new ResponsePagination<Product>(results, count);
            } else {
                // no search tokens, just return empty pagination or full range depending on original semantics
                int count = countSQL(qy.toString());
                qy.append(pageHandler.handlePageSqlQuery()).append(" ;");
                ArrayList<Product> results = (ArrayList<Product>) jdbcTemplate.query(qy.toString(), new Object[]{}, new ProductMapper(true));
                return new ResponsePagination<Product>(results, count);
=======
=======

>>>>>>> theirs
            String query = "SELECT * " +
                    "\tFROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where " + sqlQuery + "  ";
            StringBuilder qy = new StringBuilder(query);
            String[] cleanedWords = Arrays.stream(text)
                    .map(String::trim)
                    .filter(word -> !word.isEmpty())
                    .toArray(String[]::new);
            System.out.println(qy);
            if (cleanedWords.length > 0) {
                for (int i = 0; i < cleanedWords.length; i++) {
                    String normalizedWord = cleanedWords[i].toLowerCase();
                    String clause = "LOWER(\"productName\") LIKE '%" + normalizedWord + "%'";
                    if (i == 0) {
                        qy.append("(").append(clause).append(")");
                        continue;
                    }
                    qy.append(" And ").append(clause);
                }

                count = countSQL(qy.toString());
                qy.append(pageHandler.handlePageSqlQuery()).append(" ;");
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
<<<<<<< ours
>>>>>>> theirs
=======
>>>>>>> theirs
=======
>>>>>>> theirs
=======
>>>>>>> theirs
=======
>>>>>>> theirs
=======
>>>>>>> theirs
=======
>>>>>>> theirs
            }

        } catch (Exception e) {
            log.info("err : {}", e.getMessage());
            throw new RuntimeException("Cant handle Search in Products by text");
        }
    }

    public ArrayList<Product> getProductsAllRange(String branchId, int companyId, ProductFilter productFilter) {
        log.info("Inside Get Product By Search  Range: " + productFilter);
        try {
            String sqlQuery = "";
            if (productFilter != null) sqlQuery = productFilter.sqlString();
            else log.info("Inside Get Product By Search Text : No Filter");
            String query = "SELECT \"productId\", \"productName\", \"buyingDay\", \"activationPeriod\", \"rPrice\", \"lPrice\", \"bPrice\", \"companyName\",\n" +
                    "\t\ttype, \"ownerName\", serial, \"desc\", \"batteryLife\", \"ownerPhone\", \"ownerNI\", quantity, \"pState\", \"supplierId\", major" +
                    "\tFROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where " + sqlQuery + "   \"productId\" > 0  ;";
            return (ArrayList<Product>) jdbcTemplate.query(query, new Object[]{}, new ProductMapper(true));
        } catch (Exception e) {
            log.info("err : " + e.getMessage());
            throw new RuntimeException("Cant handle Search in Products by Range");
        }

    }

    public Product getProductById(int supplierId, int branchId, int companyId) {
        log.info("Inside getProductById Function");
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
                            rs.getString(19),
                            rs.getString("imgFile"));
                  //  pt.setImage(rs.getString(20));
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
        log.info("Inside getProductNames Function");

        try {

            // guard against empty input
            if (text == null || text.trim().isEmpty()) return ResponseEntity.status(200).body(new ArrayList<>());

            // escape single quotes and prepare safe search token
            String safe = text.replace("'", "''");
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT  DISTINCT ON (\"productName\") \"productName\" ,\"companyName\" , type ,major \n" +
                    "\tFROM c_" + companyId + ".\"PosProduct_" + branchId + "\" where \"productName\" ILIKE '%" + safe + "%' ORDER BY \n" +
                    "        \"productName\";";
            System.out.println(query);
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            ArrayList<ProductUtilNames> productNames = new ArrayList<>();

            try {
                while (rs.next()) {
                    ProductUtilNames productUtilNames = new ProductUtilNames(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
                    productNames.add(productUtilNames);
                }

            } catch (Exception e) {
                System.out.println("getProductById " + e.getMessage());
                return ResponseEntity.status(406).body("errorIn getProductNames To array" + e.getMessage());
            }

            rs.close();
            st.close();
            conn.close();
            return ResponseEntity.status(200).body(productNames);

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return ResponseEntity.status(406).body("errorIn getProductNames To array" + e.getMessage());

        }

    }

    public ResponsePagination<Product> getProductBySearchCompanyName(String comName, String branchId, int companyId, ProductFilter productFilter, PageHandler pageHandler) {
        log.info("Inside getProductBySearchCompanyName Function");

        try {
            int count = 0;
            String query = "";

            String sqlQuery = "";
            if (productFilter != null) {
                sqlQuery = productFilter.sqlString();
            } else {
                System.out.println("No Filter");
            }
            if (comName.contains("All")) {
                query = "SELECT * " +
                        "\tFROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where  " + sqlQuery + " \"type\" = '" + comName.split(" ")[1] + "' ";
            } else {
                query = "SELECT * " +
                        "\tFROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where  " + sqlQuery + " \"companyName\" = '" + comName + "' ";
            }
            System.out.println(query);
            count = countSQL(query);

            return new ResponsePagination<Product>(
                    (ArrayList<Product>) jdbcTemplate.query(
                            query, new Object[]{}, new ProductMapper(true)), count);

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            throw new RuntimeException("Cant handle Search in Products by CompanyName");

        }
    }

    static public ResponseEntity<Object> AddProduct(Product prod, String branchId, int companyId) {
        log.info("Inside AddProduct Function");

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
        log.info("Inside getProductBySearchBarcode Function");

        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Product> productArrayList = new ArrayList<>();
            String query = "";
            // escape single quotes in barcode
            String safeTrim = (trim == null) ? "" : trim.replace("'", "''");
            query = "SELECT \"productId\", \"productName\", \"buyingDay\", \"activationPeriod\", \"rPrice\", \"lPrice\", \"bPrice\",\n" +
                    "\"companyName\", type, \"ownerName\", serial, \"desc\", \"batteryLife\", \"ownerPhone\", \"ownerNI\", quantity,\n" +
                    "\"pState\", \"supplierId\",\"major\" , \"imgFile\" " +
                    "\tFROM C_" + companyId + ".\"PosProduct_" + branchId + "\" where  serial = '" + safeTrim + "' ";


            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            while (rs.next()) {
                Product prod = new Product(rs.getInt(1), rs.getString(2), rs.getTimestamp(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getInt(13), rs.getString(14), rs.getString(15),
                        rs.getInt(16), rs.getString(17), rs.getInt(18),
                        rs.getString(19), rs.getString("imgFile"));
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
        log.info("Inside EditProduct Function");

        try {


            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("UPDATE C_" + companyId + ".\"PosProduct_" + branchId + "\"\n" +
                    "\tSET  \"productName\"=?, \"buyingDay\"=?, \"activationPeriod\"=?, \"rPrice\"=?, \"lPrice\"=?, \"bPrice\"=?, \"companyName\"=?, type=?, \"ownerName\"=?, serial=?, \"desc\"=?, \"batteryLife\"=?, \"ownerPhone\"=?, \"ownerNI\"=?, quantity=?, \"pState\"=?, \"supplierId\"=?, major=?, \"imgFile\"=? \n" +
                    "\tWHERE \"productId\"=?;");

            stmt.setString(1, prod.getProductName());
            stmt.setTimestamp(2, prod.getBuyingDay());
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
