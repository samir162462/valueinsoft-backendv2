package com.example.valueinsoftbackend.DatabaseRequests;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
@Slf4j
public class DbBranch {

    private static final RowMapper<Branch> BRANCH_ROW_MAPPER = new RowMapper<>() {
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

    private final JdbcTemplate jdbcTemplate;

    public DbBranch(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Branch> getBranchByCompanyId(int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String sql = "SELECT \"branchId\", \"branchName\", \"branchLocation\", \"companyId\", \"branchEstTime\" " +
                "FROM public.\"Branch\" WHERE \"companyId\" = ?";
        return jdbcTemplate.query(sql, BRANCH_ROW_MAPPER, companyId);
    }

    public Branch getBranchById(int branchId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        String sql = "SELECT \"branchId\", \"branchName\", \"branchLocation\", \"companyId\", \"branchEstTime\" " +
                "FROM public.\"Branch\" WHERE \"branchId\" = ?";
        List<Branch> branches = jdbcTemplate.query(sql, BRANCH_ROW_MAPPER, branchId);
        if (branches.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BRANCH_NOT_FOUND", "Branch was not found");
        }
        return branches.get(0);
    }

    public List<Branch> getAllBranches() {
        String sql = "SELECT \"branchId\", \"branchName\", \"branchLocation\", \"companyId\", \"branchEstTime\" " +
                "FROM public.\"Branch\"";
        return jdbcTemplate.query(sql, BRANCH_ROW_MAPPER);
    }

    public int getBranchIdByCompanyNameAndBranchName(int companyId, String branchName) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String sql = "SELECT \"branchId\" FROM public.\"Branch\" WHERE \"companyId\" = ? AND \"branchName\" = ?";
        try {
            Integer branchId = jdbcTemplate.queryForObject(sql, Integer.class, companyId, branchName);
            return branchId == null ? -1 : branchId;
        } catch (Exception e) {
            log.debug("Unable to resolve branch id for company {} and branch {}", companyId, branchName, e);
            return -1;
        }
    }

    private boolean checkExistBranchName(String branchName) {
        String sql = "SELECT COUNT(*) FROM public.\"Branch\" WHERE \"branchName\" = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, branchName);
        return count != null && count > 0;
    }

    public int createBranchWithTables(String branchName, String branchLocation, int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        if (checkExistBranchName(branchName)) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_NAME_EXISTS", "The Branch Name existed!");
        }

        String insertBranchSql = "INSERT INTO public.\"Branch\" (\"branchName\", \"branchLocation\", \"companyId\", \"branchEstTime\") " +
                "VALUES (?, ?, ?, ?)";
        int result = jdbcTemplate.update(insertBranchSql, branchName, branchLocation, companyId, new Timestamp(System.currentTimeMillis()));
        if (result != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BRANCH_CREATE_FAILED", "The branch not added due to error!");
        }

        int branchId = getBranchIdByCompanyNameAndBranchName(companyId, branchName);
        if (branchId <= 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BRANCH_ID_RESOLUTION_FAILED", "Branch was inserted but could not be resolved");
        }

        boolean provisioned = createPosProductTable(branchId, companyId)
                && createOrderTable(branchId, companyId)
                && createOrderDetailsTable(branchId, companyId)
                && createSupplierTable(branchId, companyId)
                && createTransactionTable(branchId, companyId);

        if (!provisioned) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BRANCH_PROVISION_FAILED", "Branch tables could not be provisioned");
        }

