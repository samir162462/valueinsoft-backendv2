package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Configuration.CompanyTemplateConfig;
import com.example.valueinsoftbackend.Model.Configuration.CompanyTemplateModuleDefault;
import com.example.valueinsoftbackend.Model.Configuration.CompanyTemplateWorkflowDefault;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only access to company templates and their default module and workflow behavior.
 */
@Repository
public class DbCompanyTemplates {

    private static final RowMapper<CompanyTemplateConfig> TEMPLATE_ROW_MAPPER = (rs, rowNum) -> new CompanyTemplateConfig(
            rs.getString("template_id"),
            rs.getString("display_name"),
            rs.getString("business_type"),
            rs.getString("status"),
            rs.getString("config_version"),
            rs.getString("description")
    );

    private static final RowMapper<CompanyTemplateModuleDefault> TEMPLATE_MODULE_ROW_MAPPER = (rs, rowNum) ->
            new CompanyTemplateModuleDefault(
                    rs.getString("template_id"),
                    rs.getString("module_id"),
                    rs.getBoolean("enabled"),
                    rs.getBoolean("recommended"),
                    rs.getString("mode"),
                    rs.getString("notes")
            );

    private static final RowMapper<CompanyTemplateWorkflowDefault> TEMPLATE_WORKFLOW_ROW_MAPPER = (rs, rowNum) ->
            new CompanyTemplateWorkflowDefault(
                    rs.getString("template_id"),
                    rs.getString("flag_key"),
                    rs.getString("flag_value"),
                    rs.getString("notes")
            );

    private final JdbcTemplate jdbcTemplate;

    public DbCompanyTemplates(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Loads one company template definition by its stable template id.
     */
    public CompanyTemplateConfig getCompanyTemplate(String templateId) {
        String sql = "SELECT template_id, display_name, business_type, status, config_version, description " +
                "FROM public.company_templates WHERE template_id = ?";
        List<CompanyTemplateConfig> results = jdbcTemplate.query(sql, TEMPLATE_ROW_MAPPER, normalizeId(templateId));
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Loads the default module configuration contributed by a company template.
     */
    public List<CompanyTemplateModuleDefault> getTemplateModuleDefaults(String templateId) {
        String sql = "SELECT template_id, module_id, enabled, recommended, mode, notes " +
                "FROM public.company_template_module_defaults WHERE template_id = ? ORDER BY module_id ASC";
        return jdbcTemplate.query(sql, TEMPLATE_MODULE_ROW_MAPPER, normalizeId(templateId));
    }

    /**
     * Loads the default workflow flags contributed by a company template.
     */
    public List<CompanyTemplateWorkflowDefault> getTemplateWorkflowDefaults(String templateId) {
        String sql = "SELECT template_id, flag_key, flag_value::text AS flag_value, notes " +
                "FROM public.company_template_workflow_defaults WHERE template_id = ? ORDER BY flag_key ASC";
        return jdbcTemplate.query(sql, TEMPLATE_WORKFLOW_ROW_MAPPER, normalizeId(templateId));
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }
}
