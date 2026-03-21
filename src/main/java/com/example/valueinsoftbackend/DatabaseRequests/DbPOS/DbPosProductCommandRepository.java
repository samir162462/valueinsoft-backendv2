package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public class DbPosProductCommandRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    public DbPosProductCommandRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long addProduct(Product product, String branchId, int companyId) {
        String sql = """
                INSERT INTO %s (
                    "productName", "buyingDay", "activationPeriod", "rPrice", "lPrice", "bPrice",
                    "companyName", type, "ownerName", serial, "desc", "batteryLife", "ownerPhone",
                    "ownerNI", quantity, "pState", "supplierId", "major", "imgFile"
                ) VALUES (
                    :productName, :buyingDay, :activationPeriod, :rPrice, :lPrice, :bPrice,
                    :companyName, :type, :ownerName, :serial, :description, :batteryLife, :ownerPhone,
                    :ownerNI, :quantity, :productState, :supplierId, :major, :image
                )
                """.formatted(ProductQueryBuilder.productTable(companyId, branchId));

        MapSqlParameterSource params = createProductParams(product);
        params.addValue("buyingDay", new Timestamp(System.currentTimeMillis()));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        int affectedRows = jdbcTemplate.update(sql, params, keyHolder, new String[]{"productId"});
        if (affectedRows == 0 || keyHolder.getKey() == null) {
            throw new IllegalStateException("Creating product failed");
        }

        return keyHolder.getKey().longValue();
    }

    public void updateProduct(Product product, String branchId, int companyId) {
        String sql = """
                UPDATE %s
                SET "productName" = :productName,
                    "buyingDay" = :buyingDay,
                    "activationPeriod" = :activationPeriod,
                    "rPrice" = :rPrice,
                    "lPrice" = :lPrice,
                    "bPrice" = :bPrice,
                    "companyName" = :companyName,
                    type = :type,
                    "ownerName" = :ownerName,
                    serial = :serial,
                    "desc" = :description,
                    "batteryLife" = :batteryLife,
                    "ownerPhone" = :ownerPhone,
                    "ownerNI" = :ownerNI,
                    quantity = :quantity,
                    "pState" = :productState,
                    "supplierId" = :supplierId,
                    major = :major,
                    "imgFile" = :image
                WHERE "productId" = :productId
                """.formatted(ProductQueryBuilder.productTable(companyId, branchId));

        MapSqlParameterSource params = createProductParams(product);
        params.addValue("buyingDay", product.getBuyingDay());
        params.addValue("productId", product.getProductId());

        int affectedRows = jdbcTemplate.update(sql, params);
        if (affectedRows == 0) {
            throw new IllegalStateException("Updating product failed");
        }
    }

    private MapSqlParameterSource createProductParams(Product product) {
        return new MapSqlParameterSource()
                .addValue("productName", product.getProductName())
                .addValue("activationPeriod", parseActivationPeriod(product.getActivationPeriod()))
                .addValue("rPrice", product.getrPrice())
                .addValue("lPrice", product.getlPrice())
                .addValue("bPrice", product.getbPrice())
                .addValue("companyName", product.getCompanyName())
                .addValue("type", product.getType())
                .addValue("ownerName", product.getOwnerName())
                .addValue("serial", product.getSerial())
                .addValue("description", product.getDesc())
                .addValue("batteryLife", product.getBatteryLife())
                .addValue("ownerPhone", product.getOwnerPhone())
                .addValue("ownerNI", product.getOwnerNI())
                .addValue("quantity", product.getQuantity())
                .addValue("productState", product.getpState())
                .addValue("supplierId", product.getSupplierId())
                .addValue("major", product.getMajor())
                .addValue("image", product.getImage());
    }

    private int parseActivationPeriod(String activationPeriod) {
        if (activationPeriod == null || activationPeriod.isBlank()) {
            return 0;
        }
        return Integer.parseInt(activationPeriod.trim());
    }
}
