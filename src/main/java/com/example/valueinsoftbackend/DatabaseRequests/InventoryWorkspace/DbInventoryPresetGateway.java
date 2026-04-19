package com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace;

import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryPresetResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetCreateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetUpdateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Repository
@Slf4j
public class DbInventoryPresetGateway implements InventoryPresetGateway {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DbInventoryPresetGateway(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<InventoryPresetResponse> PRESET_ROW_MAPPER = this::mapRawToPreset;

    private InventoryPresetResponse mapRawToPreset(ResultSet rs, int rowNum) throws SQLException {
        InventoryPresetResponse response = new InventoryPresetResponse();
        response.setPresetId(rs.getString("preset_id"));
        response.setName(rs.getString("preset_name"));
        response.setScope(rs.getString("scope"));
        response.setMode(rs.getString("mode"));
        response.setBranchId(rs.getObject("branch_id", Integer.class));
        response.setRoleTarget(rs.getString("role_target"));
        response.setCreatedBy(rs.getString("created_by"));
        response.setCanManage(true);

        try {
            String queryStateJson = rs.getString("query_state");
            if (queryStateJson != null) {
                LinkedHashMap<String, Object> queryState = objectMapper.readValue(
                        queryStateJson,
                        new TypeReference<LinkedHashMap<String, Object>>() {}
                );
                response.setQueryState(queryState);
            }
        } catch (Exception e) {
            log.error("Failed to parse query_state for preset {}", response.getPresetId(), e);
            response.setQueryState(new LinkedHashMap<>());
        }
        
        return response;
    }

    @Override
    public ArrayList<InventoryPresetResponse> getPresets(String actorName, Integer companyId, Integer branchId) {
        String sql = """
                SELECT * FROM public.inventory_presets
                WHERE company_id = :companyId
                  AND (scope = 'global' OR (scope = 'company') OR (scope = 'branch' AND branch_id = :branchId))
                ORDER BY created_at DESC
                """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId);
        
        List<InventoryPresetResponse> results = jdbcTemplate.query(sql, params, PRESET_ROW_MAPPER);
        return new ArrayList<>(results);
    }

    @Override
    public InventoryPresetResponse createPreset(String actorName, Integer companyId, InventoryPresetCreateRequest request) {
        String sql = """
                INSERT INTO public.inventory_presets (
                    company_id, branch_id, preset_name, scope, mode, role_target, query_state, created_by
                ) VALUES (
                    :companyId, :branchId, :name, :scope, :mode, :roleTarget, :queryState::jsonb, :createdBy
                ) RETURNING preset_id
                """;
        
        String queryStateJson = "{}";
        try {
            queryStateJson = objectMapper.writeValueAsString(request.getQueryState());
        } catch (Exception e) {
            log.error("Failed to serialize query_state", e);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", request.getBranchId())
                .addValue("name", request.getName())
                .addValue("scope", request.getScope())
                .addValue("mode", request.getMode())
                .addValue("roleTarget", request.getRoleTarget())
                .addValue("queryState", queryStateJson)
                .addValue("createdBy", actorName);

        UUID presetId = jdbcTemplate.queryForObject(sql, params, UUID.class);
        return findById(presetId);
    }

    @Override
    public InventoryPresetResponse updatePreset(String actorName, String presetId, InventoryPresetUpdateRequest request) {
        String queryStateJson = "{}";
        try {
            queryStateJson = objectMapper.writeValueAsString(request.getQueryState());
        } catch (Exception e) {
            log.error("Failed to serialize query_state", e);
        }

        String sql = """
                UPDATE public.inventory_presets
                SET preset_name = :name,
                    query_state = :queryState::jsonb,
                    updated_at = CURRENT_TIMESTAMP
                WHERE preset_id = :presetId::uuid
                """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("presetId", presetId)
                .addValue("name", request.getName())
                .addValue("queryState", queryStateJson);
        
        jdbcTemplate.update(sql, params);
        return findById(UUID.fromString(presetId));
    }

    @Override
    public void deletePreset(String actorName, String presetId) {
        String sql = "DELETE FROM public.inventory_presets WHERE preset_id = :presetId::uuid";
        jdbcTemplate.update(sql, new MapSqlParameterSource("presetId", presetId));
    }

    private InventoryPresetResponse findById(UUID presetId) {
        String sql = "SELECT * FROM public.inventory_presets WHERE preset_id = :presetId";
        List<InventoryPresetResponse> results = jdbcTemplate.query(sql, new MapSqlParameterSource("presetId", presetId), PRESET_ROW_MAPPER);
        return results.isEmpty() ? null : results.get(0);
    }
}
