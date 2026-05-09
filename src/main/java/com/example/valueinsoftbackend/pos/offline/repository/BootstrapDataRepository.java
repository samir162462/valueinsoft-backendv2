package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.dto.response.*;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
@Slf4j
public class BootstrapDataRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BootstrapDataRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public BootstrapPage<OfflineBootstrapProductItem> findProducts(Long companyId, Long branchId,
                                                                   Long afterProductId, int pageSize) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId.intValue());
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId.intValue());
        String sql = """
                SELECT
                    p.product_id          AS product_id,
                    p.serial              AS barcode,
                    p.product_name        AS product_name,
                    p.retail_price        AS retail_price,
                    p.lowest_price        AS lowest_price,
                    COALESCE(s.quantity, 0) AS current_stock,
                    p.major               AS category,
                    p.product_state       AS product_state,
                    p.base_uom_code       AS base_uom_code,
                    p.pricing_policy_code AS pricing_policy_code,
                    COALESCE(p.updated_at, p.created_at, p.buying_day) AS updated_at
                FROM %s p
                LEFT JOIN %s s ON s.product_id = p.product_id AND s.branch_id = ?
                WHERE p.product_id > ?
                ORDER BY p.product_id ASC
                LIMIT ?
                """.formatted(productTable, stockTable);

        List<OfflineBootstrapProductItem> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new OfflineBootstrapProductItem(
                rs.getLong("product_id"),
                rs.getString("barcode"),
                rs.getString("product_name"),
                rs.getBigDecimal("retail_price"),
                rs.getBigDecimal("lowest_price"),
                rs.getInt("current_stock"),
                rs.getString("category"),
                isActiveProductState(rs.getString("product_state")),
                rs.getString("base_uom_code"),
                rs.getString("pricing_policy_code"),
                toInstant(rs.getTimestamp("updated_at"))
        ), branchId, afterProductId, pageSize + 1);

        return toPage(rows, pageSize, OfflineBootstrapProductItem::productId, this::productUpdatedAt);
    }

    public BootstrapPage<OfflineBootstrapPriceItem> findPrices(Long companyId, Long branchId,
                                                               Long afterProductId, int pageSize) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId.intValue());
        String sql = """
                SELECT
                    p.product_id    AS product_id,
                    p.retail_price  AS retail_price,
                    p.lowest_price  AS lowest_price,
                    p.buying_price  AS buying_price,
                    p.pricing_policy_code AS pricing_policy_code,
                    NULL            AS strategy_type,
                    NULL            AS config_json,
                    COALESCE(p.updated_at, p.created_at, p.buying_day) AS updated_at
                FROM %s p
                WHERE p.product_id > ?
                ORDER BY p.product_id ASC
                LIMIT ?
                """.formatted(productTable);

        List<OfflineBootstrapPriceItem> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new OfflineBootstrapPriceItem(
                rs.getLong("product_id"),
                rs.getBigDecimal("retail_price"),
                rs.getBigDecimal("lowest_price"),
                rs.getBigDecimal("buying_price"),
                rs.getString("pricing_policy_code"),
                rs.getString("strategy_type"),
                rs.getString("config_json"),
                toInstant(rs.getTimestamp("updated_at"))
        ), afterProductId, pageSize + 1);

        return toPage(rows, pageSize, OfflineBootstrapPriceItem::productId, OfflineBootstrapPriceItem::updatedAt);
    }

    public List<OfflineBootstrapPaymentMethodItem> findPaymentMethods(Long companyId, Long branchId) {
        String mappingTable = "public.finance_account_mapping";
        String sql = """
                SELECT mapping_key
                FROM %s
                WHERE company_id = ?
                  AND (branch_id IS NULL OR branch_id = ?)
                  AND mapping_key LIKE 'pos.%%'
                  AND status = 'active'
                  AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
                """.formatted(mappingTable);

        List<String> keys = jdbcTemplate.queryForList(sql, String.class, companyId, branchId);
        List<OfflineBootstrapPaymentMethodItem> items = new ArrayList<>();

        if (keys.contains("pos.cash")) {
            items.add(new OfflineBootstrapPaymentMethodItem("CASH", "Cash", true, false));
        }
        if (keys.contains("pos.card")) {
            items.add(new OfflineBootstrapPaymentMethodItem("CARD", "Card", true, true));
        }
        if (keys.contains("pos.wallet")) {
            items.add(new OfflineBootstrapPaymentMethodItem("WALLET", "Digital Wallet", true, true));
        }
        if (keys.contains("pos.receivable")) {
            items.add(new OfflineBootstrapPaymentMethodItem("CREDIT", "Credit / Receivable", true, true));
        }

        // Always ensure at least Cash if nothing is mapped specifically
        if (items.isEmpty()) {
            items.add(new OfflineBootstrapPaymentMethodItem("CASH", "Cash (Default)", true, false));
        }

        return items;
    }

    public List<OfflineBootstrapPosSettingItem> findPosSettings(Long companyId, Long branchId) {
        String sql = """
                SELECT d.setting_key AS setting_key,
                       COALESCE(v.value_json, d.default_value_json)::text AS value_json
                FROM public.branch_setting_definitions d
                LEFT JOIN public.branch_setting_values v
                  ON v.setting_key = d.setting_key
                 AND v.tenant_id = ?
                 AND v.branch_id = ?
                 AND v.active = TRUE
                WHERE d.active = TRUE
                  AND d.group_key = 'pos'
                ORDER BY d.sort_order, d.setting_key
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new OfflineBootstrapPosSettingItem(
                rs.getString("setting_key"),
                readJsonValue(rs.getString("value_json"))
        ), companyId, branchId);
    }

    public List<OfflineBootstrapTaxItem> findTaxes(Long companyId) {
        String sql = """
                SELECT code, name, rate, status
                FROM public.finance_tax_code
                WHERE company_id = ?
                  AND status = 'active'
                  AND effective_from <= CURRENT_DATE
                  AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
                  AND tax_type IN ('sales_vat', 'exempt', 'zero_rated')
                ORDER BY code
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new OfflineBootstrapTaxItem(
                rs.getString("code"),
                rs.getString("name"),
                rs.getBigDecimal("rate"),
                "active".equalsIgnoreCase(rs.getString("status"))
        ), companyId);
    }

    public List<OfflineBootstrapDiscountItem> findDiscounts(Long companyId, Long branchId) {
        String offerTable = TenantSqlIdentifiers.offerTable(companyId.intValue());
        String sql = """
                SELECT offer_id, offer_name, offer_type, offer_value, is_active
                FROM %s
                WHERE branch_id = ?
                  AND is_active = TRUE
                  AND (start_date IS NULL OR start_date <= CURRENT_TIMESTAMP)
                  AND (end_date IS NULL OR end_date >= CURRENT_TIMESTAMP)
                ORDER BY offer_id
                """.formatted(offerTable);

        return jdbcTemplate.query(sql, (rs, rowNum) -> new OfflineBootstrapDiscountItem(
                "OFFER-" + rs.getLong("offer_id"),
                rs.getString("offer_name"),
                rs.getString("offer_type"),
                rs.getBigDecimal("offer_value"),
                rs.getBoolean("is_active")
        ), branchId);
    }

    public List<OfflineBootstrapCashierPermissionItem> findCashierPermissions(String principalName, Long companyId, Long branchId) {
        // This would ideally call AuthenticatedEffectiveConfigurationService,
        // but for repository-level consistency, we might just return the relevant subset.
        // For Phase 10N, we'll implement this in the Service layer instead.
        return Collections.emptyList();
    }

    private Object readJsonValue(String valueJson) {
        if (valueJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(valueJson, Object.class);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to parse branch setting JSON value for offline bootstrap");
            return valueJson;
        }
    }

    private <T> BootstrapPage<T> toPage(List<T> fetched, int pageSize, IdExtractor<T> idExtractor,
                                        UpdatedAtExtractor<T> updatedAtExtractor) {
        boolean hasMore = fetched.size() > pageSize;
        List<T> items = hasMore ? new ArrayList<>(fetched.subList(0, pageSize)) : fetched;
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            nextCursor = String.valueOf(idExtractor.extract(items.get(items.size() - 1)));
        }

        Instant lastUpdatedAt = null;
        for (T item : items) {
            Instant updatedAt = updatedAtExtractor.extract(item);
            if (updatedAt != null && (lastUpdatedAt == null || updatedAt.isAfter(lastUpdatedAt))) {
                lastUpdatedAt = updatedAt;
            }
        }

        return new BootstrapPage<>(items, hasMore, nextCursor, lastUpdatedAt);
    }

    private Boolean isActiveProductState(String productState) {
        return productState == null || !"INACTIVE".equalsIgnoreCase(productState);
    }

    private Instant productUpdatedAt(OfflineBootstrapProductItem item) {
        return item.updatedAt();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private interface IdExtractor<T> {
        Long extract(T item);
    }

    private interface UpdatedAtExtractor<T> {
        Instant extract(T item);
    }
}
