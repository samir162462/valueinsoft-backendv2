package com.example.valueinsoftbackend.ai.sql;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AiSchemaMetadataService {

    private static final Set<String> SENSITIVE_COLUMN_PARTS = Set.of(
            "password", "token", "secret", "credential", "apikey", "api_key", "key_hash", "raw_content", "content_text"
    );

    private static final Map<String, String> BUSINESS_MEANINGS = Map.ofEntries(
            Map.entry("inventory_product", "product catalog, names, barcodes, pricing, supplier links"),
            Map.entry("inventory_branch_stock_balance", "branch stock balance and availability"),
            Map.entry("inventory_stock_ledger", "inventory movement, purchases, returns, stock reasons"),
            Map.entry("posorder", "sales orders, revenue, cashier, payment type, order date"),
            Map.entry("posorderdetail", "sales order lines, products sold, quantities, line totals"),
            Map.entry("supplier", "supplier master data and payable balances"),
            Map.entry("supplierbproduct", "supplier purchase/product intake rows"),
            Map.entry("client", "customer master data"),
            Map.entry("clientreceipts", "customer payments and receipts"),
            Map.entry("expenses", "branch expense transactions"),
            Map.entry("posshiftperiod", "POS shift sessions, cash, opening/closing totals"),
            Map.entry("companyanalysis", "daily branch business metrics")
    );

    private final JdbcTemplate jdbcTemplate;
    private final AiProperties properties;
    private final Map<String, CachedSchema> cache = new ConcurrentHashMap<>();

    public AiSchemaMetadataService(JdbcTemplate jdbcTemplate, AiProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public String compactPromptCatalog(long companyId, Long branchId, String question) {
        List<TableSummary> tables = getSchema(companyId, branchId);
        List<TableSummary> selected = tables.stream()
                .sorted(Comparator.comparingInt((TableSummary table) -> score(table, question)).reversed())
                .filter(table -> score(table, question) > 0)
                .limit(Math.max(1, properties.getMaxSchemaTables()))
                .toList();

        if (selected.isEmpty()) {
            selected = tables.stream()
                    .limit(Math.max(1, properties.getMaxSchemaTables()))
                    .toList();
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Approved compact PostgreSQL schema. Use only these tables and explicit columns.\n");
        builder.append("Always use :companyId for public company-owned tables and :branchId for branch-scoped tables.\n");
        builder.append("Dynamic branch tables already belong to the selected branch.\n\n");

        for (TableSummary table : selected) {
            builder.append("- ").append(table.qualifiedName()).append(" ")
                    .append(table.alias()).append(": ")
                    .append(table.businessMeaning()).append(". Columns: ");
            builder.append(table.columns().stream()
                    .limit(Math.max(1, properties.getMaxColumnsPerTable()))
                    .map(ColumnSummary::promptText)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("(none)"));
            builder.append("\n");
        }

        builder.append("""

                Safety:
                - Return one SELECT only, no semicolon, no comments, no DML/DDL.
                - Never use SELECT * or alias.*.
                - Never select password/token/secret/credential/key/raw content fields.
                - Non-aggregate list queries must include LIMIT 50 or less.
                """);
        return builder.toString();
    }

    private List<TableSummary> getSchema(long companyId, Long branchId) {
        String key = companyId + ":" + (branchId == null ? "all" : branchId);
        CachedSchema cached = cache.get(key);
        if (cached != null && Duration.between(cached.refreshedAt(), Instant.now()).toMinutes() < Math.max(1, properties.getCacheTtlMinutes())) {
            return cached.tables();
        }

        List<TableSummary> tables = loadSchema(companyId, branchId);
        cache.put(key, new CachedSchema(Instant.now(), tables));
        return tables;
    }

    private List<TableSummary> loadSchema(long companyId, Long branchId) {
        String tenantSchema = TenantSqlIdentifiers.companySchema(companyId);
        List<String> schemas = List.of("public", tenantSchema);
        String sql = """
                SELECT table_schema, table_name, column_name, data_type
                FROM information_schema.columns
                WHERE table_schema IN (?, ?)
                ORDER BY table_schema, table_name, ordinal_position
                """;
        Map<String, MutableTable> tables = new LinkedHashMap<>();
        jdbcTemplate.query(sql, ps -> {
            ps.setString(1, schemas.get(0));
            ps.setString(2, schemas.get(1));
        }, rs -> {
            String schema = rs.getString("table_schema");
            String tableName = rs.getString("table_name");
            String columnName = rs.getString("column_name");
            String dataType = rs.getString("data_type");
            if (isSensitive(columnName)) {
                return;
            }
            if (!isBusinessRelevantTable(tableName, branchId)) {
                return;
            }
            String key = schema + "." + tableName;
            MutableTable table = tables.computeIfAbsent(key, ignored -> new MutableTable(schema, tableName, businessMeaning(tableName)));
            if (table.columns.size() < Math.max(1, properties.getMaxColumnsPerTable())) {
                table.columns.add(new ColumnSummary(columnName, dataType, columnRole(columnName, dataType)));
            }
        });
        List<TableSummary> result = new ArrayList<>();
        for (MutableTable table : tables.values()) {
            result.add(new TableSummary(table.schema, table.tableName, aliasFor(table.tableName), table.meaning, List.copyOf(table.columns)));
        }
        log.debug("AI schema metadata loaded companyId={} branchId={} tableCount={}", companyId, branchId, result.size());
        return result;
    }

    private boolean isBusinessRelevantTable(String tableName, Long branchId) {
        String normalized = normalize(tableName);
        if (normalized.startsWith("ai_") || normalized.startsWith("flyway_")) {
            return false;
        }
        if (branchId != null && normalized.endsWith("_" + branchId)) {
            return true;
        }
        return BUSINESS_MEANINGS.keySet().stream().anyMatch(normalized::contains)
                || normalized.contains("finance")
                || normalized.contains("branch")
                || normalized.contains("billing");
    }

    private int score(TableSummary table, String question) {
        String normalizedQuestion = normalize(question);
        String normalizedTable = normalize(table.tableName());
        int score = 1;
        if (normalizedQuestion.contains("sale") || normalizedQuestion.contains("selling") || normalizedQuestion.contains("revenue")) {
            if (normalizedTable.contains("posorder")) score += 20;
        }
        if (normalizedQuestion.contains("product") || normalizedQuestion.contains("inventory") || normalizedQuestion.contains("stock")) {
            if (normalizedTable.contains("product") || normalizedTable.contains("stock") || normalizedTable.contains("inventory")) score += 20;
        }
        if (normalizedQuestion.contains("supplier") || normalizedQuestion.contains("payable")) {
            if (normalizedTable.contains("supplier")) score += 20;
        }
        if (normalizedQuestion.contains("customer") || normalizedQuestion.contains("client")) {
            if (normalizedTable.contains("client")) score += 20;
        }
        if (normalizedQuestion.contains("branch")) {
            if (normalizedTable.contains("branch") || normalizedTable.contains("companyanalysis")) score += 20;
        }
        if (normalizedQuestion.contains("expense") || normalizedQuestion.contains("profit")) {
            if (normalizedTable.contains("expense") || normalizedTable.contains("finance") || normalizedTable.contains("posorder")) score += 15;
        }
        return score;
    }

    private String businessMeaning(String tableName) {
        String normalized = normalize(tableName).replaceAll("_\\d+$", "");
        return BUSINESS_MEANINGS.entrySet().stream()
                .filter(entry -> normalized.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("approved business data");
    }

    private String aliasFor(String tableName) {
        String normalized = normalize(tableName);
        if (normalized.contains("orderdetail")) return "od";
        if (normalized.contains("order")) return "o";
        if (normalized.contains("product")) return "p";
        if (normalized.contains("supplier")) return "s";
        if (normalized.contains("client")) return "c";
        if (normalized.contains("branch")) return "b";
        return "t";
    }

    private String columnRole(String columnName, String dataType) {
        String normalized = normalize(columnName);
        if (normalized.contains("branch")) return "branch";
        if (normalized.contains("company") || normalized.contains("tenant")) return "tenant";
        if (normalized.contains("date") || normalized.contains("time") || dataType.contains("timestamp")) return "date";
        if (dataType.contains("int") || dataType.contains("numeric") || dataType.contains("double")) return "number";
        return "";
    }

    private boolean isSensitive(String columnName) {
        String normalized = normalize(columnName);
        return SENSITIVE_COLUMN_PARTS.stream().anyMatch(normalized::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\"", "").toLowerCase(Locale.ROOT);
    }

    private record CachedSchema(Instant refreshedAt, List<TableSummary> tables) {
    }

    private record TableSummary(String schema, String tableName, String alias, String businessMeaning, List<ColumnSummary> columns) {
        String qualifiedName() {
            return schema + ".\"" + tableName + "\"";
        }
    }

    private record ColumnSummary(String name, String dataType, String role) {
        String promptText() {
            return role == null || role.isBlank()
                    ? name + " " + dataType
                    : name + " " + dataType + " " + role;
        }
    }

    private static final class MutableTable {
        private final String schema;
        private final String tableName;
        private final String meaning;
        private final List<ColumnSummary> columns = new ArrayList<>();

        private MutableTable(String schema, String tableName, String meaning) {
            this.schema = schema;
            this.tableName = tableName;
            this.meaning = meaning;
        }
    }
}
