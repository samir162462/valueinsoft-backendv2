package com.example.valueinsoftbackend.ai.sql;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiSqlExecutor {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AiProperties aiProperties;

    public AiSqlExecutor(NamedParameterJdbcTemplate jdbcTemplate, AiProperties aiProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiProperties = aiProperties;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(String sql, long companyId, Long branchId) {
        long startedAt = System.nanoTime();
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("companyId", Math.toIntExact(companyId));
        params.addValue("companyid", Math.toIntExact(companyId));
        if (branchId != null) {
            params.addValue("branchId", Math.toIntExact(branchId));
            params.addValue("branchid", Math.toIntExact(branchId));
        }
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);
        String sqlToUse = NamedParameterUtils.substituteNamedParameters(parsedSql, params);
        Object[] values = NamedParameterUtils.buildValueArray(parsedSql, params, null);
        ArgumentPreparedStatementSetter setter = new ArgumentPreparedStatementSetter(values);
        int timeoutSeconds = Math.max(1, aiProperties.getSqlQueryTimeoutSeconds());
        int maxRows = Math.max(1, aiProperties.getSqlMaxRows());
        log.info(
                "AI SQL SELECT executing companyId={} branchId={} timeoutSeconds={} maxRows={}",
                companyId,
                branchId,
                timeoutSeconds,
                maxRows
        );

        List<Map<String, Object>> rows = jdbcTemplate.getJdbcTemplate().query(connection -> {
            PreparedStatement statement = connection.prepareStatement(sqlToUse);
            statement.setQueryTimeout(timeoutSeconds);
            statement.setMaxRows(maxRows);
            setter.setValues(statement);
            return statement;
        }, new ColumnMapRowMapper());
        log.debug("AI SQL SELECT result companyId={} branchId={} rowCount={} durationMs={}",
                companyId,
                branchId,
                rows.size(),
                Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L));
        return rows;
    }
}
