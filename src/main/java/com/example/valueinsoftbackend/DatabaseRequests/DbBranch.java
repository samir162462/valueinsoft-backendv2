package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;


@Component
public class DbBranch {


    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DbBranch(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Branch> branchRowMapper = new RowMapper<>() {
        @Override
        public Branch mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Branch(
                    rs.getInt("branchId"),
                    rs.getInt("companyId"),
                    rs.getString("branchName"),
                    rs.getString("branchLocation"),
                    rs.getTimestamp("branchEstTime")
            );
        }
    };

/*
    public static ArrayList<Branch> getBranchByCompanyId(int id) {
        ArrayList<Branch> bsList = new ArrayList<>();

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT \"branchId\", \"branchName\", \"branchLocation\", \"companyId\", \"branchEstTime\"\n" +
                    "\tFROM public.\"Branch\" where \"companyId\" = " + id + ";";

            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));



                Branch branch = new Branch(rs.getInt(1), rs.getInt(4), rs.getString(2), rs.getString(3), rs.getTimestamp(5));
                bsList.add(branch);
                // print the results
            }

            rs.close();
            st.close();
            conn.close();
            return bsList;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }


 */
    public List<Branch> getBranchByCompanyId(int companyId) {
        String sql = "SELECT \"branchId\", \"branchName\", \"branchLocation\", \"companyId\", \"branchEstTime\" " +
                "FROM public.\"Branch\" WHERE \"companyId\" = ?";
        return jdbcTemplate.query(sql, branchRowMapper, companyId);
    }



/*
    public static ArrayList<Branch> getAllBranches() {
        ArrayList<Branch> bsList = new ArrayList<>();

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT \"branchId\", \"branchName\", \"branchLocation\", \"companyId\", \"branchEstTime\"\n" +
                    "\tFROM public.\"Branch\" ;";

            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("Connected to branch " + rs.getString(1));


                Branch branch = new Branch(rs.getInt(1), rs.getInt(4), rs.getString(2), rs.getString(3), rs.getTimestamp(5));
                bsList.add(branch);
                // print the results
            }

            rs.close();
            st.close();
            conn.close();
            return bsList;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }

    public static int getBranchIdByCompanyNameAndBranchName(int companyId, String branchName) {
        ArrayList<Branch> bsList = new ArrayList<>();

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT \"branchId\"\n" +
                    "\tFROM public.\"Branch\" where \"companyId\" = " + companyId + " And \"branchName\" = '" + branchName + "';";

            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                return rs.getInt(1);
                // print the results
            }


            rs.close();
            st.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return -1;

    }
    private static boolean checkExistBranchName(String BName)
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT  \"branchName\"\n" +
                    "\tFROM public.\"Branch\" where \"branchName\" = '"+BName+"';";

            // create the java statement
            Statement st = conn.createStatement();

            ResultSet rs = st.executeQuery(query);

            while (rs.next())
            {
                return true;


                // print the results
            }

            rs.close();
            st.close();
            conn.close();
        }catch (Exception e)
        {
            System.out.println(" no user exist");
            return true;

        }
        return false;

    }

    static public String AddBranch(String branchName, String branchLocation, int companyId) {
        try {


            if (checkExistBranchName(branchName))
            {
                return "The Branch Name existed!" ;
            }

            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("INSERT INTO public.\"Branch\"(\n" +
                    " \"branchName\", \"branchLocation\", \"companyId\", \"branchEstTime\")\n" +
                    "\tVALUES ( ?, ?, ?, ?)");

            stmt.setString(1, branchName);
            stmt.setString(2, branchLocation);
            stmt.setInt(3, companyId);
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));

            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted");
            conn.close();

            // Crate Branch tables for new branch in schema
            int branchId = getBranchIdByCompanyNameAndBranchName(companyId,branchName);
            System.out.println("branchId => "+branchId);
            CreatePosProductTable(branchId,companyId);
            CreateOrderTable(branchId,companyId);
            CreateOrderDetailsTable(branchId,companyId);
            CreateSupplierTable(branchId,companyId);
            CreateTransactionTable(branchId,companyId);
            CreateSupplierTable(branchId,companyId);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the user not added bs error!";

        }

        return "the Branch added!";
    }


    //---------------------Create DB Branch Section----------------------------
    static public boolean CreatePosProductTable(int branchId, int comId) {
        try {


            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS C_"+comId+".\"PosProduct_"+branchId+"\"\n" +
                    "(\n" +
                    "    \"productId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 11110000 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                    "    \"productName\" character varying(30) COLLATE pg_catalog.\"default\",\n" +
                    "    \"buyingDay\" timestamp without time zone,\n" +
                    "    \"activationPeriod\" integer,\n" +
                    "    \"rPrice\" integer,\n" +
                    "    \"lPrice\" integer,\n" +
                    "    \"bPrice\" integer,\n" +
                    "    \"companyName\" character varying(30) COLLATE pg_catalog.\"default\",\n" +
                    "    type character varying(15) COLLATE pg_catalog.\"default\",\n" +
                    "    \"ownerName\" character varying(20) COLLATE pg_catalog.\"default\",\n" +
                    "    serial character varying(35) COLLATE pg_catalog.\"default\",\n" +
                    "    \"desc\" character varying(60) COLLATE pg_catalog.\"default\",\n" +
                    "    \"batteryLife\" integer,\n" +
                    "    \"ownerPhone\" character varying(14) COLLATE pg_catalog.\"default\",\n" +
                    "    \"ownerNI\" character varying(18) COLLATE pg_catalog.\"default\",\n" +
                    "    quantity integer,\n" +
                    "    \"pState\" character varying(10) COLLATE pg_catalog.\"default\",\n" +
                    "    \"supplierId\" integer,\n" +
                    "    \"major\" character varying(30) COLLATE pg_catalog.\"default\",\n" +
                    "    \"imgFile\" text COLLATE pg_catalog.\"default\"," +
                    "    CONSTRAINT \"PosProduct_pkey-"+branchId+"\" PRIMARY KEY (\"productId\")\n" +
                    ")\n" +
                    "WITH (\n" +
                    "    OIDS = FALSE\n" +
                    ")\n" +
                    "TABLESPACE pg_default;\n" +
                    "\n" +
                    "");


            int i = stmt.executeUpdate();
            System.out.println(i + " Table Established in PosProduct_"+branchId);

            stmt.close();
            conn.close();

        } catch (Exception e) {
            System.out.println(e.getMessage());
        return false;
        }
        return true;
    }

    static public boolean CreateOrderTable(int branchId,int comId ) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS C_"+comId+".\"PosOrder_"+branchId+"\"\n" +
                    "(\n" +
                    "    \"orderId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 106245000 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                    "    \"orderTime\" timestamp without time zone NOT NULL,\n" +
                    "    \"clientName\" character varying COLLATE pg_catalog.\"default\",\n" +
                    "    \"orderType\" character varying(10) COLLATE pg_catalog.\"default\",\n" +
                    "    \"orderDiscount\" integer,\n" +
                    "    \"orderTotal\" integer,\n" +
                    "    \"salesUser\" character varying COLLATE pg_catalog.\"default\",\n" +
                    "    \"clientId\" integer," +
                    "    \"orderIncome\" integer," +
                    "    \"orderBouncedBack\" integer," +
                    "    CONSTRAINT \"PosOrder_pkey_"+branchId+"\" PRIMARY KEY (\"orderId\")\n" +
                    ")\n" +
                    "\n" +
                    "TABLESPACE pg_default;\n" +
                    "\n" +
                    ";");

            int i = stmt.executeUpdate();
            System.out.println(i + " Table Established in PosOrder_"+branchId);
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }

    static public boolean CreateSupplierTable(int branchId,int comId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS C_"+comId+".\"supplier_"+branchId+"\"\n" +
                    "(\n" +
                    "    \"supplierId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1000 MINVALUE 1000 MAXVALUE 2147483647 CACHE 1 ),\n" +
                    "    \"SupplierName\" character varying COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                    "    \"supplierPhone1\" character varying(14) COLLATE pg_catalog.\"default\",\n" +
                    "    \"supplierPhone2\" character varying(14) COLLATE pg_catalog.\"default\",\n" +
                    "    \"SupplierLocation\" character varying COLLATE pg_catalog.\"default\",\n" +
                    "    \"suplierMajor\" character varying(20) COLLATE pg_catalog.\"default\",\n" +
                    "    \"supplierRemainig\" integer DEFAULT 0,\n" +
                    "    \"supplierTotalSales\" integer DEFAULT 0," +
                    "    CONSTRAINT \"supplier _pkey_"+branchId+"\" PRIMARY KEY (\"supplierId\")\n" +
                    ")" +
                    "\n" +
                    "TABLESPACE pg_default;\n" +
                    "\n" +
                    "");

            int i = stmt.executeUpdate();
            System.out.println(i + " Table Established in Supplier_"+branchId);
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }
    static public boolean CreateTransactionTable(int branchId,int comId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS C_"+comId+".\"InventoryTransactions_"+branchId+"\"\n" +
                    "(\n" +
                    "  \"transId\" integer NOT NULL GENERATED BY DEFAULT AS IDENTITY ( INCREMENT 1 START 1000 MINVALUE 1000 MAXVALUE 2147483647 CACHE 1 ),\n" +
                    "    \"productId\" integer,\n" +
                    "    \"userName\" character varying(15) COLLATE pg_catalog.\"default\",\n" +
                    "    \"supplierId\" integer,\n" +
                    "    \"transactionType\" character varying(15) COLLATE pg_catalog.\"default\",\n" +
                    "    \"NumItems\" integer,\n" +
                    "    \"transTotal\" integer,\n" +
                    "    \"payType\" character varying COLLATE pg_catalog.\"default\",\n" +
                    "    \"time\" timestamp without time zone,\n" +
                    "    \"RemainingAmount\" integer,\n" +
                    "    CONSTRAINT inventorytransactions_pkey_"+branchId+" PRIMARY KEY (\"transId\"),\n" +
                    "    CONSTRAINT \"product_invTrans\" FOREIGN KEY (\"productId\")\n" +
                    "        REFERENCES C_"+comId+".\"PosProduct_"+branchId+"\" (\"productId\") MATCH SIMPLE\n" +
                    "        ON UPDATE NO ACTION\n" +
                    "        ON DELETE NO ACTION\n" +
                    "        NOT VALID\n" +
                    ")\n" +
                    "\n" +
                    "TABLESPACE pg_default;\n" +
                    "\n" +
                    "");

            int i = stmt.executeUpdate();
            System.out.println(i + " Table Established in TransTable"+branchId);
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }
    static public boolean CreateOrderDetailsTable(int branchId,int comId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS C_"+comId+".\"PosOrderDetail_"+branchId+"\"\n" +
                    "(\n" +
                    "    \"orderDetailsId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                    "    \"itemId\" integer,\n" +
                    "    \"itemName\" character varying COLLATE pg_catalog.\"default\",\n" +
                    "    quantity integer,\n" +
                    "    price integer,\n" +
                    "    total integer,\n" +
                    "    \"orderId\" integer,\n" +
                    "    \"productId\" integer,\n"+
                    "    \"bouncedBack\" integer,\n"+
                    "    CONSTRAINT \"orderDetail_pkey_"+branchId+"\" PRIMARY KEY (\"orderDetailsId\"),\n" +
                    "    CONSTRAINT \"OrderHasDetails_"+branchId+"\" FOREIGN KEY (\"orderId\")\n" +
                    "        REFERENCES C_"+comId+".\"PosOrder_"+branchId+"\" (\"orderId\") MATCH SIMPLE\n" +
                    "        ON UPDATE CASCADE\n" +
                    "        ON DELETE CASCADE\n" +
                    "        NOT VALID\n" +
                    ")\n" +
                    "TABLESPACE pg_default;" +
                    "");

            int i = stmt.executeUpdate();
            System.out.println(i + " Table Established in PosOrderDetails_"+branchId);
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }
    //Delete
    static public boolean deleteBranch(int branchId,String comId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("Begin;\n" +
                    "DELETE FROM public.\"Branch\"\n" +
                    "\tWHERE \"branchId\" = "+branchId+";\n" +
                    "DROP TABLE IF EXISTS "+comId+".\"PosOrderDetail_"+branchId+"\" CASCADE;\n" +
                    "DROP TABLE IF EXISTS "+comId+".\"PosOrder_"+branchId+"\" CASCADE;\n" +
                    "DROP TABLE IF EXISTS "+comId+".\"PosProduct_"+branchId+"\" CASCADE;\n" +
                    "DROP TABLE IF EXISTS "+comId+".\"InventoryTransactions_"+branchId+"\" CASCADE;\n" +
                    "DROP TABLE IF EXISTS "+comId+".\"supplier_"+branchId+"\" CASCADE;\n" +
                    "\n" +
                    "commit;");

            int i = stmt.executeUpdate();
            System.out.println(branchId + " Deleted! ");
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }
*/
public List<Branch> getAllBranches() {
    String sql = "SELECT \"branchId\", \"branchName\", \"branchLocation\", \"companyId\", \"branchEstTime\" " +
            "FROM public.\"Branch\"";
    return jdbcTemplate.query(sql, branchRowMapper);
}