        return branchId;
    }

    public String addBranch(String branchName, String branchLocation, int companyId) {
        createBranchWithTables(branchName, branchLocation, companyId);
        return "The Branch added!";
    }

    public boolean deleteBranch(int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");

        try {
            jdbcTemplate.update("DELETE FROM public.\"Branch\" WHERE \"branchId\" = ?", branchId);
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + TenantSqlIdentifiers.orderDetailTable(companyId, branchId) + " CASCADE");
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " CASCADE");
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + TenantSqlIdentifiers.productTable(companyId, branchId) + " CASCADE");
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId) + " CASCADE");
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + TenantSqlIdentifiers.supplierTable(companyId, branchId) + " CASCADE");
            log.info("Deleted branch {} for company {}", branchId, companyId);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete branch {} for company {}", branchId, companyId, e);
            return false;
        }
    }

    public boolean createPosProductTable(int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String sql = "CREATE TABLE IF NOT EXISTS " + TenantSqlIdentifiers.productTable(companyId, branchId) + " (" +
                "    \"productId\" SERIAL PRIMARY KEY," +
                "    \"productName\" VARCHAR(30)," +
                "    \"buyingDay\" TIMESTAMP," +
                "    \"activationPeriod\" INTEGER," +
                "    \"rPrice\" INTEGER," +
                "    \"lPrice\" INTEGER," +
                "    \"bPrice\" INTEGER," +
                "    \"companyName\" VARCHAR(30)," +
                "    type VARCHAR(15)," +
                "    \"ownerName\" VARCHAR(20)," +
                "    serial VARCHAR(35)," +
                "    \"desc\" VARCHAR(60)," +
                "    \"batteryLife\" INTEGER," +
                "    \"ownerPhone\" VARCHAR(14)," +
                "    \"ownerNI\" VARCHAR(18)," +
                "    quantity INTEGER," +
                "    \"pState\" VARCHAR(10)," +
                "    \"supplierId\" INTEGER," +
                "    \"major\" VARCHAR(30)," +
                "    \"imgFile\" TEXT" +
                ")";
        return executeProvisioningSql(sql, "PosProduct", branchId, companyId);
    }

    public boolean createOrderTable(int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String sql = "CREATE TABLE IF NOT EXISTS " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " (" +
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
        return executeProvisioningSql(sql, "PosOrder", branchId, companyId);
    }

    public boolean createSupplierTable(int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String sql = "CREATE TABLE IF NOT EXISTS " + TenantSqlIdentifiers.supplierTable(companyId, branchId) + " (" +
                "    \"supplierId\" SERIAL PRIMARY KEY," +
                "    \"SupplierName\" VARCHAR NOT NULL," +
                "    \"supplierPhone1\" VARCHAR(14)," +
                "    \"supplierPhone2\" VARCHAR(14)," +
                "    \"SupplierLocation\" VARCHAR," +
                "    \"suplierMajor\" VARCHAR(20)," +
                "    \"supplierRemainig\" INTEGER DEFAULT 0," +
                "    \"supplierTotalSales\" INTEGER DEFAULT 0" +
                ")";
        return executeProvisioningSql(sql, "supplier", branchId, companyId);
    }

    public boolean createTransactionTable(int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String sql = "CREATE TABLE IF NOT EXISTS " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId) + " (" +
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
                "    FOREIGN KEY (\"productId\") REFERENCES " + TenantSqlIdentifiers.productTable(companyId, branchId) + " (\"productId\")" +
                ")";
        return executeProvisioningSql(sql, "InventoryTransactions", branchId, companyId);
    }

    public boolean createOrderDetailsTable(int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String sql = "CREATE TABLE IF NOT EXISTS " + TenantSqlIdentifiers.orderDetailTable(companyId, branchId) + " (" +
                "    \"orderDetailsId\" SERIAL PRIMARY KEY," +
                "    \"itemId\" INTEGER," +
                "    \"itemName\" VARCHAR," +
                "    \"quantity\" INTEGER," +
                "    \"price\" INTEGER," +
                "    \"total\" INTEGER," +
                "    \"orderId\" INTEGER," +
                "    \"productId\" INTEGER," +
                "    \"bouncedBack\" INTEGER," +
                "    FOREIGN KEY (\"orderId\") REFERENCES " + TenantSqlIdentifiers.orderTable(companyId, branchId) + " (\"orderId\") ON DELETE CASCADE ON UPDATE CASCADE" +
                ")";
        return executeProvisioningSql(sql, "PosOrderDetail", branchId, companyId);
    }

    private boolean executeProvisioningSql(String sql, String tableLabel, int branchId, int companyId) {
        try {
            jdbcTemplate.execute(sql);
            log.info("Provisioned {} table for company {} branch {}", tableLabel, companyId, branchId);
            return true;
        } catch (Exception e) {
            log.error("Failed to provision {} table for company {} branch {}", tableLabel, companyId, branchId, e);
            return false;
        }
    }
}
