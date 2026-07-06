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
public class AiMessageRepository {

    private static final RowMapper<AiMessageRecord> ROW_MAPPER = (rs, rowNum) -> new AiMessageRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("conversation_id", UUID.class),
            rs.getLong("company_id"),
            nullableLong(rs.getObject("branch_id")),
            rs.getLong("user_id"),
            rs.getString("role"),
            rs.getString("content"),
            rs.getInt("token_count"),
            rs.getTimestamp("created_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiMessageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AiMessageRecord create(UUID conversationId,
                                  long companyId,
                                  Long branchId,
                                  long userId,
                                  String role,
                                  String content,
                                  int tokenCount) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO public.ai_message
                    (id, conversation_id, company_id, branch_id, user_id, role, content, token_count, created_at)
                VALUES
                    (:id, :conversationId, :companyId, :branchId, :userId, :role, :content, :tokenCount, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("conversationId", conversationId)
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("userId", userId)
                .addValue("role", role)
                .addValue("content", content)
                .addValue("tokenCount", Math.max(0, tokenCount))
                .addValue("createdAt", Timestamp.from(now)));
        return new AiMessageRecord(id, conversationId, companyId, branchId, userId, role, content, Math.max(0, tokenCount), now);
    }

    public List<AiMessageRecord> findByConversation(UUID conversationId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, conversation_id, company_id, branch_id, user_id, role, content, token_count, created_at
                FROM (
                    SELECT id, conversation_id, company_id, branch_id, user_id, role, content, token_count, created_at
                    FROM public.ai_message
                    WHERE conversation_id = :conversationId
                    ORDER BY created_at DESC
                    LIMIT :limit
                ) recent_messages
                ORDER BY created_at ASC
                """, new MapSqlParameterSource()
                .addValue("conversationId", conversationId)
                .addValue("limit", Math.max(1, Math.min(limit, 100))), ROW_MAPPER);
    }

    public List<AiMessageRecord> findOldestByConversation(UUID conversationId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, conversation_id, company_id, branch_id, user_id, role, content, token_count, created_at
                FROM public.ai_message
                WHERE conversation_id = :conversationId
                ORDER BY created_at ASC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("conversationId", conversationId)
                .addValue("limit", Math.max(1, Math.min(limit, 100))), ROW_MAPPER);
    }

    public int countByConversation(UUID conversationId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM public.ai_message
                WHERE conversation_id = :conversationId
                """, new MapSqlParameterSource("conversationId", conversationId), Integer.class);
        return count == null ? 0 : count;
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
