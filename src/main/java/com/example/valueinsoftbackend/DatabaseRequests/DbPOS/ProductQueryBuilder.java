package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.util.PageHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.Normalizer;

final class ProductQueryBuilder {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile("'([^']+)'\\s+And\\s+'([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter FRONTEND_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE MMM dd yyyy", Locale.ENGLISH);

    private ProductQueryBuilder() {
    }

    static ProductQuerySpec buildTextSearchQuery(String[] text, String branchId, int companyId, ProductFilter productFilter,
                                                 PageHandler pageHandler, boolean useDefaultInStockFilter) {
        String tableName = productTable(companyId, branchId);
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();

        addFilterConditions(conditions, params, productFilter, useDefaultInStockFilter);

        List<String> tokens = normalizeTokens(text);
        for (int i = 0; i < tokens.size(); i++) {
            conditions.add("\"productName\" ILIKE :token" + i);
            params.addValue("token" + i, "%" + tokens.get(i) + "%");
        }

        String whereClause = buildWhereClause(conditions);
        String countSql = "SELECT count(*) FROM " + tableName + whereClause;
        String dataSql = "SELECT * FROM " + tableName + whereClause + appendPaging(pageHandler);
        return new ProductQuerySpec(dataSql, countSql, params);
    }

    static ProductQuerySpec buildCompanySearchQuery(String comName, String branchId, int companyId, ProductFilter productFilter,
                                                    PageHandler pageHandler) {
        String tableName = productTable(companyId, branchId);
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();

        addFilterConditions(conditions, params, productFilter, false);

        if (comName != null && comName.startsWith("All ")) {
            conditions.add("\"type\" = :type");
            params.addValue("type", comName.substring(4).trim());
        } else {
            conditions.add("\"companyName\" = :companyName");
            params.addValue("companyName", comName == null ? "" : comName.trim());
        }

        String whereClause = buildWhereClause(conditions);
        String countSql = "SELECT count(*) FROM " + tableName + whereClause;
        String dataSql = "SELECT * FROM " + tableName + whereClause + appendPaging(pageHandler);
        return new ProductQuerySpec(dataSql, countSql, params);
    }

    static ProductQuerySpec buildAllRangeQuery(String branchId, int companyId, ProductFilter productFilter) {
        String tableName = productTable(companyId, branchId);
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();

        addFilterConditions(conditions, params, productFilter, false);
        conditions.add("\"productId\" > 0");

        String whereClause = buildWhereClause(conditions);
        String sql = "SELECT * FROM " + tableName + whereClause;
        return new ProductQuerySpec(sql, null, params);
    }

    static String productTable(int companyId, String branchId) {
        validateCompanyId(companyId);
        validateIdentifier(branchId, "branchId");
        return "C_" + companyId + ".\"PosProduct_" + branchId + "\"";
    }

    static String productTable(int companyId, int branchId) {
        return productTable(companyId, String.valueOf(branchId));
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

            // Remove control characters and invisible/formatting chars (zero-width, etc.)
            token = token.replaceAll("\\p{C}", "");

            // Normalize unicode to a consistent form to avoid mismatch (NFKC)
            token = Normalizer.normalize(token, Normalizer.Form.NFKC);

            // Replace punctuation with a single space, then collapse whitespace
            token = token.replaceAll("[\\p{Punct}]+", " ").replaceAll("\\s+", " ").trim();

            // Skip tokens that are too short to be useful (single characters)
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
                conditions.add("\"quantity\" <> 0");
            }
            return;
        }

        if (productFilter.isOutOfStock() && productFilter.isToSell()) {
            conditions.add("\"quantity\" >= 0");
        } else if (!productFilter.isOutOfStock() && productFilter.isToSell()) {
            conditions.add("\"quantity\" > 0");
        } else if (productFilter.isOutOfStock()) {
            conditions.add("\"quantity\" = 0");
        }

        if (productFilter.getRangeMin() > 0 || productFilter.getRangeMax() < 100000) {
            conditions.add("\"rPrice\" BETWEEN :rangeMin AND :rangeMax");
            params.addValue("rangeMin", productFilter.getRangeMin());
            params.addValue("rangeMax", productFilter.getRangeMax());
        }

        if (productFilter.isUsed()) {
            conditions.add("\"pState\" = :productState");
            params.addValue("productState", "Used");
        }

        if (productFilter.getMajor() != null && !productFilter.getMajor().isBlank()) {
            conditions.add("\"major\" = :major");
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

        conditions.add("\"buyingDay\"::date BETWEEN :startDate AND :endDate");
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

    private static void validateCompanyId(int companyId) {
        if (companyId <= 0) {
            throw new IllegalArgumentException("companyId must be positive");
        }
    }

    private static void validateIdentifier(String value, String fieldName) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported characters");
        }
    }
}

record ProductQuerySpec(String dataSql, String countSql, MapSqlParameterSource params) {
}
