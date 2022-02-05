package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;

import java.sql.*;
import java.util.ArrayList;

public class DbBranch {


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
            CreateBranchTable(branchId);
            CreateOrderTable(branchId);
            CreateOrderDetailsTable(branchId);
            CreateSupplierTable(branchId);
            CreateTransactionTable(branchId);
            CreateSupplierTable(branchId);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the user not added bs error!";

        }

        return "the Branch added!";
    }


    //---------------------Create DB Branch Section----------------------------
    static public boolean CreateBranchTable(int branchId) {
        try {


            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS public.\"PosProduct_"+branchId+"\"\n" +
                    "(\n" +
                    "    \"productId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
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
                    "    CONSTRAINT \"PosProduct_pkey-"+branchId+"\" PRIMARY KEY (\"productId\")\n" +
                    ")\n" +
                    "WITH (\n" +
                    "    OIDS = FALSE\n" +
                    ")\n" +
                    "TABLESPACE pg_default;\n" +
                    "\n" +
                    "ALTER TABLE public.\"PosProduct\"\n" +
                    "    OWNER to "+ ValueinsoftBackendApplication.DatabaseOwner+" ;");


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

    static public boolean CreateOrderTable(int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS public.\"PosOrder_"+branchId+"\"\n" +
                    "(\n" +
                    "    \"orderId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                    "    \"orderTime\" timestamp without time zone NOT NULL,\n" +
                    "    \"clientName\" character varying COLLATE pg_catalog.\"default\",\n" +
                    "    \"orderType\" character varying(10) COLLATE pg_catalog.\"default\",\n" +
                    "    \"orderDiscount\" integer,\n" +
                    "    \"orderTotal\" integer,\n" +
                    "    \"salesUser\" character varying COLLATE pg_catalog.\"default\",\n" +
                    "    \"clientId\" integer," +
                    "    \"orderIncome\" integer," +
                    "    CONSTRAINT \"PosOrder_pkey_"+branchId+"\" PRIMARY KEY (\"orderId\")\n" +
                    ")\n" +
                    "\n" +
                    "TABLESPACE pg_default;\n" +
                    "\n" +
                    "ALTER TABLE public.\"PosOrder\"" +
                    "    OWNER to "+ ValueinsoftBackendApplication.DatabaseOwner+" ;");

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

    static public boolean CreateSupplierTable(int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS public.\"supplier_"+branchId+"\"\n" +
                    "(\n" +
                    "    \"supplierId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1000 MINVALUE 1000 MAXVALUE 2147483647 CACHE 1 ),\n" +
                    "    \"SupplierName\" character varying COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                    "    \"supplierPhone1\" character varying(14) COLLATE pg_catalog.\"default\",\n" +
                    "    \"supplierPhone2\" character varying(14) COLLATE pg_catalog.\"default\",\n" +
                    "    \"SupplierLocation\" character varying COLLATE pg_catalog.\"default\",\n" +
                    "    \"suplierMajor\" character varying(20) COLLATE pg_catalog.\"default\",\n" +
                    "    CONSTRAINT \"supplier _pkey_"+branchId+"\" PRIMARY KEY (\"supplierId\")\n" +
                    ")" +
                    "\n" +
                    "TABLESPACE pg_default;\n" +
                    "\n" +
                    "ALTER TABLE public.\"PosOrder\"" +
                    "    OWNER to "+ ValueinsoftBackendApplication.DatabaseOwner+" ;");

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
    static public boolean CreateTransactionTable(int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS public.\"InventoryTransactions_"+branchId+"\"\n" +
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
                    "        REFERENCES public.\"PosProduct_"+branchId+"\" (\"productId\") MATCH SIMPLE\n" +
                    "        ON UPDATE NO ACTION\n" +
                    "        ON DELETE NO ACTION\n" +
                    "        NOT VALID\n" +
                    ")\n" +
                    "\n" +
                    "TABLESPACE pg_default;\n" +
                    "\n" +
                    "ALTER TABLE public.\"InventoryTransactions_"+branchId+"\"" +
                    "    OWNER to "+ ValueinsoftBackendApplication.DatabaseOwner+" ;");

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
    static public boolean CreateOrderDetailsTable(int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS public.\"PosOrderDetail_"+branchId+"\"\n" +
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
                    "        REFERENCES public.\"PosOrder_"+branchId+"\" (\"orderId\") MATCH SIMPLE\n" +
                    "        ON UPDATE CASCADE\n" +
                    "        ON DELETE CASCADE\n" +
                    "        NOT VALID\n" +
                    ")\n" +
                    "TABLESPACE pg_default;" +
                    "ALTER TABLE public.\"PosOrderDetail\"" +
                    "    OWNER to "+ ValueinsoftBackendApplication.DatabaseOwner+" ;");

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
    static public boolean deleteBranch(int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("Begin;\n" +
                    "DELETE FROM public.\"Branch\"\n" +
                    "\tWHERE \"branchId\" = "+branchId+";\n" +
                    "DROP TABLE IF EXISTS public.\"PosOrderDetail_"+branchId+"\" CASCADE;\n" +
                    "DROP TABLE IF EXISTS public.\"PosOrder_"+branchId+"\" CASCADE;\n" +
                    "DROP TABLE IF EXISTS public.\"PosProduct_"+branchId+"\" CASCADE;\n" +
                    "DROP TABLE IF EXISTS public.\"InventoryTransactions_"+branchId+"\" CASCADE;\n" +
                    "DROP TABLE IF EXISTS public.\"supplier_"+branchId+"\" CASCADE;\n" +
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

}
