package com.example.valueinsoftbackend.ai.sql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiSqlValidator {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|alter|create|truncate|grant|revoke|copy|call|execute|merge|vacuum|analyze|set|show)\\b"
    );
    private static final Pattern AGGREGATE = Pattern.compile("(?i)\\b(count|sum|avg|min|max)\\s*\\(");
    private static final Pattern WILDCARD_SELECT = Pattern.compile("(?i)(^|,|\\s)select\\s+\\*|\\b[a-z_][a-z0-9_]*\\.\\*");
    private static final Pattern SENSITIVE_IDENTIFIER = Pattern.compile(
            "(?i)(password|secret|token|credential|api_key|apikey|key_hash|raw_content|content_text)"
    );

    private final AiSqlSchemaCatalog schemaCatalog;

    public AiSqlValidator(AiSqlSchemaCatalog schemaCatalog) {
        this.schemaCatalog = schemaCatalog;
    }

    public String validate(String sql, long companyId, Long branchId) {
        String normalized = normalizeSql(sql);
        String lower = normalized.toLowerCase(Locale.ROOT);

        if (normalized.contains(";") || normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/")) {
            throw new AiSqlValidationException("Multiple statements and SQL comments are not allowed.");
        }
        if (FORBIDDEN.matcher(normalized).find()) {
            throw new AiSqlValidationException("The generated SQL contains a forbidden operation.");
        }
        if (WILDCARD_SELECT.matcher(normalized).find()) {
            throw new AiSqlValidationException("Wildcard SELECT is not allowed. Select explicit columns only.");
        }
        if (SENSITIVE_IDENTIFIER.matcher(normalized).find()) {
            throw new AiSqlValidationException("The generated SQL references sensitive fields.");
        }

        Statement statement = parseStatement(normalized);
        if (!(statement instanceof Select select)) {
            throw new AiSqlValidationException("Only SELECT statements are allowed.");
        }
        if (select instanceof SetOperationList) {
            throw new AiSqlValidationException("Set operations are not allowed in AI SQL queries.");
        }
        PlainSelect plainSelect = unwrapPlainSelect(select);
        if (plainSelect == null) {
            throw new AiSqlValidationException("Only plain SELECT statements are allowed.");
        }

        Set<String> allowedTables = schemaCatalog.allowedTables(companyId, branchId);
        boolean usesBranchScopedStock = false;
        boolean usesSchemaBranchScopedTable = false;
        boolean usesPublicCompanyTable = false;
        for (String tableName : new TablesNamesFinder().getTableList(statement)) {
            String table = schemaCatalog.normalizeTableName(tableName);
            if (!allowedTables.contains(table)) {
                throw new AiSqlValidationException("The generated SQL references an unapproved table: " + table);
            }
            if (table.endsWith(".inventory_branch_stock_balance")) {
                usesBranchScopedStock = true;
            }
            if (requiresBranchFilter(table)) {
                usesSchemaBranchScopedTable = true;
            }
            if (requiresCompanyFilter(table)) {
                usesPublicCompanyTable = true;
            }
        }
        if (new TablesNamesFinder().getTableList(statement).isEmpty()) {
            throw new AiSqlValidationException("The generated SQL must reference an approved table.");
        }
        if ((usesBranchScopedStock || usesSchemaBranchScopedTable) && branchId == null) {
            throw new AiSqlValidationException("Branch context is required for branch-scoped queries.");
        }
        if ((usesBranchScopedStock || usesSchemaBranchScopedTable) && !lower.contains(":branchid")) {
            throw new AiSqlValidationException("Branch-scoped queries must filter by :branchId.");
        }
        if (usesPublicCompanyTable && !lower.contains(":companyid")) {
            throw new AiSqlValidationException("Company-scoped public queries must filter by :companyId.");
        }

        boolean aggregateWithoutGrouping = hasAggregate(plainSelect) && plainSelect.getGroupBy() == null;
        if (!aggregateWithoutGrouping) {
            Limit limit = plainSelect.getLimit();
            if (limit == null || limit.getRowCount() == null) {
                throw new AiSqlValidationException("Non-aggregate SELECT queries must include LIMIT 50 or less.");
            }
            long rowCount = parseLimit(limit.getRowCount());
            if (rowCount < 1 || rowCount > 50) {
                throw new AiSqlValidationException("Query LIMIT must be between 1 and 50.");
            }
        }

        return normalized;
    }

    private Statement parseStatement(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException exception) {
            throw new AiSqlValidationException("The generated SQL could not be parsed.");
        }
    }

    private PlainSelect unwrapPlainSelect(Select select) {
        if (select instanceof PlainSelect plainSelect) {
            return plainSelect;
        }
        if (select instanceof ParenthesedSelect parenthesedSelect) {
            return unwrapPlainSelect(parenthesedSelect.getSelect());
        }
        return null;
    }

    private boolean hasAggregate(PlainSelect plainSelect) {
        for (SelectItem<?> item : plainSelect.getSelectItems()) {
            Expression expression = item.getExpression();
            if (expression != null && AGGREGATE.matcher(expression.toString()).find()) {
                return true;
            }
        }
        return false;
    }

    private long parseLimit(Expression expression) {
        try {
            return Long.parseLong(expression.toString());
        } catch (NumberFormatException exception) {
            throw new AiSqlValidationException("Query LIMIT must be a numeric literal.");
        }
    }

    private boolean requiresBranchFilter(String table) {
        return table.endsWith(".inventory_branch_stock_balance")
                || table.endsWith(".inventory_stock_ledger")
                || table.endsWith(".client")
                || table.endsWith(".clientreceipts")
                || table.endsWith(".posshiftperiod")
                || table.endsWith(".shift_event")
                || table.endsWith(".shift_cash_movement")
                || table.endsWith(".supplierreciepts")
                || table.endsWith(".supplierbproduct")
                || table.endsWith(".damagedlist")
                || table.endsWith(".expenses");
    }

    private boolean requiresCompanyFilter(String table) {
        if (!table.startsWith("public.")) {
            return false;
        }
        return !table.equals("public.platform_modules")
                && !table.equals("public.platform_capabilities")
                && !table.equals("public.role_definitions")
                && !table.equals("public.role_grants")
                && !table.equals("public.package_plans")
                && !table.equals("public.package_module_policies")
                && !table.equals("public.company_templates")
                && !table.equals("public.company_template_module_defaults")
                && !table.equals("public.company_template_workflow_defaults")
                && !table.equals("public.business_package_groups")
                && !table.equals("public.business_package_categories")
                && !table.equals("public.business_package_subcategories")
                && !table.equals("public.business_packages");
    }

    private String normalizeSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new AiSqlValidationException("The model did not produce SQL.");
        }
        return sql.trim().replaceAll("\\s+", " ");
    }
}
