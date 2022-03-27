package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;

import java.sql.*;
import java.util.ArrayList;

public class DbCompany {


    public static Company getCompanyByOwnerId(String id) {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            ArrayList<Branch> bsList ;
            Company company = null;
            String query = "SELECT id, \"companyName\", \"establishedTime\", \"ownerId\", \"planName\", \"planPrice\" ,\"currency\", \"comImg\"\n" +
                    "\tFROM public.\"Company\" where \"ownerId\" = " + id + ";";


            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));


                company = new Company(rs.getInt(1), rs.getString(2), rs.getTimestamp(3), rs.getString(5), rs.getInt(6), rs.getString(7),rs.getString(8) , null);

                // print the results

            }


            bsList = DbBranch.getBranchByCompanyId(company.getCompanyId());
            company.setBranchList(bsList);
            rs.close();
            st.close();
            conn.close();
            return company;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }
    public static Company getCompanyAndBranchesByUserName(String id) {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            ArrayList<Branch> bsList = new ArrayList<>();
            Company company = new Company(0,null,null,null,0,null,null,null);
            String query = "SELECT  \"companyId\" ,public.users.\"branchId\" \n" +
                    "FROM public.users  \n" +
                    " JOIN public.\"Branch\"\n" +
                    "ON public.users.\"branchId\" = public.\"Branch\".\"branchId\" where public.\"users\".\"userName\" = '"+id+"';";

            String qu1 = "SELECT * FROM public.users\n" +
                    "ORDER BY id ASC ";
            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            int companyId = 0 ;
            int branchId = 0 ;
            while (rs.next()) {
                companyId = rs.getInt(1);
                branchId = rs.getInt(2);
                System.out.println(branchId);
            }
            try {
                System.out.println("companyId: "+companyId);
                bsList = DbBranch.getBranchByCompanyId(companyId);
                System.out.println(bsList.toString());
                company.setCompanyId(companyId);
                ArrayList<Branch> branchArrayList = new ArrayList<>();
                for (int i = 0; i < bsList.size(); i++) {
                    if (bsList.get(i).getBranchID() == branchId)
                        branchArrayList.add(bsList.get(i));
                }
                company.setBranchList(branchArrayList);
            }catch(Exception e)
            {
                System.out.println(e.getMessage());
                return null;
            }

            rs.close();
            st.close();
            conn.close();
            return company;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }
    private static boolean checkExistOwnerId(int oId)
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT  \"ownerId\"\n" +
                    "\tFROM public.\"Company\" where \"ownerId\" = '"+oId+"';";

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
    public static Company getCompanyById(String id) {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            ArrayList<Branch> bsList = new ArrayList<>();
            Company company = null;
            String query = "SELECT id, \"companyName\", \"establishedTime\", \"ownerId\", \"planName\", \"planPrice\",  \"currency\",\"comImg\"  \n" +
                    "\tFROM public.\"Company\" where \"id\" = " + id + ";";

            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));

                try {
                    company = new Company(rs.getInt(1), rs.getString(2), rs.getTimestamp(3), rs.getString(5), rs.getInt(6),rs.getString(7) , rs.getString(8) ,null );
                    System.out.println(company.toString());
                }catch (Exception e)
                {
                    System.out.println(e.getMessage());
                }

                // print the results
            }




            try {
                bsList = DbBranch.getBranchByCompanyId(company.getCompanyId());
                company.setBranchList(bsList);
            }catch (Exception e)
            {
                System.out.println(e.getMessage());
            }

            rs.close();
            st.close();
            conn.close();
            return company;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }

    static public String AddCompany(String companyName, String branchName, String plan, int price, String username ,String comImg, String currency) {
        try {

            int ownerId = 0;
            User u1 = DbUsers.getUser(username);
            ownerId = u1.getUserId();
            if (checkExistOwnerId(ownerId) ) {
                return "The Owner already has Company!";
            }

            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("INSERT INTO public.\"Company\"(\n" +
                    " \"companyName\", \"establishedTime\", \"ownerId\", \"planName\", \"planPrice\" , \"comImg\", \"currency\")\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?,?,?);",Statement.RETURN_GENERATED_KEYS);

            stmt.setString(1, companyName);
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setInt(3, ownerId);
            stmt.setString(4, plan);
            stmt.setInt(5, price);
            stmt.setString(6, comImg);
            stmt.setString(7, currency);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating Company failed, no rows affected.");
            }
            int id = 0;
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    id = generatedKeys.getInt(1) ;
                    CreateCompanySchema(id);
                    DbUsers.UpdateRole("public",ownerId,"Owner");
                    if (branchName.length()>2) {
                        DbBranch.AddBranch(branchName,"Egypt",id);

                    }
                }
                else {
                    throw new SQLException("Creating user failed, no ID obtained.");
                }
            }
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the user not added bs error!";

        }

        return "the user added!";
    }

    //Statics SQL Queries

    //Todo SQL Company users
    static String SQLCompanyUsers (String  SchemaName , String DBOwner)
    {
        String query = " CREATE TABLE IF NOT EXISTS "+SchemaName+".\"users\"\n" +
                "(\n" +
                "    id integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 21 START 10000 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"userName\" character varying(30) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    \"userPassword\" character varying(30) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    \"userEmail\" character varying(60) COLLATE pg_catalog.\"default\",\n" +
                "    \"userRole\" character varying(15) COLLATE pg_catalog.\"default\",\n" +
                "    \"userPhone\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    \"branchId\" integer,\n" +
                "    \"firstName\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    \"lastName\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    gender smallint,\n" +
                "    \"creationTime\" timestamp without time zone,\n" +
                "    CONSTRAINT users_pkey PRIMARY KEY (id)\n" +
                ")\n" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE "+SchemaName+".\"users\"\n" +
                "    OWNER to "+DBOwner+"; ";

        return query;
    }
    static  String SQLMainMajor (String  SchemaName , String DBOwner)
    {
        String query = " CREATE TABLE IF NOT EXISTS "+SchemaName+".\"MainMajor\"\n" +
                " AS \n" +
                "SELECT\n" +
                "*\n" +
                "FROM\n" +
                "public.\"MainMajor\"; ";

        return query;
    }
    //Todo SQL PosShiftPeriod
    static String SQLPosShiftPeriod (String  SchemaName , String DBOwner)
    {
        String query = "CREATE TABLE IF NOT EXISTS "+SchemaName+".\"PosShiftPeriod\"\n" +
                "(\n" +
                "    \"PosSOID\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"ShiftStartTime\" timestamp without time zone,\n" +
                "    \"ShiftEndTime\" timestamp without time zone,\n" +
                "    \"branchId\" integer,\n" +
                "    CONSTRAINT \"PosShiftPeriod_pkey\" PRIMARY KEY (\"PosSOID\")\n" +
                ")\n" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE "+SchemaName+".\"PosShiftPeriod\"\n" +
                "    OWNER to "+DBOwner+"; ";

        return query;
    }
    //Todo SQL supplier
    static String SQLSupplier (String  SchemaName , String DBOwner)
    {
        String query = "CREATE TABLE IF NOT EXISTS "+SchemaName+".\"supplier \"\n" +
                "(\n" +
                "    \"supplierId\" integer NOT NULL GENERATED BY DEFAULT AS IDENTITY ( INCREMENT 1 START 1000 MINVALUE 1000 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"SupplierName\" character varying COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    \"supplierPhone1\" character varying(14) COLLATE pg_catalog.\"default\",\n" +
                "    \"supplierPhone2\" character varying(14) COLLATE pg_catalog.\"default\",\n" +
                "    \"SupplierLocation\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    \"suplierMajor\" character varying(20) COLLATE pg_catalog.\"default\",\n" +
                "    CONSTRAINT \"supplier _pkey\" PRIMARY KEY (\"supplierId\")\n" +
                ")\n" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE "+SchemaName+".\"supplier \"\n" +
                "    OWNER to "+DBOwner+";";

        return query;
    }
    //Todo SQL Branch
    static String SQLBranch (String  SchemaName , String DBOwner)
    {
        String query = "CREATE TABLE IF NOT EXISTS "+SchemaName+".\"Branch\"\n" +
                "(\n" +
                "    \"branchId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 123 START 10000 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"branchName\" character varying(25) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    \"branchLocation\" character varying(30) COLLATE pg_catalog.\"default\",\n" +
                "    \"companyId\" integer NOT NULL,\n" +
                "    \"branchEstTime\" timestamp(3) without time zone,\n" +
                "    CONSTRAINT \"Branch_pkey\" PRIMARY KEY (\"branchId\")\n" +
                ")\n" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE "+SchemaName+".\"Branch\"\n" +
                "    OWNER to "+DBOwner+";";

        return query;
    }
    //Todo SQL DamagedList
    static String SQLDamagedList (String  SchemaName , String DBOwner)
    {
        String query = "CREATE TABLE IF NOT EXISTS "+SchemaName+".\"DamagedList\"\n" +
                "(\n" +
                "    \"DId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"ProductId\" integer NOT NULL,\n" +
                "    \"ProductName\" character varying COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    \"Time\" timestamp without time zone NOT NULL,\n" +
                "    \"Reason\" character varying(15) COLLATE pg_catalog.\"default\",\n" +
                "    \"Damaged by\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    \"Cashier user\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    \"AmountTP\" integer,\n" +
                "    \"Paid\" boolean,\n" +
                "    \"branchId\" integer,\n" +
                "    quantity integer,\n" +
                "    CONSTRAINT \"DamgedList_pkey\" PRIMARY KEY (\"DId\")\n" +
                ")\n" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE "+SchemaName+".\"DamagedList\"\n" +
                "    OWNER to "+DBOwner+";";

        return query;
    }
    //Todo SQL Client
    static String SQLClient (String  SchemaName , String DBOwner)
    {
        String query = " CREATE TABLE IF NOT EXISTS "+SchemaName+".\"Client\"\n" +
                "(\n" +
                "    c_id integer NOT NULL GENERATED BY DEFAULT AS IDENTITY ( INCREMENT 11 START 10000 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"clientName\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    \"clientPhone\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    gender character varying COLLATE pg_catalog.\"default\",\n" +
                "    description character varying COLLATE pg_catalog.\"default\",\n" +
                "    \"branchId\" integer,\n" +
                "    \"registeredTime\" timestamp without time zone,\n" +
                "    CONSTRAINT \"Client_Key\" PRIMARY KEY (c_id)\n" +
                ")\n" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE "+SchemaName+".\"Client\"\n" +
                "    OWNER to "+DBOwner+";";

        return query;
    }
    //Todo SQL PosCateJson
    static String SQLPosCateJson (String  SchemaName , String DBOwner)
    {
        String query = "CREATE TABLE IF NOT EXISTS "+SchemaName+".\"PosCateJson\"\n" +
                "(\n" +
                "    \"CategoryJID\" integer NOT NULL GENERATED BY DEFAULT AS IDENTITY ( INCREMENT 1 START 1000 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"CategoryData\" json,\n" +
                "    \"BranchId\" integer,\n" +
                "    CONSTRAINT \"PosCateJson_pkey\" PRIMARY KEY (\"CategoryJID\")\n" +
                ")\n" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE "+SchemaName+".\"PosCateJson\"\n" +
                "    OWNER to "+DBOwner+";";

        return query;
    }
    //Todo SQL clientReceipts
    static String SQLClientReceipts (String  SchemaName , String DBOwner)
    {
        String query = "CREATE TABLE IF NOT EXISTS "+SchemaName+".\"ClientReceipts\"\n" +
                "(\n" +
                "    \"crId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 11 START 10000 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    type character varying(20) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    amount money NOT NULL,\n" +
                "    \"time\" timestamp without time zone NOT NULL,\n" +
                "    \"userName\" character varying(25) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    \"clientId\" integer NOT NULL,\n" +
                "    \"branchId\" integer NOT NULL,\n" +
                "    CONSTRAINT \"ClientRecipts_pkey\" PRIMARY KEY (\"crId\")\n" +
                ")" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE "+SchemaName+".\"ClientReceipts\"\n" +
                "    OWNER to "+DBOwner+";";

        return query;
    }

    //Schema Builder For All

    static public boolean CreateCompanySchema(int companyId) {
        try {

            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("" +
                    "CREATE SCHEMA IF NOT EXISTS C_"+companyId+" ; " +
                    " "+SQLCompanyUsers("C_"+companyId,ValueinsoftBackendApplication.DatabaseOwner)+" " +
                    " "+SQLPosShiftPeriod("C_"+companyId,ValueinsoftBackendApplication.DatabaseOwner)+" " +
                    " "+SQLSupplier("C_"+companyId,ValueinsoftBackendApplication.DatabaseOwner)+" " +
                    " "+SQLBranch("C_"+companyId,ValueinsoftBackendApplication.DatabaseOwner)+" " +
                    " "+SQLDamagedList("C_"+companyId,ValueinsoftBackendApplication.DatabaseOwner)+" " +
                    " "+SQLPosCateJson("C_"+companyId,ValueinsoftBackendApplication.DatabaseOwner)+" " +
                    " "+SQLMainMajor("c_"+companyId,ValueinsoftBackendApplication.DatabaseOwner)+" " +
                    " "+SQLClientReceipts("C_"+companyId,ValueinsoftBackendApplication.DatabaseOwner)+" " +
                    " "+SQLClient("C_"+companyId,ValueinsoftBackendApplication.DatabaseOwner)+" ;");

            int i = stmt.executeUpdate();
            System.out.println(i + " CreateCompanySchema Established For Company"+companyId);
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("Schema Build Error"+e.getMessage());
            return false;
        }
        return true;
    }




}
