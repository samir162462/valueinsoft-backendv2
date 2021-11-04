package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

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

            // Crate Branch table for new branch in DB
            CreateBranchTable(getBranchIdByCompanyNameAndBranchName(companyId,branchName));

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
                    "    CONSTRAINT \"PosProduct_pkey-"+branchId+"\" PRIMARY KEY (\"productId\")\n" +
                    ")\n" +
                    "WITH (\n" +
                    "    OIDS = FALSE\n" +
                    ")\n" +
                    "TABLESPACE pg_default;\n" +
                    "\n" +
                    "ALTER TABLE public.\"PosProduct\"\n" +
                    "    OWNER to postgres;");


            int i = stmt.executeUpdate();
            System.out.println(i + " Table Established in PosProduct_"+branchId);

            conn.close();

        } catch (Exception e) {
            System.out.println(e.getMessage());
        return false;
        }
        return true;
    }

}