package com.example.valueinsoftbackend.DatabaseRequests.Public;

import com.example.valueinsoftbackend.Model.Public.PublicProductDTO;
import com.example.valueinsoftbackend.Model.Public.PublicTenantDTO;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class DbPublicCatalog {

    private final JdbcTemplate jdbcTemplate;

    public DbPublicCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Integer resolveTenantIdByCode(String tenantCode) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT tenant_id FROM public.public_tenants WHERE tenant_code = ? AND is_active = true",
                    Integer.class,
                    tenantCode);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public PublicTenantDTO getPublicTenantByCode(String tenantCode) {
        String sql = "SELECT tenant_code, display_name, logo_url, primary_color, whatsapp_number, facebook_url, instagram_url, contact_email, contact_phone, store_address, cover_image_url, description, working_hours "
                +
                "FROM public.public_tenants WHERE tenant_code = ? AND is_active = true";
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new PublicTenantDTO(
                    rs.getString("tenant_code"),
                    rs.getString("display_name"),
                    rs.getString("logo_url"),
                    rs.getString("primary_color"),
                    rs.getString("whatsapp_number"),
                    rs.getString("facebook_url"),
                    rs.getString("instagram_url"),
                    rs.getString("contact_email"),
                    rs.getString("contact_phone"),
                    rs.getString("store_address"),
                    rs.getString("cover_image_url"),
                    rs.getString("description"),
                    rs.getString("working_hours")), tenantCode);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public PublicTenantDTO getPublicTenantById(int tenantId) {
        String sql = "SELECT tenant_code, display_name, logo_url, primary_color, whatsapp_number, facebook_url, instagram_url, contact_email, contact_phone, store_address, cover_image_url, description, working_hours "
                +
                "FROM public.public_tenants WHERE tenant_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new PublicTenantDTO(
                    rs.getString("tenant_code"),
                    rs.getString("display_name"),
                    rs.getString("logo_url"),
                    rs.getString("primary_color"),
                    rs.getString("whatsapp_number"),
                    rs.getString("facebook_url"),
                    rs.getString("instagram_url"),
                    rs.getString("contact_email"),
                    rs.getString("contact_phone"),
                    rs.getString("store_address"),
                    rs.getString("cover_image_url"),
                    rs.getString("description"),
                    rs.getString("working_hours")), tenantId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void upsertPublicTenant(int tenantId, PublicTenantDTO dto) {
        String sql = "INSERT INTO public.public_tenants (tenant_id, tenant_code, display_name, logo_url, primary_color, "
                +
                "whatsapp_number, facebook_url, instagram_url, contact_email, contact_phone, store_address, cover_image_url, description, working_hours, is_active) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true) " +
                "ON CONFLICT (tenant_id) DO UPDATE SET " +
                "tenant_code = EXCLUDED.tenant_code, " +
                "display_name = EXCLUDED.display_name, " +
                "logo_url = EXCLUDED.logo_url, " +
                "primary_color = EXCLUDED.primary_color, " +
                "whatsapp_number = EXCLUDED.whatsapp_number, " +
                "facebook_url = EXCLUDED.facebook_url, " +
                "instagram_url = EXCLUDED.instagram_url, " +
                "contact_email = EXCLUDED.contact_email, " +
                "contact_phone = EXCLUDED.contact_phone, " +
                "store_address = EXCLUDED.store_address, " +
                "cover_image_url = EXCLUDED.cover_image_url, " +
                "description = EXCLUDED.description, " +
                "working_hours = EXCLUDED.working_hours, " +
                "updated_at = CURRENT_TIMESTAMP";
        jdbcTemplate.update(sql,
                tenantId,
                dto.getTenantCode(),
                dto.getDisplayName(),
                dto.getLogoUrl(),
                dto.getPrimaryColor(),
                dto.getWhatsappNumber(),
                dto.getFacebookUrl(),
                dto.getInstagramUrl(),
                dto.getContactEmail(),
                dto.getContactPhone(),
                dto.getStoreAddress(),
                dto.getCoverImageUrl(),
                dto.getDescription(),
                dto.getWorkingHours());
    }

    public List<PublicProductDTO> getPublicProducts(int tenantId, String category, String search) {
        String schema = TenantSqlIdentifiers.companySchema(tenantId);
        StringBuilder sql = new StringBuilder()
                .append("SELECT prod.product_id, prod.product_name, ")
                .append("COALESCE(prod.online_description, prod.description) as description, ")
                .append("COALESCE(prod.online_image_url, prod.img_file) as image_url, ")
                .append("prod.major as category, prod.retail_price, prod.lowest_price, prod.online_offer_price, ")
                .append("SUM(stock.quantity) as total_quantity ")
                .append("FROM ").append(schema).append(".inventory_product prod ")
                .append("LEFT JOIN ").append(schema)
                .append(".inventory_branch_stock_balance stock ON prod.product_id = stock.product_id ")
                .append("WHERE prod.show_online = true AND prod.online_active = true ");

        List<Object> params = new ArrayList<>();

        if (category != null && !category.isEmpty()) {
            sql.append("AND prod.major = ? ");
            params.add(category);
        }

        if (search != null && !search.isEmpty()) {
            sql.append("AND prod.product_name ILIKE ? ");
            params.add("%" + search + "%");
        }

        sql.append("GROUP BY prod.product_id, prod.product_name, prod.online_description, prod.description, ")
                .append("prod.online_image_url, prod.img_file, prod.major, prod.retail_price, prod.lowest_price, prod.online_offer_price ")
                .append("ORDER BY prod.online_sort_order ASC, prod.product_name ASC");

        return jdbcTemplate.query(sql.toString(), getProductRowMapper(), params.toArray());
    }

    public PublicProductDTO getPublicProductById(int tenantId, int productId) {
        String schema = TenantSqlIdentifiers.companySchema(tenantId);
        String sql = "SELECT prod.product_id, prod.product_name, " +
                "COALESCE(prod.online_description, prod.description) as description, " +
                "COALESCE(prod.online_image_url, prod.img_file) as image_url, " +
                "prod.major as category, prod.retail_price, prod.lowest_price, prod.online_offer_price, " +
                "SUM(stock.quantity) as total_quantity " +
                "FROM " + schema + ".inventory_product prod " +
                "LEFT JOIN " + schema + ".inventory_branch_stock_balance stock ON prod.product_id = stock.product_id " +
                "WHERE prod.product_id = ? AND prod.show_online = true AND prod.online_active = true " +
                "GROUP BY prod.product_id, prod.product_name, prod.online_description, prod.description, " +
                "prod.online_image_url, prod.img_file, prod.major, prod.retail_price, prod.lowest_price, prod.online_offer_price";

        try {
            return jdbcTemplate.queryForObject(sql, getProductRowMapper(), productId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<String> getPublicCategories(int tenantId) {
        String schema = TenantSqlIdentifiers.companySchema(tenantId);
        String sql = "SELECT DISTINCT major FROM " + schema
                + ".inventory_product WHERE show_online = true AND online_active = true ORDER BY major";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    private RowMapper<PublicProductDTO> getProductRowMapper() {
        return (rs, rowNum) -> {
            int retailPrice = rs.getInt("retail_price");
            int lowestPrice = rs.getInt("lowest_price");
            // If lowest_price is lower than retail_price, use it as the main price.
            // If they are the same, retailPrice is the main price.
            int finalSellingPrice = (lowestPrice > 0 && lowestPrice < retailPrice) ? lowestPrice : retailPrice;

            return new PublicProductDTO(
                    rs.getLong("product_id"),
                    rs.getString("product_name"),
                    rs.getString("description"),
                    rs.getString("image_url"),
                    rs.getString("category"),
                    finalSellingPrice,
                    retailPrice,
                    rs.getBigDecimal("online_offer_price"),
                    rs.getInt("total_quantity"),
                    rs.getInt("total_quantity") > 0);
        };
    }
}