    public int getBranchIdByCompanyNameAndBranchName(int companyId, String branchName) {
        String sql = "SELECT \"branchId\" FROM public.\"Branch\" WHERE \"companyId\" = ? AND \"branchName\" = ?";
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, companyId, branchName);
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean checkExistBranchName(String branchName) {
        String sql = "SELECT COUNT(*) FROM public.\"Branch\" WHERE \"branchName\" = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, branchName);
        return count != null && count > 0;
    }

    public  String addBranch(String branchName, String branchLocation, int companyId) {
        if (checkExistBranchName(branchName)) {
            return "The Branch Name existed!";
        }

        String sql = "INSERT INTO public.\"Branch\" (\"branchName\", \"branchLocation\", \"companyId\", \"branchEstTime\") " +
                "VALUES (?, ?, ?, ?)";
        int result = jdbcTemplate.update(sql, branchName, branchLocation, companyId, new Timestamp(System.currentTimeMillis()));

        if (result > 0) {
            int branchId = getBranchIdByCompanyNameAndBranchName(companyId, branchName);
            CreatePosProductTable(branchId, companyId);
            CreateOrderTable(branchId, companyId);
            CreateOrderDetailsTable(branchId, companyId);
            CreateSupplierTable(branchId, companyId);
            CreateTransactionTable(branchId, companyId);
            return "The Branch added!";
        } else {
            return "The branch not added due to error!";
        }
    }

