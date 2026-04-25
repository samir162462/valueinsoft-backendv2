package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Expenses;
import com.example.valueinsoftbackend.Model.Sales.ExpensesSum;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class DbExpenses {

    private static final RowMapper<Expenses> expensesRowMapper = (rs, rowNum) -> new Expenses(
            rs.getInt("eId"),
            rs.getString("type"),
            rs.getBigDecimal("amount"),
            rs.getTimestamp("time"),
            rs.getInt("branchId"),
            rs.getString("user"),
            rs.getString("name"),
            rs.getString("period"),
            rs.getObject("expense_account_id", java.util.UUID.class),
            rs.getObject("payment_account_id", java.util.UUID.class),
            rs.getObject("posted_journal_entry_id", java.util.UUID.class),
            null // nextDueDate (not in operational Expenses table)
    );

    private static final RowMapper<com.example.valueinsoftbackend.Model.ExpensesStatic> expensesStaticRowMapper = (rs, rowNum) -> new com.example.valueinsoftbackend.Model.ExpensesStatic(
            rs.getInt("eId"),
            rs.getString("type"),
            rs.getBigDecimal("amount"),
            rs.getTimestamp("time"),
            rs.getInt("branchId"),
            rs.getString("user"),
            rs.getString("name"),
            rs.getString("period"),
            rs.getObject("expense_account_id", java.util.UUID.class),
            rs.getObject("payment_account_id", java.util.UUID.class),
            rs.getObject("posted_journal_entry_id", java.util.UUID.class),
            rs.getObject("last_posted_date") != null ? rs.getDate("last_posted_date").toLocalDate() : null,
            rs.getObject("next_due_date") != null ? rs.getDate("next_due_date").toLocalDate() : null,
            rs.getBoolean("auto_post_enabled")
    );

    private static final RowMapper<com.example.valueinsoftbackend.Model.ExpensesStaticHistory> expensesStaticHistoryRowMapper = (rs, rowNum) -> new com.example.valueinsoftbackend.Model.ExpensesStaticHistory(
            rs.getInt("id"),
            0, // companyId not in table, handled via schema
            rs.getInt("static_expense_id"),
            rs.getDate("due_date").toLocalDate(),
            rs.getDate("posting_date").toLocalDate(),
            rs.getBigDecimal("amount"),
            rs.getString("status"),
            (Integer) rs.getObject("expense_id"),
            rs.getObject("journal_entry_id", java.util.UUID.class),
            rs.getTimestamp("created_at"),
            (Integer) rs.getObject("created_by")
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
        String sql = "SELECT \"eId\", type, amount::money::numeric AS amount, \"time\", \"branchId\", \"user\", name, period, " +
                "expense_account_id, payment_account_id, posted_journal_entry_id " +
                "FROM " + TenantSqlIdentifiers.expensesTable(companyId, isStatic) + " WHERE \"branchId\" = ?";
        return jdbcTemplate.query(sql, expensesRowMapper, branchId);
    }

    public List<com.example.valueinsoftbackend.Model.ExpensesStatic> getAllStaticExpenses(int branchId, int companyId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        self.provisionExpenseTables(companyId);
        String sql = "SELECT \"eId\", type, amount::money::numeric AS amount, \"time\", \"branchId\", \"user\", name, period, " +
                "expense_account_id, payment_account_id, posted_journal_entry_id, " +
                "last_posted_date, next_due_date, auto_post_enabled " +
                "FROM " + TenantSqlIdentifiers.expensesTable(companyId, true) + " WHERE \"branchId\" = ?";
        return jdbcTemplate.query(sql, expensesStaticRowMapper, branchId);
    }

    public com.example.valueinsoftbackend.Model.ExpensesStatic getStaticExpenseById(int companyId, int branchId, int eId) {
        self.provisionExpenseTables(companyId);
        String sql = "SELECT \"eId\", type, amount::money::numeric AS amount, \"time\", \"branchId\", \"user\", name, period, " +
                "expense_account_id, payment_account_id, posted_journal_entry_id, " +
                "last_posted_date, next_due_date, auto_post_enabled " +
                "FROM " + TenantSqlIdentifiers.expensesTable(companyId, true) + " WHERE \"branchId\" = ? AND \"eId\" = ?";
        List<com.example.valueinsoftbackend.Model.ExpensesStatic> results = jdbcTemplate.query(sql, expensesStaticRowMapper, branchId, eId);
        return results.isEmpty() ? null : results.get(0);
    }

    public void updateStaticExpenseRecurrence(int companyId, int eId, java.time.LocalDate lastPostedDate, java.time.LocalDate nextDueDate) {
        String sql = "UPDATE " + TenantSqlIdentifiers.expensesTable(companyId, true) +
                " SET last_posted_date = ?, next_due_date = ? WHERE \"eId\" = ?";
        jdbcTemplate.update(sql, java.sql.Date.valueOf(lastPostedDate), java.sql.Date.valueOf(nextDueDate), eId);
    }

    public void recordStaticExpenseHistory(int companyId, com.example.valueinsoftbackend.Model.ExpensesStaticHistory history) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.expensesStaticHistoryTable(companyId) +
                " (static_expense_id, due_date, posting_date, amount, status, expense_id, journal_entry_id, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(
                sql,
                history.getStaticExpenseId(),
                java.sql.Date.valueOf(history.getDueDate()),
                java.sql.Date.valueOf(history.getPostingDate()),
                history.getAmount(),
                history.getStatus(),
                history.getExpenseId(),
                history.getJournalEntryId(),
                history.getCreatedBy()
        );
    }

    public List<com.example.valueinsoftbackend.Model.ExpensesStaticHistory> getStaticExpenseHistory(int companyId, int eId) {
        self.provisionExpenseTables(companyId);
        String sql = "SELECT id, static_expense_id, due_date, posting_date, amount::money::numeric AS amount, status, expense_id, journal_entry_id, created_at, created_by " +
                "FROM " + TenantSqlIdentifiers.expensesStaticHistoryTable(companyId) +
                " WHERE static_expense_id = ? ORDER BY due_date DESC";
        return jdbcTemplate.query(sql, expensesStaticHistoryRowMapper, eId);
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

        String sql;
        if (isStatic) {
            sql = "INSERT INTO " + TenantSqlIdentifiers.expensesTable(companyId, true) +
                    " (type, amount, \"time\", \"branchId\", \"user\", name, period, expense_account_id, payment_account_id, next_due_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            return String.valueOf(jdbcTemplate.update(
                    sql,
                    expenses.getType(),
                    expenses.getAmount(),
                    new Timestamp(System.currentTimeMillis()),
                    branchId,
                    expenses.getUser(),
                    expenses.getName(),
                    expenses.getPeriod(),
                    expenses.getExpenseAccountId(),
                    expenses.getPaymentAccountId(),
                    expenses.getNextDueDate() != null ? java.sql.Date.valueOf(expenses.getNextDueDate()) : null
            ));
        } else {
            sql = "INSERT INTO " + TenantSqlIdentifiers.expensesTable(companyId, false) +
                    " (type, amount, \"time\", \"branchId\", \"user\", name, period, expense_account_id, payment_account_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(
                    sql,
                    expenses.getType(),
                    expenses.getAmount(),
                    expenses.getTime() != null ? expenses.getTime() : new Timestamp(System.currentTimeMillis()),
                    branchId,
                    expenses.getUser(),
                    expenses.getName(),
                    expenses.getPeriod(),
                    expenses.getExpenseAccountId(),
                    expenses.getPaymentAccountId()
            );
            return "Record Inserted";
        }
    }

    public String updateExpenses(int branchId, int companyId, Expenses expenses, boolean isStatic) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        log.debug("Updating expense eId: {}, expenseAccountId: {}, paymentAccountId: {}, isStatic: {}", 
                expenses.getEId(), expenses.getExpenseAccountId(), expenses.getPaymentAccountId(), isStatic);
        String sql;
        int rows;
        if (isStatic) {
            sql = "UPDATE " + TenantSqlIdentifiers.expensesTable(companyId, true) +
                    " SET type = ?, amount = ?, \"branchId\" = ?, \"user\" = ?, name = ?, period = ?, " +
                    "expense_account_id = ?, payment_account_id = ?, next_due_date = ? WHERE \"eId\" = ?";
            rows = jdbcTemplate.update(
                    sql,
                    expenses.getType(),
                    expenses.getAmount(),
                    branchId,
                    expenses.getUser(),
                    expenses.getName(),
                    expenses.getPeriod(),
                    expenses.getExpenseAccountId(),
                    expenses.getPaymentAccountId(),
                    expenses.getNextDueDate() != null ? java.sql.Date.valueOf(expenses.getNextDueDate()) : null,
                    expenses.getEId()
            );
        } else {
            sql = "UPDATE " + TenantSqlIdentifiers.expensesTable(companyId, false) +
                    " SET type = ?, amount = ?, \"time\" = ?, \"branchId\" = ?, \"user\" = ?, name = ?, period = ?, " +
                    "expense_account_id = ?, payment_account_id = ? WHERE \"eId\" = ?";
            rows = jdbcTemplate.update(
                    sql,
                    expenses.getType(),
                    expenses.getAmount(),
                    expenses.getTime(),
                    branchId,
                    expenses.getUser(),
                    expenses.getName(),
                    expenses.getPeriod(),
                    expenses.getExpenseAccountId(),
                    expenses.getPaymentAccountId(),
                    expenses.getEId()
            );
        }
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EXPENSE_NOT_FOUND", "Record Not Updated");
        }
        return "Record Updated";
    }

    public void markExpensePosted(int companyId, int eId, java.util.UUID journalEntryId, boolean isStatic) {
        String sql = "UPDATE " + TenantSqlIdentifiers.expensesTable(companyId, isStatic) +
                " SET posted_journal_entry_id = ? WHERE \"eId\" = ?";
        jdbcTemplate.update(sql, journalEntryId, eId);
    }

    public JdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    private boolean existsStaticExpense(int branchId, int companyId, String name) {
        String sql = "SELECT EXISTS (SELECT 1 FROM " + TenantSqlIdentifiers.expensesTable(companyId, true) +
                " WHERE \"branchId\" = ? AND name = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, branchId, name);
        return Boolean.TRUE.equals(exists);
    }

    private boolean isRelationNotFound(BadSqlGrammarException e) {
        String sqlState = e.getSQLException().getSQLState();
        // 42P01: relation does not exist
        // 42703: undefined_column (happens when the table exists but column is missing)
        return "42P01".equals(sqlState) || "42703".equals(sqlState) || (sqlState != null && sqlState.startsWith("42"));
    }

    public void provisionExpenseTables(int companyId) {
        String schema = TenantSqlIdentifiers.companySchema(companyId);
        String owner = "postgres"; // Fallback to default postgres owner

        try {
            // Ensure tables exist
            jdbcTemplate.execute(createTableSql(schema, "Expenses", owner));
            jdbcTemplate.execute(createTableSql(schema, "ExpensesStatic", owner));

            // Ensure columns exist (for existing tables)
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"Expenses\" ADD COLUMN IF NOT EXISTS period character varying(20)");
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"ExpensesStatic\" ADD COLUMN IF NOT EXISTS period character varying(20)");
            
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"Expenses\" ADD COLUMN IF NOT EXISTS expense_account_id uuid");
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"Expenses\" ADD COLUMN IF NOT EXISTS payment_account_id uuid");
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"Expenses\" ADD COLUMN IF NOT EXISTS posted_journal_entry_id uuid");
            
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"ExpensesStatic\" ADD COLUMN IF NOT EXISTS expense_account_id uuid");
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"ExpensesStatic\" ADD COLUMN IF NOT EXISTS payment_account_id uuid");
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"ExpensesStatic\" ADD COLUMN IF NOT EXISTS posted_journal_entry_id uuid");

            // Recurrence columns
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"ExpensesStatic\" ADD COLUMN IF NOT EXISTS last_posted_date date");
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"ExpensesStatic\" ADD COLUMN IF NOT EXISTS next_due_date date");
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"ExpensesStatic\" ADD COLUMN IF NOT EXISTS auto_post_enabled boolean DEFAULT false");

            // History Table
            String historyTableSql = "CREATE TABLE IF NOT EXISTS " + schema + ".\"ExpensesStaticHistory\" (\n" +
                    "    id integer NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 2147483647 CACHE 1 ),\n" +
                    "    static_expense_id integer NOT NULL,\n" +
                    "    due_date date NOT NULL,\n" +
                    "    posting_date date NOT NULL,\n" +
                    "    amount money,\n" +
                    "    status character varying(20) DEFAULT 'posted',\n" +
                    "    expense_id integer,\n" +
                    "    journal_entry_id uuid,\n" +
                    "    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,\n" +
                    "    created_by integer,\n" +
                    "    CONSTRAINT \"ExpensesStaticHistory_pkey\" PRIMARY KEY (id),\n" +
                    "    CONSTRAINT \"ExpensesStaticHistory_static_fkey\" FOREIGN KEY (static_expense_id) REFERENCES " + schema + ".\"ExpensesStatic\" (\"eId\") ON DELETE CASCADE\n" +
                    ") TABLESPACE pg_default;";
            jdbcTemplate.execute(historyTableSql);
            jdbcTemplate.execute("ALTER TABLE " + schema + ".\"ExpensesStaticHistory\" OWNER to " + owner + ";");

        } catch (Exception e) {
            // Ignore if already handled
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
                "    period character varying(20) COLLATE pg_catalog.\"default\",\n" +
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
