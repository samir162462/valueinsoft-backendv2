package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Configuration.BusinessPackageCategoryConfig;
import com.example.valueinsoftbackend.Model.Configuration.BusinessPackageConfig;
import com.example.valueinsoftbackend.Model.Configuration.BusinessPackageGroupConfig;
import com.example.valueinsoftbackend.Model.Configuration.BusinessPackageSubcategoryConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DbBusinessPackages {

    private static final RowMapper<BusinessPackageConfig> PACKAGE_ROW_MAPPER = (rs, rowNum) -> new BusinessPackageConfig(
            rs.getString("package_id"),
            rs.getString("display_name"),
            rs.getString("onboarding_label"),
            rs.getString("business_type"),
            rs.getString("status"),
            rs.getString("config_version"),
            rs.getString("description"),
            rs.getString("default_template_id"),
            rs.getInt("display_order"),
            rs.getBoolean("featured"),
            new ArrayList<>()
    );

    private static final RowMapper<BusinessPackageGroupConfig> GROUP_ROW_MAPPER = (rs, rowNum) -> new BusinessPackageGroupConfig(
            rs.getLong("group_id"),
            rs.getString("group_key"),
            rs.getString("display_name"),
            rs.getString("status"),
            rs.getInt("display_order"),
            new ArrayList<>()
    );

    private static final RowMapper<BusinessPackageCategoryConfig> CATEGORY_ROW_MAPPER = (rs, rowNum) -> new BusinessPackageCategoryConfig(
            rs.getLong("category_id"),
            rs.getString("category_key"),
            rs.getString("display_name"),
            rs.getString("status"),
            rs.getInt("display_order"),
            new ArrayList<>()
    );

    private static final RowMapper<BusinessPackageSubcategoryConfig> SUBCATEGORY_ROW_MAPPER = (rs, rowNum) -> new BusinessPackageSubcategoryConfig(
            rs.getLong("subcategory_id"),
            rs.getString("subcategory_key"),
            rs.getString("display_name"),
            rs.getString("status"),
            rs.getInt("display_order")
    );

    private final JdbcTemplate jdbcTemplate;

    public DbBusinessPackages(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BusinessPackageConfig> getAllBusinessPackages(boolean activeOnly) {
        String sql = "SELECT package_id, display_name, onboarding_label, business_type, status, config_version, description, " +
                "default_template_id, display_order, featured FROM public.business_packages " +
                (activeOnly ? "WHERE status = 'active' " : "") +
                "ORDER BY display_order ASC, package_id ASC";
        return jdbcTemplate.query(sql, PACKAGE_ROW_MAPPER);
    }

    public BusinessPackageConfig getBusinessPackage(String packageId) {
        String sql = "SELECT package_id, display_name, onboarding_label, business_type, status, config_version, description, " +
                "default_template_id, display_order, featured FROM public.business_packages WHERE package_id = ?";
        List<BusinessPackageConfig> items = jdbcTemplate.query(sql, PACKAGE_ROW_MAPPER, normalize(packageId));
        return items.isEmpty() ? null : items.get(0);
    }

    public BusinessPackageConfig getFeaturedBusinessPackage() {
        String sql = "SELECT package_id, display_name, onboarding_label, business_type, status, config_version, description, " +
                "default_template_id, display_order, featured FROM public.business_packages " +
                "WHERE status = 'active' ORDER BY featured DESC, display_order ASC, package_id ASC";
        List<BusinessPackageConfig> items = jdbcTemplate.query(sql, PACKAGE_ROW_MAPPER);
        return items.isEmpty() ? null : items.get(0);
    }

    public List<BusinessPackageGroupConfig> getGroups(String packageId) {
        String sql = "SELECT group_id, group_key, display_name, status, display_order FROM public.business_package_groups " +
                "WHERE package_id = ? ORDER BY display_order ASC, group_id ASC";
        return jdbcTemplate.query(sql, GROUP_ROW_MAPPER, normalize(packageId));
    }

    public List<BusinessPackageCategoryConfig> getCategories(String packageId) {
        String sql = "SELECT c.category_id, c.category_key, c.display_name, c.status, c.display_order " +
                "FROM public.business_package_categories c " +
                "JOIN public.business_package_groups g ON g.group_id = c.group_id " +
                "WHERE g.package_id = ? ORDER BY g.display_order ASC, c.display_order ASC, c.category_id ASC";
        return jdbcTemplate.query(sql, CATEGORY_ROW_MAPPER, normalize(packageId));
    }

    public List<Map<String, Object>> getCategoryGroupLinks(String packageId) {
        String sql = "SELECT c.category_id, c.group_id FROM public.business_package_categories c " +
                "JOIN public.business_package_groups g ON g.group_id = c.group_id " +
                "WHERE g.package_id = ? ORDER BY g.display_order ASC, c.display_order ASC, c.category_id ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("categoryId", rs.getLong("category_id"));
            row.put("groupId", rs.getLong("group_id"));
            return row;
        }, normalize(packageId));
    }

    public List<BusinessPackageSubcategoryConfig> getSubcategories(String packageId) {
        String sql = "SELECT s.subcategory_id, s.subcategory_key, s.display_name, s.status, s.display_order " +
                "FROM public.business_package_subcategories s " +
                "JOIN public.business_package_categories c ON c.category_id = s.category_id " +
                "JOIN public.business_package_groups g ON g.group_id = c.group_id " +
                "WHERE g.package_id = ? ORDER BY c.display_order ASC, s.display_order ASC, s.subcategory_id ASC";
        return jdbcTemplate.query(sql, SUBCATEGORY_ROW_MAPPER, normalize(packageId));
    }

    public List<Map<String, Object>> getSubcategoryCategoryLinks(String packageId) {
        String sql = "SELECT s.subcategory_id, s.category_id FROM public.business_package_subcategories s " +
                "JOIN public.business_package_categories c ON c.category_id = s.category_id " +
                "JOIN public.business_package_groups g ON g.group_id = c.group_id " +
                "WHERE g.package_id = ? ORDER BY c.display_order ASC, s.display_order ASC, s.subcategory_id ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subcategoryId", rs.getLong("subcategory_id"));
            row.put("categoryId", rs.getLong("category_id"));
            return row;
        }, normalize(packageId));
    }

    public void upsertBusinessPackageRoot(BusinessPackageConfig item) {
        jdbcTemplate.update(
                "INSERT INTO public.business_packages (package_id, display_name, onboarding_label, business_type, status, config_version, description, default_template_id, display_order, featured) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (package_id) DO UPDATE SET " +
                        "display_name = EXCLUDED.display_name, onboarding_label = EXCLUDED.onboarding_label, business_type = EXCLUDED.business_type, " +
                        "status = EXCLUDED.status, config_version = EXCLUDED.config_version, description = EXCLUDED.description, " +
                        "default_template_id = EXCLUDED.default_template_id, display_order = EXCLUDED.display_order, featured = EXCLUDED.featured, updated_at = NOW()",
                normalize(item.getPackageId()),
                normalize(item.getDisplayName()),
                normalizeNullable(item.getOnboardingLabel()),
                normalize(item.getBusinessType()),
                normalize(item.getStatus()),
                normalize(item.getConfigVersion()),
                normalize(item.getDescription()),
                normalize(item.getDefaultTemplateId()),
                item.getDisplayOrder(),
                item.isFeatured()
        );
    }

    public void deleteGroupsForPackage(String packageId) {
        jdbcTemplate.update("DELETE FROM public.business_package_groups WHERE package_id = ?", normalize(packageId));
    }

    public long insertGroup(String packageId, BusinessPackageGroupConfig group) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO public.business_package_groups (package_id, group_key, display_name, status, display_order) " +
                        "VALUES (?, ?, ?, ?, ?) RETURNING group_id",
                Long.class,
                normalize(packageId),
                normalize(group.getGroupKey()),
                normalize(group.getDisplayName()),
                normalize(group.getStatus()),
                group.getDisplayOrder()
        );
        return id == null ? 0L : id;
    }

    public long insertCategory(long groupId, BusinessPackageCategoryConfig category) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO public.business_package_categories (group_id, category_key, display_name, status, display_order) " +
                        "VALUES (?, ?, ?, ?, ?) RETURNING category_id",
                Long.class,
                groupId,
                normalize(category.getCategoryKey()),
                normalize(category.getDisplayName()),
                normalize(category.getStatus()),
                category.getDisplayOrder()
        );
        return id == null ? 0L : id;
    }

    public void insertSubcategory(long categoryId, BusinessPackageSubcategoryConfig subcategory) {
        jdbcTemplate.update(
                "INSERT INTO public.business_package_subcategories (category_id, subcategory_key, display_name, status, display_order) VALUES (?, ?, ?, ?, ?)",
                categoryId,
                normalize(subcategory.getSubcategoryKey()),
                normalize(subcategory.getDisplayName()),
                normalize(subcategory.getStatus()),
                subcategory.getDisplayOrder()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
