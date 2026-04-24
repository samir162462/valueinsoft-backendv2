package com.example.valueinsoftbackend.DatabaseRequests;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;


@Repository
@Slf4j
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
        company.setTenantStatus(rs.getString("tenantStatus"));
        return company;
    };

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DbBranch dbBranch;
    private final String databaseOwner;
    private final boolean legacyDirectSchemaProvisioningEnabled;

    @Autowired
    public DbCompany(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            DbBranch dbBranch,
            @Value("${vls.database.owner:${spring.datasource.username:postgres}}") String databaseOwner,
            @Value("${vls.database.legacy-direct-schema-provisioning-enabled:false}") boolean legacyDirectSchemaProvisioningEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.dbBranch = dbBranch;
        this.databaseOwner = (databaseOwner == null || databaseOwner.isBlank()) ? "postgres" : databaseOwner.trim();
        this.legacyDirectSchemaProvisioningEnabled = legacyDirectSchemaProvisioningEnabled;
    }

    public Company getCompanyByOwnerId(String id) {
        return getCompanyByOwnerId(Integer.parseInt(id));
    }

    public Company getCompanyByOwnerId(int ownerId) {
        String sql = "SELECT c.id, c.\"companyName\", c.\"establishedTime\", c.\"ownerId\", c.\"planName\", c.\"planPrice\", " +
                "c.\"currency\", c.\"comImg\", COALESCE(t.status, 'active') AS \"tenantStatus\" " +
                "FROM public.\"Company\" c " +
                "LEFT JOIN public.tenants t ON t.tenant_id = c.id " +
                "WHERE c.\"ownerId\" = ?";
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
        String sql = "SELECT c.id, c.\"companyName\", c.\"establishedTime\", c.\"ownerId\", c.\"planName\", c.\"planPrice\", " +
                "c.\"currency\", c.\"comImg\", COALESCE(t.status, 'active') AS \"tenantStatus\" " +
                "FROM public.\"Company\" c " +
                "LEFT JOIN public.tenants t ON t.tenant_id = c.id " +
                "WHERE c.id = ?";
        List<Company> companies = jdbcTemplate.query(sql, COMPANY_ROW_MAPPER, companyId);
        if (companies.isEmpty()) {
            return null;
        }
        Company company = companies.get(0);
        company.setBranchList(new ArrayList<>(dbBranch.getBranchByCompanyId(company.getCompanyId())));
        return company;
    }

    public ArrayList<Company> getAllCompanies() {
        String sql = "SELECT c.id, c.\"companyName\", c.\"establishedTime\", c.\"ownerId\", c.\"planName\", c.\"planPrice\", " +
                "c.\"currency\", c.\"comImg\", COALESCE(t.status, 'active') AS \"tenantStatus\" " +
                "FROM public.\"Company\" c " +
                "LEFT JOIN public.tenants t ON t.tenant_id = c.id";
        return new ArrayList<>(jdbcTemplate.query(sql, COMPANY_ROW_MAPPER));
    }

    public int createCompany(String companyName, String plan, int price, int ownerId, String comImg, String currency) {
        String sql = "INSERT INTO public.\"Company\" (\"companyName\", \"establishedTime\", \"ownerId\", \"planName\", " +
                "\"planPrice\", \"comImg\", \"currency\") VALUES (:companyName, :establishedTime, :ownerId, :planName, :planPrice, :comImg, :currency)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyName", companyName)
                .addValue("establishedTime", new Timestamp(System.currentTimeMillis()))
                .addValue("ownerId", ownerId)
                .addValue("planName", plan)
                .addValue("planPrice", price)
                .addValue("comImg", comImg)
                .addValue("currency", currency);
        int rows = namedParameterJdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

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
            for (String statement : buildCompanySchemaSql(companyId)) {
                jdbcTemplate.execute(statement);
            }
            log.info("Provisioned company schema for company {}", companyId);
            return true;
        } catch (Exception exception) {
            log.error("Failed to provision company schema for company {}", companyId, exception);
            return false;
        }
    }

    private List<String> buildCompanySchemaSql(int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String schemaName = TenantSqlIdentifiers.companySchema(companyId);
        ArrayList<String> statements = new ArrayList<>(List.of(
                "CREATE SCHEMA IF NOT EXISTS " + schemaName,
                SQLCompanyUsers(schemaName, databaseOwner),
                SQLPosShiftPeriod(schemaName, databaseOwner),
                SQLShiftEvent(schemaName, databaseOwner),
                SQLShiftCashMovement(schemaName, databaseOwner),
                SQLSupplier(schemaName, databaseOwner),
                SQLBranch(schemaName, databaseOwner),
                SQLDamagedList(schemaName, databaseOwner),
                SQLPosCateJson(schemaName, databaseOwner),
                SQLMainMajor(schemaName, databaseOwner),
                SQLClientReceipts(schemaName, databaseOwner),
                SQLSupplierBProduct(schemaName, databaseOwner),
                SQLCompanyAnalysis(schemaName, databaseOwner),
                SQLSupplierReciepts(schemaName, databaseOwner),
                SQLFixArea(schemaName, databaseOwner),
                SQLClient(schemaName, databaseOwner)
        ));
        statements.addAll(SQLModernInventoryFoundation(schemaName, databaseOwner));
        return statements;
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
        return "CREATE TABLE IF NOT EXISTS " + SchemaName + ".\"PosShiftPeriod\"\n" +
                "(\n" +
                "    \"PosSOID\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    \"ShiftStartTime\" timestamp without time zone,\n" +
                "    \"ShiftEndTime\" timestamp without time zone,\n" +
                "    \"branchId\" integer,\n" +
                "    opened_by_user_id   VARCHAR(120),\n" +
                "    assigned_cashier_id  VARCHAR(120),\n" +
                "    closed_by_user_id    VARCHAR(120),\n" +
                "    register_code        VARCHAR(40),\n" +
                "    status               VARCHAR(20) NOT NULL DEFAULT 'OPEN',\n" +
                "    opening_float        NUMERIC(14,2) NOT NULL DEFAULT 0,\n" +
                "    expected_cash        NUMERIC(14,2),\n" +
                "    counted_cash         NUMERIC(14,2),\n" +
                "    variance_amount      NUMERIC(14,2),\n" +
                "    variance_reason      VARCHAR(500),\n" +
                "    close_note           VARCHAR(500),\n" +
                "    order_count          INTEGER DEFAULT 0,\n" +
                "    gross_sales          NUMERIC(14,2) DEFAULT 0,\n" +
                "    net_sales            NUMERIC(14,2) DEFAULT 0,\n" +
                "    discount_total       NUMERIC(14,2) DEFAULT 0,\n" +
                "    refund_total         NUMERIC(14,2) DEFAULT 0,\n" +
                "    version              INTEGER NOT NULL DEFAULT 1,\n" +
                "    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                "    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                "    CONSTRAINT \"PosShiftPeriod_pkey\" PRIMARY KEY (\"PosSOID\"),\n" +
                "    CONSTRAINT shift_status_ck CHECK (status IN ('OPEN', 'CLOSING', 'CLOSED', 'FORCE_CLOSED'))\n" +
                ")\n" +
                "\n" +
                "TABLESPACE pg_default;\n" +
                "\n" +
                "ALTER TABLE " + SchemaName + ".\"PosShiftPeriod\"\n" +
                "    OWNER to " + DBOwner + "; " +
                "CREATE INDEX IF NOT EXISTS idx_shift_branch_status ON " + SchemaName + ".\"PosShiftPeriod\" (\"branchId\", status); " +
                "CREATE INDEX IF NOT EXISTS idx_shift_opened_at ON " + SchemaName + ".\"PosShiftPeriod\" (\"ShiftStartTime\" DESC); ";
    }

    static String SQLShiftEvent(String SchemaName, String DBOwner) {
        return "CREATE TABLE IF NOT EXISTS " + SchemaName + ".shift_event (" +
                " event_id BIGSERIAL PRIMARY KEY," +
                " shift_id INTEGER NOT NULL," +
                " branch_id INTEGER NOT NULL," +
                " event_type VARCHAR(60) NOT NULL," +
                " event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " actor_user_id VARCHAR(120)," +
                " actor_role VARCHAR(40)," +
                " reference_type VARCHAR(60)," +
                " reference_id VARCHAR(120)," +
                " metadata JSONB," +
                " reason VARCHAR(500)," +
                " CONSTRAINT shift_event_shift_fk FOREIGN KEY (shift_id) REFERENCES " + SchemaName + ".\"PosShiftPeriod\" (\"PosSOID\") ON DELETE CASCADE" +
                "); " +
                "CREATE INDEX IF NOT EXISTS idx_shift_event_shift_time ON " + SchemaName + ".shift_event (shift_id, event_time); " +
                "ALTER TABLE " + SchemaName + ".shift_event OWNER to " + DBOwner + "; ";
    }

    static String SQLShiftCashMovement(String SchemaName, String DBOwner) {
        return "CREATE TABLE IF NOT EXISTS " + SchemaName + ".shift_cash_movement (" +
                " movement_id BIGSERIAL PRIMARY KEY," +
                " shift_id INTEGER NOT NULL," +
                " branch_id INTEGER NOT NULL," +
                " movement_type VARCHAR(30) NOT NULL," +
                " amount NUMERIC(14,2) NOT NULL," +
                " actor_user_id VARCHAR(120)," +
                " reference_type VARCHAR(60)," +
                " reference_id VARCHAR(120)," +
                " note VARCHAR(500)," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " CONSTRAINT shift_cash_movement_shift_fk FOREIGN KEY (shift_id) REFERENCES " + SchemaName + ".\"PosShiftPeriod\" (\"PosSOID\") ON DELETE CASCADE," +
                " CONSTRAINT shift_cash_movement_type_ck CHECK (movement_type IN ('OPENING_FLOAT','CASH_SALE','CASH_REFUND','PAID_IN','PAID_OUT','SAFE_DROP','CASH_ADJUSTMENT','CLOSE_COUNT'))" +
                "); " +
                "CREATE INDEX IF NOT EXISTS idx_shift_cash_movement_shift ON " + SchemaName + ".shift_cash_movement (shift_id, created_at); " +
                "ALTER TABLE " + SchemaName + ".shift_cash_movement OWNER to " + DBOwner + "; ";
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
                "    \"Reason\" character varying(255) COLLATE pg_catalog.\"default\",\n" +
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

    static List<String> SQLModernInventoryFoundation(String schemaName, String dbOwner) {
        ArrayList<String> statements = new ArrayList<>();
        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_product (" +
                " product_id BIGSERIAL PRIMARY KEY," +
                " product_name VARCHAR(30) NOT NULL," +
                " buying_day TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " activation_period INTEGER NOT NULL DEFAULT 0," +
                " retail_price INTEGER NOT NULL," +
                " lowest_price INTEGER NOT NULL," +
                " buying_price INTEGER NOT NULL," +
                " company_name VARCHAR(30) NOT NULL," +
                " product_type VARCHAR(15) NOT NULL," +
                " owner_name VARCHAR(20)," +
                " serial VARCHAR(35)," +
                " description VARCHAR(60)," +
                " battery_life INTEGER NOT NULL DEFAULT 0," +
                " owner_phone VARCHAR(14)," +
                " owner_ni VARCHAR(18)," +
                " product_state VARCHAR(10) NOT NULL," +
                " supplier_id INTEGER NOT NULL DEFAULT 0," +
                " major VARCHAR(30) NOT NULL," +
                " img_file TEXT," +
                " business_line_key VARCHAR(40) NOT NULL DEFAULT 'MOBILE'," +
                " template_key VARCHAR(80) NOT NULL DEFAULT 'mobile_device'," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " CONSTRAINT inventory_product_price_order_ck CHECK (retail_price >= lowest_price AND lowest_price >= buying_price)," +
                " CONSTRAINT inventory_product_state_ck CHECK (product_state IN ('New', 'Used'))," +
                " CONSTRAINT inventory_product_activation_ck CHECK (activation_period >= 0)," +
                " CONSTRAINT inventory_product_battery_ck CHECK (battery_life >= 0)" +
                ")");
        statements.add("CREATE INDEX IF NOT EXISTS idx_inventory_product_major ON " + schemaName + ".inventory_product (major, product_id DESC)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_inventory_product_name ON " + schemaName + ".inventory_product (product_name)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_inventory_product_serial ON " + schemaName + ".inventory_product (serial)");

        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_branch_stock_balance (" +
                " branch_id INTEGER NOT NULL," +
                " product_id BIGINT NOT NULL," +
                " quantity INTEGER NOT NULL DEFAULT 0," +
                " reserved_qty INTEGER NOT NULL DEFAULT 0," +
                " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " PRIMARY KEY (branch_id, product_id)," +
                " CONSTRAINT inventory_branch_stock_balance_branch_fk FOREIGN KEY (branch_id) REFERENCES public.\"Branch\" (\"branchId\") ON DELETE CASCADE," +
                " CONSTRAINT inventory_branch_stock_balance_product_fk FOREIGN KEY (product_id) REFERENCES " + schemaName + ".inventory_product (product_id) ON DELETE CASCADE," +
                " CONSTRAINT inventory_branch_stock_balance_quantity_ck CHECK (quantity >= 0)," +
                " CONSTRAINT inventory_branch_stock_balance_reserved_ck CHECK (reserved_qty >= 0)" +
                ")");
        statements.add("CREATE INDEX IF NOT EXISTS idx_inventory_branch_stock_balance_branch ON " + schemaName + ".inventory_branch_stock_balance (branch_id, quantity DESC)");

        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_stock_ledger (" +
                " stock_ledger_id BIGSERIAL PRIMARY KEY," +
                " branch_id INTEGER NOT NULL," +
                " product_id BIGINT NOT NULL," +
                " quantity_delta INTEGER NOT NULL," +
                " movement_type VARCHAR(40) NOT NULL," +
                " reference_type VARCHAR(40)," +
                " reference_id VARCHAR(64)," +
                " actor_name VARCHAR(100)," +
                " note VARCHAR(255)," +
                " supplier_id INTEGER NOT NULL DEFAULT 0," +
                " trans_total INTEGER NOT NULL DEFAULT 0," +
                " pay_type VARCHAR(30)," +
                " remaining_amount INTEGER NOT NULL DEFAULT 0," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " CONSTRAINT inventory_stock_ledger_branch_fk FOREIGN KEY (branch_id) REFERENCES public.\"Branch\" (\"branchId\") ON DELETE CASCADE," +
                " CONSTRAINT inventory_stock_ledger_product_fk FOREIGN KEY (product_id) REFERENCES " + schemaName + ".inventory_product (product_id) ON DELETE CASCADE" +
                ")");
        statements.add("CREATE INDEX IF NOT EXISTS idx_inventory_stock_ledger_branch_product_time ON " + schemaName + ".inventory_stock_ledger (branch_id, product_id, created_at DESC)");

        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_legacy_product_mapping (" +
                " branch_id INTEGER NOT NULL," +
                " legacy_product_id INTEGER NOT NULL," +
                " product_id BIGINT NOT NULL," +
                " synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " PRIMARY KEY (branch_id, legacy_product_id)," +
                " CONSTRAINT inventory_legacy_product_mapping_product_fk FOREIGN KEY (product_id) REFERENCES " + schemaName + ".inventory_product (product_id) ON DELETE CASCADE" +
                ")");

        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_product_template (" +
                " template_id BIGSERIAL PRIMARY KEY," +
                " business_line_key VARCHAR(40) NOT NULL," +
                " template_key VARCHAR(80) NOT NULL UNIQUE," +
                " display_name VARCHAR(100) NOT NULL," +
                " major_key VARCHAR(40)," +
                " supports_serial BOOLEAN NOT NULL DEFAULT FALSE," +
                " supports_batch BOOLEAN NOT NULL DEFAULT FALSE," +
                " supports_expiry BOOLEAN NOT NULL DEFAULT FALSE," +
                " supports_weight BOOLEAN NOT NULL DEFAULT FALSE," +
                " is_system BOOLEAN NOT NULL DEFAULT TRUE," +
                " is_active BOOLEAN NOT NULL DEFAULT TRUE," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")");

        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_attribute_definition (" +
                " attribute_id BIGSERIAL PRIMARY KEY," +
                " business_line_key VARCHAR(40) NOT NULL," +
                " attribute_key VARCHAR(80) NOT NULL," +
                " display_name VARCHAR(100) NOT NULL," +
                " data_type VARCHAR(20) NOT NULL," +
                " is_required BOOLEAN NOT NULL DEFAULT FALSE," +
                " is_filterable BOOLEAN NOT NULL DEFAULT FALSE," +
                " is_searchable BOOLEAN NOT NULL DEFAULT FALSE," +
                " field_schema JSONB NOT NULL DEFAULT '{}'::jsonb," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " CONSTRAINT inventory_attribute_definition_data_type_ck CHECK (data_type IN ('TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'JSON'))," +
                " CONSTRAINT inventory_attribute_definition_unique_key UNIQUE (business_line_key, attribute_key)" +
                ")");

        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_template_attribute (" +
                " template_id BIGINT NOT NULL," +
                " attribute_id BIGINT NOT NULL," +
                " display_order INTEGER NOT NULL DEFAULT 0," +
                " is_required BOOLEAN NOT NULL DEFAULT FALSE," +
                " group_key VARCHAR(80)," +
                " default_value_jsonb JSONB," +
                " PRIMARY KEY (template_id, attribute_id)," +
                " CONSTRAINT inventory_template_attribute_template_fk FOREIGN KEY (template_id) REFERENCES " + schemaName + ".inventory_product_template (template_id) ON DELETE CASCADE," +
                " CONSTRAINT inventory_template_attribute_attribute_fk FOREIGN KEY (attribute_id) REFERENCES " + schemaName + ".inventory_attribute_definition (attribute_id) ON DELETE CASCADE" +
                ")");

        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_product_attribute_value (" +
                " product_id BIGINT NOT NULL," +
                " attribute_id BIGINT NOT NULL," +
                " value_text TEXT," +
                " value_number DOUBLE PRECISION," +
                " value_boolean BOOLEAN," +
                " value_date DATE," +
                " value_jsonb JSONB," +
                " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " PRIMARY KEY (product_id, attribute_id)," +
                " CONSTRAINT inventory_product_attribute_value_product_fk FOREIGN KEY (product_id) REFERENCES " + schemaName + ".inventory_product (product_id) ON DELETE CASCADE," +
                " CONSTRAINT inventory_product_attribute_value_attribute_fk FOREIGN KEY (attribute_id) REFERENCES " + schemaName + ".inventory_attribute_definition (attribute_id) ON DELETE CASCADE" +
                ")");
        statements.add("CREATE INDEX IF NOT EXISTS idx_inventory_product_attribute_value_attribute_text ON " + schemaName + ".inventory_product_attribute_value (attribute_id, value_text)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_inventory_product_attribute_value_attribute_number ON " + schemaName + ".inventory_product_attribute_value (attribute_id, value_number)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_inventory_product_attribute_value_attribute_boolean ON " + schemaName + ".inventory_product_attribute_value (attribute_id, value_boolean)");

        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_uom_dimension (" +
                " dimension_key VARCHAR(20) PRIMARY KEY," +
                " display_name VARCHAR(60) NOT NULL," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")");
        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_uom_unit (" +
                " uom_code VARCHAR(20) PRIMARY KEY," +
                " display_name VARCHAR(60) NOT NULL," +
                " dimension_key VARCHAR(20) NOT NULL," +
                " precision_scale INTEGER NOT NULL DEFAULT 0," +
                " is_base BOOLEAN NOT NULL DEFAULT FALSE," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " CONSTRAINT inventory_uom_unit_dimension_fk FOREIGN KEY (dimension_key) REFERENCES " + schemaName + ".inventory_uom_dimension (dimension_key) ON DELETE CASCADE" +
                ")");
        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_uom_conversion (" +
                " from_uom_code VARCHAR(20) NOT NULL," +
                " to_uom_code VARCHAR(20) NOT NULL," +
                " multiplier NUMERIC(18,6) NOT NULL," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " PRIMARY KEY (from_uom_code, to_uom_code)," +
                " CONSTRAINT inventory_uom_conversion_from_fk FOREIGN KEY (from_uom_code) REFERENCES " + schemaName + ".inventory_uom_unit (uom_code) ON DELETE CASCADE," +
                " CONSTRAINT inventory_uom_conversion_to_fk FOREIGN KEY (to_uom_code) REFERENCES " + schemaName + ".inventory_uom_unit (uom_code) ON DELETE CASCADE," +
                " CONSTRAINT inventory_uom_conversion_multiplier_ck CHECK (multiplier > 0)" +
                ")");
        statements.add("CREATE TABLE IF NOT EXISTS " + schemaName + ".inventory_pricing_policy (" +
                " pricing_policy_code VARCHAR(40) PRIMARY KEY," +
                " display_name VARCHAR(100) NOT NULL," +
                " strategy_type VARCHAR(40) NOT NULL," +
                " config_json JSONB NOT NULL DEFAULT '{}'::jsonb," +
                " is_active BOOLEAN NOT NULL DEFAULT TRUE," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")");
        statements.add("ALTER TABLE " + schemaName + ".inventory_product " +
                " ADD COLUMN IF NOT EXISTS base_uom_code VARCHAR(20) NOT NULL DEFAULT 'PCS'," +
                " ADD COLUMN IF NOT EXISTS pricing_policy_code VARCHAR(40) NOT NULL DEFAULT 'FIXED_RETAIL'");

        statements.add("INSERT INTO " + schemaName + ".inventory_product_template " +
                "(business_line_key, template_key, display_name, major_key, supports_serial, supports_batch, supports_expiry, supports_weight, is_system, is_active, created_at, updated_at) " +
                "SELECT business_line_key, template_key, display_name, major_key, supports_serial, supports_batch, supports_expiry, supports_weight, is_system, is_active, created_at, updated_at " +
                "FROM public.inventory_product_template ON CONFLICT (template_key) DO NOTHING");
        statements.add("INSERT INTO " + schemaName + ".inventory_attribute_definition " +
                "(business_line_key, attribute_key, display_name, data_type, is_required, is_filterable, is_searchable, field_schema, created_at, updated_at) " +
                "SELECT business_line_key, attribute_key, display_name, data_type, is_required, is_filterable, is_searchable, field_schema, created_at, updated_at " +
                "FROM public.inventory_attribute_definition ON CONFLICT (business_line_key, attribute_key) DO NOTHING");
        statements.add("INSERT INTO " + schemaName + ".inventory_template_attribute " +
                "(template_id, attribute_id, display_order, is_required, group_key, default_value_jsonb) " +
                "SELECT target_template.template_id, target_attribute.attribute_id, source.display_order, source.is_required, source.group_key, source.default_value_jsonb " +
                "FROM public.inventory_template_attribute source " +
                "JOIN public.inventory_product_template source_template ON source_template.template_id = source.template_id " +
                "JOIN public.inventory_attribute_definition source_attribute ON source_attribute.attribute_id = source.attribute_id " +
                "JOIN " + schemaName + ".inventory_product_template target_template ON target_template.template_key = source_template.template_key " +
                "JOIN " + schemaName + ".inventory_attribute_definition target_attribute ON target_attribute.business_line_key = source_attribute.business_line_key AND target_attribute.attribute_key = source_attribute.attribute_key " +
                "ON CONFLICT (template_id, attribute_id) DO NOTHING");

        statements.add("INSERT INTO " + schemaName + ".inventory_uom_dimension (dimension_key, display_name) VALUES " +
                "('COUNT', 'Count'), ('WEIGHT', 'Weight'), ('VOLUME', 'Volume') " +
                "ON CONFLICT (dimension_key) DO NOTHING");
        statements.add("INSERT INTO " + schemaName + ".inventory_uom_unit (uom_code, display_name, dimension_key, precision_scale, is_base) VALUES " +
                "('PCS', 'Piece', 'COUNT', 0, TRUE), " +
                "('BOX', 'Box', 'COUNT', 0, FALSE), " +
                "('PACK', 'Pack', 'COUNT', 0, FALSE), " +
                "('GRAM', 'Gram', 'WEIGHT', 3, TRUE), " +
                "('KILOGRAM', 'Kilogram', 'WEIGHT', 3, FALSE), " +
                "('ML', 'Milliliter', 'VOLUME', 2, TRUE), " +
                "('LITER', 'Liter', 'VOLUME', 2, FALSE) " +
                "ON CONFLICT (uom_code) DO UPDATE SET display_name = EXCLUDED.display_name, dimension_key = EXCLUDED.dimension_key, precision_scale = EXCLUDED.precision_scale, is_base = EXCLUDED.is_base, updated_at = CURRENT_TIMESTAMP");
        statements.add("INSERT INTO " + schemaName + ".inventory_uom_conversion (from_uom_code, to_uom_code, multiplier) VALUES " +
                "('KILOGRAM', 'GRAM', 1000.000000), ('LITER', 'ML', 1000.000000) " +
                "ON CONFLICT (from_uom_code, to_uom_code) DO UPDATE SET multiplier = EXCLUDED.multiplier");
        statements.add("INSERT INTO " + schemaName + ".inventory_uom_conversion (from_uom_code, to_uom_code, multiplier) VALUES " +
                "('KILOGRAM', 'GRAM', 1000.000000), ('LITER', 'ML', 1000.000000) " +
                "ON CONFLICT (from_uom_code, to_uom_code) DO UPDATE SET multiplier = EXCLUDED.multiplier");
        statements.add("INSERT INTO " + schemaName + ".inventory_pricing_policy (pricing_policy_code, display_name, strategy_type, config_json) VALUES " +
                "('FIXED_RETAIL', 'Fixed Retail Price', 'FIXED', '{}'::jsonb), " +
                "('MARKUP_COST', 'Markup From Cost', 'MARKUP', '{\"base\":\"buying_price\"}'::jsonb), " +
                "('WEIGHT_MARKET', 'Weight X Market Rate', 'WEIGHT_X_MARKET_RATE', '{\"market\":\"gold\"}'::jsonb), " +
                "('FORMULA', 'Formula Based', 'FORMULA', '{}'::jsonb), " +
                "('BATCH_BASED', 'Batch Based', 'BATCH_BASED', '{}'::jsonb) " +
                "ON CONFLICT (pricing_policy_code) DO UPDATE SET display_name = EXCLUDED.display_name, strategy_type = EXCLUDED.strategy_type, config_json = EXCLUDED.config_json, is_active = TRUE, updated_at = CURRENT_TIMESTAMP");
        statements.add("UPDATE " + schemaName + ".inventory_product SET base_uom_code = CASE WHEN business_line_key = 'GOLD' THEN 'GRAM' WHEN business_line_key = 'CHEMICAL' THEN 'LITER' ELSE 'PCS' END WHERE base_uom_code IS NULL OR base_uom_code = ''");
        statements.add("UPDATE " + schemaName + ".inventory_product SET pricing_policy_code = CASE WHEN business_line_key = 'GOLD' THEN 'WEIGHT_MARKET' WHEN business_line_key = 'CHEMICAL' THEN 'FORMULA' ELSE 'FIXED_RETAIL' END WHERE pricing_policy_code IS NULL OR pricing_policy_code = ''");
        statements.add("ALTER TABLE " + schemaName + ".inventory_product DROP CONSTRAINT IF EXISTS inventory_product_uom_fk");
        statements.add("ALTER TABLE " + schemaName + ".inventory_product DROP CONSTRAINT IF EXISTS inventory_product_pricing_policy_fk");
        statements.add("ALTER TABLE " + schemaName + ".inventory_product ADD CONSTRAINT inventory_product_uom_fk FOREIGN KEY (base_uom_code) REFERENCES " + schemaName + ".inventory_uom_unit (uom_code)");
        statements.add("ALTER TABLE " + schemaName + ".inventory_product ADD CONSTRAINT inventory_product_pricing_policy_fk FOREIGN KEY (pricing_policy_code) REFERENCES " + schemaName + ".inventory_pricing_policy (pricing_policy_code)");

        return statements;
    }
}
