package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.example.valueinsoftbackend.util.PageHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProductQueryBuilder {

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile("'([^']+)'\\s+And\\s+'([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter FRONTEND_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE MMM dd yyyy", Locale.ENGLISH);
    private static final String PRODUCT_SELECT_COLUMNS =
            "prod.product_id AS \"productId\", " +
                    "prod.product_name AS \"productName\", " +
                    "prod.buying_day AS \"buyingDay\", " +
                    "CAST(prod.activation_period AS VARCHAR) AS \"activationPeriod\", " +
                    "prod.retail_price AS \"rPrice\", " +
                    "prod.lowest_price AS \"lPrice\", " +
                    "prod.buying_price AS \"bPrice\", " +
                    "prod.company_name AS \"companyName\", " +
                    "prod.product_type AS type, " +
                    "prod.owner_name AS \"ownerName\", " +
                    "prod.serial AS serial, " +
                    "prod.description AS \"desc\", " +
                    "prod.battery_life AS \"batteryLife\", " +
                    "prod.owner_phone AS \"ownerPhone\", " +
                    "prod.owner_ni AS \"ownerNI\", " +
                    "COALESCE(stock.quantity, 0) AS quantity, " +
                    "prod.product_state AS \"pState\", " +
                    "prod.supplier_id AS \"supplierId\", " +
                    "prod.major AS major, " +
                    "prod.img_file AS \"imgFile\", " +
                    "prod.business_line_key AS \"businessLineKey\", " +
                    "prod.template_key AS \"templateKey\", " +
                    "prod.base_uom_code AS \"baseUomCode\", " +
                    "prod.pricing_policy_code AS \"pricingPolicyCode\"";
    private ProductQueryBuilder() {
    }

    static ProductQuerySpec buildTextSearchQuery(String[] text, String branchId, int companyId, ProductFilter productFilter,
                                                 PageHandler pageHandler, boolean useDefaultInStockFilter) {
        MapSqlParameterSource params = baseParams(branchId, companyId);
        List<String> conditions = new ArrayList<>();

        addFilterConditions(conditions, params, productFilter, useDefaultInStockFilter);

        List<String> tokens = normalizeTokens(text);
        for (int i = 0; i < tokens.size(); i++) {
            conditions.add("prod.product_name ILIKE :token" + i);
            params.addValue("token" + i, "%" + tokens.get(i) + "%");
        }

        String whereClause = buildWhereClause(conditions);
        String fromClause = productFromClause(companyId);
        String countSql = "SELECT count(*)" + fromClause + whereClause;
        String dataSql = "SELECT " + PRODUCT_SELECT_COLUMNS + fromClause + whereClause + appendPaging(pageHandler);
        return new ProductQuerySpec(dataSql, countSql, params);
    }

    static ProductQuerySpec buildCompanySearchQuery(String comName, String branchId, int companyId, ProductFilter productFilter,
                                                    PageHandler pageHandler) {
        MapSqlParameterSource params = baseParams(branchId, companyId);
        List<String> conditions = new ArrayList<>();

        addFilterConditions(conditions, params, productFilter, false);

        if (comName != null && comName.startsWith("All ")) {
            conditions.add("prod.product_type = :type");
            params.addValue("type", comName.substring(4).trim());
        } else {
            conditions.add("prod.company_name = :companyName");
            params.addValue("companyName", comName == null ? "" : comName.trim());
        }

        String whereClause = buildWhereClause(conditions);
        String fromClause = productFromClause(companyId);
        String countSql = "SELECT count(*)" + fromClause + whereClause;
        String dataSql = "SELECT " + PRODUCT_SELECT_COLUMNS + fromClause + whereClause + appendPaging(pageHandler);
        return new ProductQuerySpec(dataSql, countSql, params);
    }

    static ProductQuerySpec buildAllRangeQuery(String branchId, int companyId, ProductFilter productFilter) {
        MapSqlParameterSource params = baseParams(branchId, companyId);
        List<String> conditions = new ArrayList<>();

        addFilterConditions(conditions, params, productFilter, false);
        conditions.add("prod.product_id > 0");

        String whereClause = buildWhereClause(conditions);
        String sql = "SELECT " + PRODUCT_SELECT_COLUMNS + productFromClause(companyId) + whereClause;
        return new ProductQuerySpec(sql, null, params);
    }

    static String productSelectColumns() {
        return PRODUCT_SELECT_COLUMNS;
    }

    static String productFromClause(int companyId) {
        return " FROM " + TenantSqlIdentifiers.inventoryProductTable(companyId) + " prod " +
                "LEFT JOIN " + TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId) + " stock " +
                "ON stock.product_id = prod.product_id " +
                "AND stock.branch_id = :branchId ";
    }

    static MapSqlParameterSource baseParams(String branchId, int companyId) {
        int numericBranchId = Integer.parseInt(branchId);
        return new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", numericBranchId);
    }

    static MapSqlParameterSource baseParams(int branchId, int companyId) {
        return new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId);
    }

    static List<String> normalizeTokens(String[] text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }

        for (String value : text) {
            if (value == null) {
                continue;
            }

            String token = value.trim();
            if (token.isEmpty()) {
                continue;
            }

            token = token.replaceAll("\\p{C}", "");
            token = Normalizer.normalize(token, Normalizer.Form.NFKC);
            token = token.replaceAll("[\\p{Punct}]+", " ").replaceAll("\\s+", " ").trim();

            if (token.length() < 2) {
                continue;
            }

            tokens.add(token);
        }

        return tokens;
    }

    private static void addFilterConditions(List<String> conditions, MapSqlParameterSource params,
                                            ProductFilter productFilter, boolean useDefaultInStockFilter) {
        if (productFilter == null) {
            if (useDefaultInStockFilter) {
                conditions.add("COALESCE(stock.quantity, 0) <> 0");
            }
            return;
        }

        if (productFilter.isOutOfStock() && productFilter.isToSell()) {
            conditions.add("COALESCE(stock.quantity, 0) >= 0");
        } else if (!productFilter.isOutOfStock() && productFilter.isToSell()) {
            conditions.add("COALESCE(stock.quantity, 0) > 0");
        } else if (productFilter.isOutOfStock()) {
            conditions.add("COALESCE(stock.quantity, 0) = 0");
        }

        if (productFilter.getRangeMin() > 0 || productFilter.getRangeMax() < 100000) {
            conditions.add("prod.retail_price BETWEEN :rangeMin AND :rangeMax");
            params.addValue("rangeMin", productFilter.getRangeMin());
            params.addValue("rangeMax", productFilter.getRangeMax());
        }

        if (productFilter.isUsed()) {
            conditions.add("prod.product_state = :productState");
            params.addValue("productState", "Used");
        }

        if (productFilter.getMajor() != null && !productFilter.getMajor().isBlank()) {
            conditions.add("prod.major = :major");
            params.addValue("major", productFilter.getMajor().trim());
        }

        addDateRangeCondition(productFilter.getDates(), conditions, params);
    }

    private static void addDateRangeCondition(String dates, List<String> conditions, MapSqlParameterSource params) {
        if (dates == null || dates.isBlank()) {
            return;
        }

        Matcher matcher = DATE_RANGE_PATTERN.matcher(dates);
        if (!matcher.find()) {
            return;
        }

        LocalDate firstDate = LocalDate.parse(matcher.group(1), FRONTEND_DATE_FORMAT);
        LocalDate secondDate = LocalDate.parse(matcher.group(2), FRONTEND_DATE_FORMAT);
        LocalDate startDate = firstDate.isBefore(secondDate) ? firstDate : secondDate;
        LocalDate endDate = firstDate.isAfter(secondDate) ? firstDate : secondDate;

        conditions.add("prod.buying_day::date BETWEEN :startDate AND :endDate");
        params.addValue("startDate", startDate);
        params.addValue("endDate", endDate);
    }

    private static String buildWhereClause(List<String> conditions) {
        if (conditions.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    private static String appendPaging(PageHandler pageHandler) {
        if (pageHandler == null) {
            return "";
        }
        return pageHandler.handlePageSqlQuery();
    }
}

record ProductQuerySpec(String dataSql, String countSql, MapSqlParameterSource params) {
}