    public boolean deleteBranch(int branchId, String companyId) {
        String sql = "BEGIN; " +
                "DELETE FROM public.\"Branch\" WHERE \"branchId\" = ?; " +
                "DROP TABLE IF EXISTS " + companyId + ".\"PosOrderDetail_" + branchId + "\" CASCADE; " +
                "DROP TABLE IF EXISTS " + companyId + ".\"PosOrder_" + branchId + "\" CASCADE; " +
                "DROP TABLE IF EXISTS " + companyId + ".\"PosProduct_" + branchId + "\" CASCADE; " +
                "DROP TABLE IF EXISTS " + companyId + ".\"InventoryTransactions_" + branchId + "\" CASCADE; " +
                "DROP TABLE IF EXISTS " + companyId + ".\"supplier_" + branchId + "\" CASCADE; " +
                "COMMIT;";

        try {
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    // --- Schema/Table Creation Methods (still using JdbcTemplate) ---
    public boolean CreatePosProductTable(int branchId, int companyId) {
        String sql = "CREATE TABLE IF NOT EXISTS C_" + companyId + ".\"PosProduct_" + branchId + "\" (" +
                "    \"productId\" SERIAL PRIMARY KEY," +
                "    \"productName\" VARCHAR(30)," +
                "    \"buyingDay\" TIMESTAMP," +
                "    \"activationPeriod\" INTEGER," +
                "    \"rPrice\" INTEGER," +
                "    \"lPrice\" INTEGER," +
                "    \"bPrice\" INTEGER," +
                "    \"companyName\" VARCHAR(30)," +
                "    \"type\" VARCHAR(15)," +
                "    \"ownerName\" VARCHAR(20)," +
                "    \"serial\" VARCHAR(35)," +
                "    \"desc\" VARCHAR(60)," +
                "    \"batteryLife\" INTEGER," +
                "    \"ownerPhone\" VARCHAR(14)," +
                "    \"ownerNI\" VARCHAR(18)," +
                "    \"quantity\" INTEGER," +
                "    \"pState\" VARCHAR(10)," +
                "    \"supplierId\" INTEGER," +
                "    \"major\" VARCHAR(30)," +
                "    \"imgFile\" TEXT" +
                ")";
        try {
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean CreateOrderTable(int branchId, int companyId) {
        String sql = "CREATE TABLE IF NOT EXISTS C_" + companyId + ".\"PosOrder_" + branchId + "\" (" +
                "    \"orderId\" SERIAL PRIMARY KEY," +
                "    \"orderTime\" TIMESTAMP NOT NULL," +
                "    \"clientName\" VARCHAR," +
                "    \"orderType\" VARCHAR(10)," +
                "    \"orderDiscount\" INTEGER," +
                "    \"orderTotal\" INTEGER," +
                "    \"salesUser\" VARCHAR," +
                "    \"clientId\" INTEGER," +
                "    \"orderIncome\" INTEGER," +
                "    \"orderBouncedBack\" INTEGER" +
                ")";
        try {
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean CreateSupplierTable(int branchId, int companyId) {
        String sql = "CREATE TABLE IF NOT EXISTS C_" + companyId + ".\"supplier_" + branchId + "\" (" +
                "    \"supplierId\" SERIAL PRIMARY KEY," +
                "    \"SupplierName\" VARCHAR NOT NULL," +
                "    \"supplierPhone1\" VARCHAR(14)," +
                "    \"supplierPhone2\" VARCHAR(14)," +
                "    \"SupplierLocation\" VARCHAR," +
                "    \"suplierMajor\" VARCHAR(20)," +
                "    \"supplierRemainig\" INTEGER DEFAULT 0," +
                "    \"supplierTotalSales\" INTEGER DEFAULT 0" +
                ")";
        try {
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean CreateTransactionTable(int branchId, int companyId) {
        String sql = "CREATE TABLE IF NOT EXISTS C_" + companyId + ".\"InventoryTransactions_" + branchId + "\" (" +
                "    \"transId\" SERIAL PRIMARY KEY," +
                "    \"productId\" INTEGER," +
                "    \"userName\" VARCHAR(15)," +
                "    \"supplierId\" INTEGER," +
                "    \"transactionType\" VARCHAR(15)," +
                "    \"NumItems\" INTEGER," +
                "    \"transTotal\" INTEGER," +
                "    \"payType\" VARCHAR," +
                "    \"time\" TIMESTAMP," +
                "    \"RemainingAmount\" INTEGER," +
                "    FOREIGN KEY (\"productId\") REFERENCES C_" + companyId + ".\"PosProduct_" + branchId + "\" (\"productId\")" +
                ")";
        try {
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean CreateOrderDetailsTable(int branchId, int companyId) {
        String sql = "CREATE TABLE IF NOT EXISTS C_" + companyId + ".\"PosOrderDetail_" + branchId + "\" (" +
                "    \"orderDetailsId\" SERIAL PRIMARY KEY," +
                "    \"itemId\" INTEGER," +
                "    \"itemName\" VARCHAR," +
                "    \"quantity\" INTEGER," +
                "    \"price\" INTEGER," +
                "    \"total\" INTEGER," +
                "    \"orderId\" INTEGER," +
                "    \"productId\" INTEGER," +
                "    \"bouncedBack\" INTEGER," +
                "    FOREIGN KEY (\"orderId\") REFERENCES C_" + companyId + ".\"PosOrder_" + branchId + "\" (\"orderId\") ON DELETE CASCADE ON UPDATE CASCADE" +
                ")";
        try {
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }




}
