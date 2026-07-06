package com.example.valueinsoftbackend.ai.memory;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AiUserMemoryRepository {

    private static final RowMapper<AiUserMemoryRecord> ROW_MAPPER = (rs, rowNum) -> new AiUserMemoryRecord(
            rs.getObject("id", UUID.class),
            rs.getLong("company_id"),
            rs.getLong("user_id"),
            rs.getString("memory_key"),
            rs.getString("memory_value"),
            rs.getString("source"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiUserMemoryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(long companyId, long userId, String memoryKey, String memoryValue, String source) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO public.ai_user_memory
                    (id, company_id, user_id, memory_key, memory_value, source, created_at, updated_at)
                VALUES
                    (:id, :companyId, :userId, :memoryKey, :memoryValue, :source, :createdAt, :updatedAt)
                ON CONFLICT (company_id, user_id, memory_key)
                DO UPDATE SET memory_value = EXCLUDED.memory_value,
                              source = EXCLUDED.source,
                              updated_at = EXCLUDED.updated_at
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("companyId", companyId)
                .addValue("userId", userId)
                .addValue("memoryKey", memoryKey)
                .addValue("memoryValue", memoryValue)
                .addValue("source", source)
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(now)));
    }

    public List<AiUserMemoryRecord> findByUser(long companyId, long userId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, company_id, user_id, memory_key, memory_value, source, created_at, updated_at
                FROM public.ai_user_memory
                WHERE company_id = :companyId AND user_id = :userId
                ORDER BY updated_at DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("userId", userId)
                .addValue("limit", Math.max(1, Math.min(limit, 50))), ROW_MAPPER);
    }

    public void delete(long companyId, long userId, String memoryKey) {
        jdbcTemplate.update("""
                DELETE FROM public.ai_user_memory
                WHERE company_id = :companyId AND user_id = :userId AND memory_key = :memoryKey
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("userId", userId)
                .addValue("memoryKey", memoryKey));
    }
}
