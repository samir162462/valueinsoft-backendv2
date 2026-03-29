package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;


@Repository
public class DbCompany {

    private static final RowMapper<Company> COMPANY_ROW_MAPPER = (rs, rowNum) -> {
        Company company = new Company(
                rs.getInt("id"),
                rs.getString("companyName"),
                rs.getTimestamp("establishedTime"),
                rs.getString("planName"),
                rs.getInt("planPrice"),
                rs.getString("currency"),
                rs.getString("comImg"),
                null
        );
        company.setOwnerId(rs.getInt("ownerId"));
        return company;
    };

    private final JdbcTemplate jdbcTemplate;
    private final DbBranch dbBranch;

    @Autowired
    public DbCompany(JdbcTemplate jdbcTemplate, DbBranch dbBranch) {
        this.jdbcTemplate = jdbcTemplate;
        this.dbBranch = dbBranch;
    }

    public Company getCompanyByOwnerId(String id) {
        return getCompanyByOwnerId(Integer.parseInt(id));
    }

    public Company getCompanyByOwnerId(int ownerId) {
        String sql = "SELECT id, \"companyName\", \"establishedTime\", \"ownerId\", \"planName\", \"planPrice\", " +
                "\"currency\", \"comImg\" FROM public.\"Company\" WHERE \"ownerId\" = ?";
        List<Company> companies = jdbcTemplate.query(sql, COMPANY_ROW_MAPPER, ownerId);
        if (companies.isEmpty()) {
            return null;
        }
        Company company = companies.get(0);
        company.setBranchList(new ArrayList<>(dbBranch.getBranchByCompanyId(company.getCompanyId())));
        return company;
    }

    public Company getCompanyAndBranchesByUserName(String userName) {
        String sql = "SELECT b.\"companyId\", u.\"branchId\" " +
                "FROM public.users u " +
                "JOIN public.\"Branch\" b ON u.\"branchId\" = b.\"branchId\" " +
                "WHERE u.\"userName\" = ?";
        List<int[]> mappings = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new int[]{rs.getInt("companyId"), rs.getInt("branchId")},
                userName
        );

        if (mappings.isEmpty()) {
            return null;
        }

        int companyId = mappings.get(0)[0];
        int branchId = mappings.get(0)[1];
        Company company = getCompanyById(companyId);
        if (company == null) {
            return null;
        }

