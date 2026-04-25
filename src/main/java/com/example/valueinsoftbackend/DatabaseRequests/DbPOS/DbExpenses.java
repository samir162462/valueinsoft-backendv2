package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Expenses;
import com.example.valueinsoftbackend.Model.Sales.ExpensesSum;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class DbExpenses {

    private static final RowMapper<Expenses> expensesRowMapper = (rs, rowNum) -> new Expenses(
            rs.getInt("eId"),
            rs.getString("type"),
            rs.getBigDecimal("amount"),
            rs.getTimestamp("time"),
            rs.getInt("branchId"),
            rs.getString("user"),
            rs.getString("name")
    );

    private static final RowMapper<ExpensesSum> expensesSumRowMapper = (rs, rowNum) -> new ExpensesSum(
            rs.getDate("expenseDate"),
            rs.getInt("totalAmount"),
            rs.getInt("transactionCount")
    );

    private final JdbcTemplate jdbcTemplate;
    private DbExpenses self;

    public DbExpenses(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Autowired
    public void setSelf(@Lazy DbExpenses self) {
        this.self = self;
    }

    public List<Expenses> getAllExpensesItems(int branchId, int companyId, boolean isStatic) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        self.provisionExpenseTables(companyId);
        String sql = "SELECT \"eId\", type, amount::money::numeric AS amount, \"time\", \"branchId\", \"user\", name " +
                "FROM " + TenantSqlIdentifiers.expensesTable(companyId, isStatic) + " WHERE \"branchId\" = ?";
        return jdbcTemplate.query(sql, expensesRowMapper, branchId);
    }

    public List<ExpensesSum> getPurchasesExpensesByMonth(int branchId, int companyId, String timeText) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        String[] times = parseRange(timeText);
        String sql = "SELECT created_at::date AS expenseDate, sum(COALESCE(trans_total, 0)) AS totalAmount, count(created_at) AS transactionCount " +
                "FROM " + TenantSqlIdentifiers.inventoryStockLedgerTable(companyId) + " " +
                "WHERE branch_id = ? " +
                "AND created_at >= date_trunc('month', CAST(? AS timestamp)) " +
                "AND created_at < date_trunc('month', CAST(? AS timestamp)) + interval '1 month' " +
                "AND COALESCE(movement_type, '') NOT IN ('SALE_OUT', 'BOUNCE_BACK_IN') " +
                "GROUP BY created_at::date ORDER BY created_at::date ASC";
        return jdbcTemplate.query(sql, expensesSumRowMapper, branchId, times[0], times[1]);
    }

    public String addExpenses(int branchId, int companyId, Expenses expenses, boolean isStatic) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        self.provisionExpenseTables(companyId);
        if (isStatic && existsStaticExpense(branchId, companyId, expenses.getName())) {
            throw new ApiException(HttpStatus.CONFLICT, "EXPENSE_NAME_EXISTS", "the Name Exists! => " + expenses.getName());
        }

        String sql = "INSERT INTO " + TenantSqlIdentifiers.expensesTable(companyId, isStatic) +
                " (type, amount, \"time\", \"branchId\", \"user\", name) VALUES (?, ?, ?, ?, ?, ?)";
        int rows = jdbcTemplate.update(
                sql,
                expenses.getType(),
                expenses.getAmount(),
                new Timestamp(System.currentTimeMillis()),
                branchId,
                expenses.getUser(),
                expenses.getName()
        );
        if (rows != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EXPENSE_INSERT_FAILED", "Record Not Inserted");
        }
        return "Record Inserted";
    }

    public String updateExpenses(int branchId, int companyId, Expenses expenses) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        String sql = "UPDATE " + TenantSqlIdentifiers.expensesTable(companyId, false) +
                " SET type = ?, amount = ?, \"time\" = ?, \"branchId\" = ?, \"user\" = ?, name = ? WHERE \"eId\" = ?";
        int rows = jdbcTemplate.update(
                sql,
                expenses.getType(),
                expenses.getAmount(),
                expenses.getTime(),
                branchId,
                expenses.getUser(),
                expenses.getName(),
                expenses.getExId()
        );
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EXPENSE_NOT_FOUND", "Record Not Updated");
        }
        return "Record Updated";
    }

    private boolean existsStaticExpense(int branchId, int companyId, String name) {
        String sql = "SELECT EXISTS (SELECT 1 FROM " + TenantSqlIdentifiers.expensesTable(companyId, true) +
                " WHERE \"branchId\" = ? AND name = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, branchId, name);
        return Boolean.TRUE.equals(exists);
    }

    private boolean isRelationNotFound(BadSqlGrammarException e) {
        String sqlState = e.getSQLException().getSQLState();
        return "42P01".equals(sqlState) || (sqlState != null && sqlState.startsWith("42"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void provisionExpenseTables(int companyId) {
        String schema = TenantSqlIdentifiers.companySchema(companyId);
        String owner = "postgres"; // Fallback to default postgres owner

        try {
            jdbcTemplate.execute(createTableSql(schema, "Expenses", owner));
            jdbcTemplate.execute(createTableSql(schema, "ExpensesStatic", owner));
        } catch (Exception e) {
            // Ignore if already created by another thread
        }
    }

    private String createTableSql(String schema, String tableName, String owner) {
        return "CREATE TABLE IF NOT EXISTS " + schema + ".\"" + tableName + "\" (\n" +
                "    \"eId\" integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                "    type character varying(20) COLLATE pg_catalog.\"default\",\n" +
                "    amount money,\n" +
                "    \"time\" timestamp without time zone,\n" +
                "    \"branchId\" integer,\n" +
                "    \"user\" character varying(25) COLLATE pg_catalog.\"default\",\n" +
                "    name character varying(50) COLLATE pg_catalog.\"default\",\n" +
                "    CONSTRAINT \"" + tableName + "_pkey\" PRIMARY KEY (\"eId\")\n" +
                ") TABLESPACE pg_default;\n" +
                "ALTER TABLE " + schema + ".\"" + tableName + "\" OWNER to " + owner + ";";
    }

    private String[] parseRange(String timeText) {
        if (timeText == null || timeText.isBlank()) {
            throw new IllegalArgumentException("option is required");
        }
        String[] times = timeText.split("x");
        if (times.length != 2 || times[0].isBlank() || times[1].isBlank()) {
            throw new IllegalArgumentException("option must contain a start and end date separated by x");
        }
        return times;
    }
}
