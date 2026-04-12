package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

/*
 * Copyright (c) Samir Filifl
 */

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.Model.ResponseModel.ResponsePagination;
import com.example.valueinsoftbackend.Model.Util.ProductUtilNames;
import com.example.valueinsoftbackend.util.PageHandler;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class DbPosProduct {

    private static final RowMapper<Product> PRODUCT_ROW_MAPPER = (rs, rowNum) -> new Product(
            rs.getInt("productId"),
            rs.getString("productName"),
            rs.getTimestamp("buyingDay"),
            rs.getString("activationPeriod"),
            rs.getInt("rPrice"),
            rs.getInt("lPrice"),
            rs.getInt("bPrice"),
            rs.getString("companyName"),
            rs.getString("type"),
            rs.getString("ownerName"),
            rs.getString("serial"),
            rs.getString("desc"),
            rs.getInt("batteryLife"),
            rs.getString("ownerPhone"),
            rs.getString("ownerNI"),
            rs.getInt("quantity"),
            rs.getString("pState"),
            rs.getInt("supplierId"),
            rs.getString("major"),
            rs.getString("imgFile"),
            rs.getString("businessLineKey"),
            rs.getString("templateKey"),
            rs.getString("baseUomCode"),
            rs.getString("pricingPolicyCode"),
            null
    );

    private static final RowMapper<ProductUtilNames> PRODUCT_NAME_ROW_MAPPER = (rs, rowNum) ->
            new ProductUtilNames(
                    rs.getString("productName"),
                    rs.getString("companyName"),
                    rs.getString("type"),
                    rs.getString("major")
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    public DbPosProduct(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ResponsePagination<Product> getProductBySearchText(String[] text, String branchId, int companyId,
                                                              ProductFilter productFilter, PageHandler pageHandler,
                                                              boolean useDefaultInStockFilter) {
        log.debug("Searching products by text for company {} branch {} tokens={}", companyId, branchId, java.util.Arrays.toString(text));
        ProductQuerySpec querySpec = ProductQueryBuilder.buildTextSearchQuery(
                text, branchId, companyId, productFilter, pageHandler, useDefaultInStockFilter);
        return executePagedQuery(querySpec);
    }

    public List<Product> getProductsAllRange(String branchId, int companyId, ProductFilter productFilter) {
        log.debug("Fetching product range for company {} branch {} filter={}", companyId, branchId, productFilter);
        ProductQuerySpec querySpec = ProductQueryBuilder.buildAllRangeQuery(branchId, companyId, productFilter);
        return jdbcTemplate.query(querySpec.dataSql(), querySpec.params(), PRODUCT_ROW_MAPPER);
    }

    public Product getProductById(int productId, int branchId, int companyId) {
        log.debug("Fetching product {} for company {} branch {}", productId, companyId, branchId);
        String sql = "SELECT " + ProductQueryBuilder.productSelectColumns() +
                ProductQueryBuilder.productFromClause(companyId) +
                " WHERE prod.product_id = :productId";

        MapSqlParameterSource params = ProductQueryBuilder.baseParams(branchId, companyId)
                .addValue("productId", productId);

        try {
            Product product = jdbcTemplate.queryForObject(sql, params, PRODUCT_ROW_MAPPER);
            if (product != null) {
                product.setAttributes(loadProductAttributes(companyId, product.getProductId()));
            }
            return product;
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public List<ProductUtilNames> getProductNames(String text, int branchId, int companyId) {
        log.debug("Fetching product names for company {} branch {} text={}", companyId, branchId, text);

        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String sql = """
                SELECT DISTINCT ON (prod.product_name)
                    prod.product_name AS "productName",
                    prod.company_name AS "companyName",
                    prod.product_type AS type,
                    prod.major AS major
                FROM %s prod
                WHERE prod.product_name ILIKE :searchText
                ORDER BY prod.product_name
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("searchText", "%" + text.trim() + "%"),
                PRODUCT_NAME_ROW_MAPPER
        );
    }

    public ResponsePagination<Product> getProductBySearchCompanyName(String comName, String branchId, int companyId,
                                                                     ProductFilter productFilter, PageHandler pageHandler) {
        log.debug("Searching products by company name for company {} branch {} companyName={}", companyId, branchId, comName);
        ProductQuerySpec querySpec = ProductQueryBuilder.buildCompanySearchQuery(
                comName, branchId, companyId, productFilter, pageHandler);
        return executePagedQuery(querySpec);
    }

    public List<Product> getProductBySearchBarcode(String barcode, String branchId, int companyId) {
        log.debug("Searching products by barcode for company {} branch {} barcode={}", companyId, branchId, barcode);

        String sql = "SELECT " + ProductQueryBuilder.productSelectColumns() +
                ProductQueryBuilder.productFromClause(companyId) +
                " WHERE prod.serial = :serial";
        MapSqlParameterSource params = ProductQueryBuilder.baseParams(branchId, companyId)
                .addValue("serial", barcode == null ? "" : barcode.trim());
        return jdbcTemplate.query(sql, params, PRODUCT_ROW_MAPPER);
    }

    private ResponsePagination<Product> executePagedQuery(ProductQuerySpec querySpec) {
        List<Product> products = jdbcTemplate.query(querySpec.dataSql(), querySpec.params(), PRODUCT_ROW_MAPPER);
        Integer count = jdbcTemplate.queryForObject(querySpec.countSql(), querySpec.params(), Integer.class);
        return new ResponsePagination<>(new ArrayList<>(products), count == null ? 0 : count);
    }

    private String loadProductAttributes(int companyId, int productId) {
        String sql = """
                SELECT COALESCE(
                    jsonb_object_agg(
                        attr.attribute_key,
                        CASE
                            WHEN pav.value_jsonb IS NOT NULL THEN pav.value_jsonb
                            WHEN pav.value_boolean IS NOT NULL THEN to_jsonb(pav.value_boolean)
                            WHEN pav.value_number IS NOT NULL THEN to_jsonb(pav.value_number)
                            WHEN pav.value_date IS NOT NULL THEN to_jsonb(to_char(pav.value_date, 'YYYY-MM-DD'))
                            WHEN pav.value_text IS NOT NULL THEN to_jsonb(pav.value_text)
                            ELSE 'null'::jsonb
                        END
                    )::text,
                    '{}'
                ) AS attributes_json
                FROM %s pav
                JOIN %s attr
                  ON attr.attribute_id = pav.attribute_id
                WHERE pav.product_id = :productId
                """.formatted(
                TenantSqlIdentifiers.inventoryProductAttributeValueTable(companyId),
                TenantSqlIdentifiers.inventoryAttributeDefinitionTable(companyId)
        );

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("productId", productId),
                rs -> rs.next() ? rs.getString("attributes_json") : "{}"
        );
    }
}
