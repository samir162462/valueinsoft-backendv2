package com.example.valueinsoftbackend.ai.memory;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AiConversationRepository {

    private static final RowMapper<AiConversationRecord> ROW_MAPPER = (rs, rowNum) -> new AiConversationRecord(
            rs.getObject("id", UUID.class),
            rs.getLong("company_id"),
            nullableLong(rs.getObject("branch_id")),
            rs.getLong("user_id"),
            rs.getString("mode"),
            rs.getString("title"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getBoolean("deleted")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiConversationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AiConversationRecord create(UUID id, long companyId, Long branchId, long userId, String mode, String title) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO public.ai_conversation
                    (id, company_id, branch_id, user_id, mode, title, created_at, updated_at, deleted)
                VALUES
                    (:id, :companyId, :branchId, :userId, :mode, :title, :createdAt, :updatedAt, false)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("userId", userId)
                .addValue("mode", mode)
                .addValue("title", title)
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(now)));
        return new AiConversationRecord(id, companyId, branchId, userId, mode, title, now, now, false);
    }

    public Optional<AiConversationRecord> findActiveById(UUID id) {
        List<AiConversationRecord> rows = jdbcTemplate.query("""
                SELECT id, company_id, branch_id, user_id, mode, title, created_at, updated_at, deleted
                FROM public.ai_conversation
                WHERE id = :id AND deleted = false
                """, new MapSqlParameterSource("id", id), ROW_MAPPER);
        return rows.stream().findFirst();
    }

    public List<AiConversationRecord> findActiveByUser(long companyId, long userId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, company_id, branch_id, user_id, mode, title, created_at, updated_at, deleted
                FROM public.ai_conversation
                WHERE company_id = :companyId AND user_id = :userId AND deleted = false
                ORDER BY updated_at DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("userId", userId)
                .addValue("limit", Math.max(1, Math.min(limit, 100))), ROW_MAPPER);
    }

    public void touch(UUID id) {
        jdbcTemplate.update("""
                UPDATE public.ai_conversation
                SET updated_at = :updatedAt
                WHERE id = :id AND deleted = false
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("updatedAt", Timestamp.from(Instant.now())));
    }

    public boolean softDelete(UUID id, long companyId, long userId) {
        int rows = jdbcTemplate.update("""
                UPDATE public.ai_conversation
                SET deleted = true, updated_at = :updatedAt
                WHERE id = :id AND company_id = :companyId AND user_id = :userId AND deleted = false
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("companyId", companyId)
                .addValue("userId", userId)
                .addValue("updatedAt", Timestamp.from(Instant.now())));
        return rows > 0;
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