        ArrayList<Branch> selectedBranches = new ArrayList<>();
        for (Branch branch : company.getBranchList()) {
            if (branch.getBranchID() == branchId) {
                selectedBranches.add(branch);
            }
        }
        company.setBranchList(selectedBranches);
        return company;
    }

    public boolean ownerHasCompany(int ownerId) {
        String sql = "SELECT COUNT(*) FROM public.\"Company\" WHERE \"ownerId\" = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, ownerId);
        return count != null && count > 0;
    }

    public Company getCompanyById(String id) {
        return getCompanyById(Integer.parseInt(id));
    }

    public Company getCompanyById(int companyId) {
        String sql = "SELECT id, \"companyName\", \"establishedTime\", \"ownerId\", \"planName\", \"planPrice\", " +
                "\"currency\", \"comImg\" FROM public.\"Company\" WHERE id = ?";
        List<Company> companies = jdbcTemplate.query(sql, COMPANY_ROW_MAPPER, companyId);
        if (companies.isEmpty()) {
            return null;
        }
        Company company = companies.get(0);
        company.setBranchList(new ArrayList<>(dbBranch.getBranchByCompanyId(company.getCompanyId())));
        return company;
    }

    public ArrayList<Company> getAllCompanies() {
        String sql = "SELECT id, \"companyName\", \"establishedTime\", \"ownerId\", \"planName\", \"planPrice\", " +
                "\"currency\", \"comImg\" FROM public.\"Company\"";
        return new ArrayList<>(jdbcTemplate.query(sql, COMPANY_ROW_MAPPER));
    }

    public int createCompany(String companyName, String plan, int price, int ownerId, String comImg, String currency) {
        String sql = "INSERT INTO public.\"Company\" (\"companyName\", \"establishedTime\", \"ownerId\", \"planName\", " +
                "\"planPrice\", \"comImg\", \"currency\") VALUES (?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int rows = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, companyName);
            statement.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            statement.setInt(3, ownerId);
            statement.setString(4, plan);
            statement.setInt(5, price);
            statement.setString(6, comImg);
            statement.setString(7, currency);
            return statement;
        }, keyHolder);

        if (rows != 1 || keyHolder.getKey() == null) {
            return -1;
        }

        return keyHolder.getKey().intValue();
    }

    public int updateCompanyImage(int companyId, String img) {
        String sql = "UPDATE public.\"Company\" SET \"comImg\" = ? WHERE id = ?";
        return jdbcTemplate.update(sql, img, companyId);
    }

    public boolean createCompanySchema(int companyId) {
        try {
            jdbcTemplate.execute(buildCompanySchemaSql(companyId));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private String buildCompanySchemaSql(int companyId) {
        String schemaName = "C_" + companyId;
        String dbOwner = ValueinsoftBackendApplication.DatabaseOwner;
        return "CREATE SCHEMA IF NOT EXISTS " + schemaName + "; " +
                SQLCompanyUsers(schemaName, dbOwner) + " " +
                SQLPosShiftPeriod(schemaName, dbOwner) + " " +
                SQLSupplier(schemaName, dbOwner) + " " +
                SQLBranch(schemaName, dbOwner) + " " +
                SQLDamagedList(schemaName, dbOwner) + " " +
                SQLPosCateJson(schemaName, dbOwner) + " " +
                SQLMainMajor(schemaName, dbOwner) + " " +
                SQLClientReceipts(schemaName, dbOwner) + " " +
                SQLSupplierBProduct(schemaName, dbOwner) + " " +
                SQLCompanyAnalysis(schemaName, dbOwner) + " " +
                SQLSupplierReciepts(schemaName, dbOwner) + " " +
                SQLFixArea(schemaName, dbOwner) + " " +
                SQLClient(schemaName, dbOwner);
    }

    //Statics SQL Queries

    //Todo SQL Company users
    static String SQLCompanyUsers(String SchemaName, String DBOwner) {
        String query = " CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"users\"\n" +
                "(\n" +
                "    id integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 21 START 10000 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"userName\" character varying(30) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    \"userPassword\" character varying(100) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    \"userEmail\" character varying(60) COLLATE pg_catalog.\"default\",\n" +
                "    \"userRole\" character varying(15) COLLATE pg_catalog.\"default\",\n" +
                "    \"userPhone\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    \"branchId\" integer,\n" +
                "    \"firstName\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    \"lastName\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    gender smallint,\n" +
                "    \"creationTime\" timestamp without time zone,\n" +
                "\"salaryPHour\" money," +
                "    CONSTRAINT users_pkey PRIMARY KEY (id)\n" +
                ")\n" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE " + SchemaName + ".\"users\"\n" +
                "    OWNER to " + DBOwner + "; ";

        return query;
    }

    static String SQLMainMajor(String SchemaName, String DBOwner) {
        String query = " CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"MainMajor\"\n" +
                " AS \n" +
                "SELECT\n" +
                "*\n" +
                "FROM\n" +
                "public.\"MainMajor\"; ";

        return query;
    }

    //Todo SQL PosShiftPeriod
    static String SQLPosShiftPeriod(String SchemaName, String DBOwner) {
        String query = "CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"PosShiftPeriod\"\n" +
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
                "ALTER TABLE " + SchemaName + ".\"PosShiftPeriod\"\n" +
                "    OWNER to " + DBOwner + "; ";

        return query;
    }

    //Todo SQL supplier
    static String SQLSupplier(String SchemaName, String DBOwner) {
        String query = "CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"supplier \"\n" +
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
                "ALTER TABLE " + SchemaName + ".\"supplier \"\n" +
                "    OWNER to " + DBOwner + ";";

        return query;
    }

    //Todo SQL Branch
    static String SQLBranch(String SchemaName, String DBOwner) {
        String query = "CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"Branch\"\n" +
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
                "ALTER TABLE " + SchemaName + ".\"Branch\"\n" +
                "    OWNER to " + DBOwner + ";";

        return query;
    }

    //Todo SQL DamagedList
    static String SQLDamagedList(String SchemaName, String DBOwner) {
        String query = "CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"DamagedList\"\n" +
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
                "ALTER TABLE " + SchemaName + ".\"DamagedList\"\n" +
                "    OWNER to " + DBOwner + ";";

        return query;
    }

    //Todo SQL Client
    static String SQLClient(String SchemaName, String DBOwner) {
        String query = " CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"Client\"\n" +
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
                "ALTER TABLE " + SchemaName + ".\"Client\"\n" +
                "    OWNER to " + DBOwner + ";";

        return query;
    }

    //Todo SQL PosCateJson
    static String SQLPosCateJson(String SchemaName, String DBOwner) {
        String query = "CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"PosCateJson\"\n" +
                "(\n" +
                "    \"CategoryJID\" integer NOT NULL GENERATED BY DEFAULT AS IDENTITY ( INCREMENT 1 START 1000 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"CategoryData\" json,\n" +
                "    \"BranchId\" integer,\n" +
                "    CONSTRAINT \"PosCateJson_pkey\" PRIMARY KEY (\"CategoryJID\")\n" +
                ")\n" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE " + SchemaName + ".\"PosCateJson\"\n" +
                "    OWNER to " + DBOwner + ";";

        return query;
    }

    //Todo SQL clientReceipts
    static String SQLClientReceipts(String SchemaName, String DBOwner) {
        String query = "CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"ClientReceipts\"\n" +
                "(\n" +
                "    \"crId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 11 START 10000 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    type character varying(20) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    amount money NOT NULL,\n" +
                "    \"time\" timestamp without time zone NOT NULL,\n" +
                "    \"userName\" character varying(25) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    \"clientId\" integer NOT NULL,\n" +
                "    \"branchId\" integer NOT NULL,\n" +
                "    CONSTRAINT \"ClientRecipts_pkey\" PRIMARY KEY (\"crId\")\n " +
                ")" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                " ";

        return query;
    }

    //Todo SQL SupplierBProduct
    static String SQLSupplierBProduct(String SchemaName, String DBOwner) {
        String query = "CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"SupplierBProduct\"\n" +
                "(\n" +
                "    \"sBPId\" integer NOT NULL GENERATED BY DEFAULT AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"productId\" integer NOT NULL,\n" +
                "    quantity integer NOT NULL,\n" +
                "    cost integer NOT NULL,\n" +
                "    \"userName\" character varying(25) COLLATE pg_catalog.\"default\",\n" +
                "    \"sPaid\" integer,\n" +
                "    \"time\" timestamp without time zone NOT NULL,\n" +
                "    \"desc\" character varying(40) COLLATE pg_catalog.\"default\",\n" +
                "    \"supplierId\" integer NOT NULL,\n" +
                "    \"branchId\" integer NOT NULL,\n" +
                "    \"orderDetailsId\" integer,\n" +
                "    CONSTRAINT \"SupplierBProduct_pkey\" PRIMARY KEY (\"sBPId\")\n" +
                ")\n" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE " + SchemaName + ".\"SupplierBProduct\"\n" +
                "    OWNER to " + DBOwner + ";";

        return query;
    }

    //Todo SQL CompanyAnalysis
    static String SQLCompanyAnalysis(String SchemaName, String DBOwner) {
        String query = "CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"CompanyAnalysis\"\n" +
                "(\n" +
                "    \"cAID\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    sales integer DEFAULT 0,\n" +
                "    \"Income\" integer DEFAULT 0,\n" +
                "    \"clientsIn\" integer DEFAULT 0,\n" +
                "    \"invShortage\" integer DEFAULT 0,\n" +
                "    \"discountByUsers\" integer DEFAULT 0,\n" +
                "    \"damagedProducts\" integer DEFAULT 0,\n" +
                "    \"returnPurchases\" integer DEFAULT 0,\n" +
                "    \"shiftEndsEarly\" integer DEFAULT 0,\n" +
                "    date date NOT NULL,\n" +
                "    \"branchId\" integer NOT NULL,\n" +
                "    CONSTRAINT \"CompanyAnalysis_pkey\" PRIMARY KEY (\"cAID\")\n" +
                ")" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n";

        return query;
    }

    //Todo SQL FixArea
    static String SQLFixArea(String SchemaName, String DBOwner) {
        String query = "CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"FixArea\"\n" +
                "(\n" +
                "    \"faId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1000 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"fixSlot\" integer,\n" +
                "    \"clientId\" integer,\n" +
                "    \"dateIn\" date,\n" +
                "    \"dateFinished\" date,\n" +
                "    \"phoneName\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    problem character varying COLLATE pg_catalog.\"default\",\n" +
                "    show boolean,\n" +
                "    \"userName_Recived\" character varying COLLATE pg_catalog.\"default\",\n" +
                "    status character varying COLLATE pg_catalog.\"default\",\n" +
                "    \"desc\" character varying COLLATE pg_catalog.\"default\"," +
                "    \"branchId\" integer,\n" +
                "    fees money DEFAULT 0,\n" +
                "    CONSTRAINT \"FixArea_pkey\" PRIMARY KEY (\"faId\")\n" +
                ")" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n";

        return query;
    }
    //Todo SQL SQLSupplierReciepts
    static String SQLSupplierReciepts(String SchemaName, String DBOwner) {
        String query = "CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"supplierReciepts\"\n" +
                "(\n" +
                "    \"srId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 111 START 10897236 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"transId\" integer NOT NULL,\n" +
                "    \"amountPaid\" money NOT NULL,\n" +
                "    \"remainingAmount\" money NOT NULL,\n" +
                "    \"receiptTime\" timestamp without time zone NOT NULL,\n" +
                "    \"userRecived\" character varying COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    \"supplierId\" integer NOT NULL,\n" +
                "    type character varying(15) COLLATE pg_catalog.\"default\" NOT NULL,\n" +
                "    \"branchId\" integer NOT NULL,\n" +
                "    CONSTRAINT \"supplierReciepts_pkey\" PRIMARY KEY (\"srId\")\n" +
                ")" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n";

        return query;
    }
    //Schema Builder For All

    static public boolean CreateCompanySchema(int companyId) {
        try {

            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("" +
                    "CREATE SCHEMA IF NOT EXISTS C_" + companyId + " ; " +
                    " " + SQLCompanyUsers("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLPosShiftPeriod("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLSupplier("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLBranch("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLDamagedList("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLPosCateJson("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLMainMajor("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLClientReceipts("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLSupplierBProduct("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLCompanyAnalysis("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLSupplierReciepts("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLFixArea("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " " +
                    " " + SQLClient("C_" + companyId, ValueinsoftBackendApplication.DatabaseOwner) + " "
            );

            System.out.println(stmt);
            int i = stmt.executeUpdate();
            System.out.println(i + " CreateCompanySchema Established For Company" + companyId);
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("Schema Build Error" + e.getMessage());
            return false;
        }
        return true;
    }


}
