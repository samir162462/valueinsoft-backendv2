package com.example.valueinsoftbackend.ai.knowledge;

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
public class AiKnowledgeIngestionJobRepository {

    private static final RowMapper<AiKnowledgeIngestionJobRecord> ROW_MAPPER = (rs, rowNum) -> new AiKnowledgeIngestionJobRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("document_id", UUID.class),
            nullableLong(rs.getObject("company_id")),
            nullableLong(rs.getObject("branch_id")),
            rs.getString("status"),
            rs.getString("embedding_model"),
            rs.getInt("chunk_count"),
            rs.getString("error_message"),
            rs.getString("metadata_json"),
            toInstant(rs.getTimestamp("started_at")),
            toInstant(rs.getTimestamp("finished_at")),
            toInstant(rs.getTimestamp("created_at"))
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiKnowledgeIngestionJobRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AiKnowledgeIngestionJobRecord create(UUID documentId, Long companyId, Long branchId, String metadataJson) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO public.ai_knowledge_ingestion_job
                    (id, document_id, company_id, branch_id, status, metadata_json, created_at)
                VALUES
                    (:id, :documentId, :companyId, :branchId, 'PENDING', CAST(:metadataJson AS jsonb), :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("documentId", documentId)
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("metadataJson", metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson)
                .addValue("createdAt", Timestamp.from(Instant.now())));
        return findById(id).orElseThrow();
    }

    public Optional<AiKnowledgeIngestionJobRecord> findById(UUID id) {
        List<AiKnowledgeIngestionJobRecord> rows = jdbcTemplate.query("""
                SELECT id, document_id, company_id, branch_id, status, embedding_model,
                       chunk_count, error_message, metadata_json::text AS metadata_json,
                       started_at, finished_at, created_at
                FROM public.ai_knowledge_ingestion_job
                WHERE id = :id
                """, new MapSqlParameterSource("id", id), ROW_MAPPER);
        return rows.stream().findFirst();
    }

    public Optional<AiKnowledgeIngestionJobRecord> findLatestByDocumentId(UUID documentId) {
        List<AiKnowledgeIngestionJobRecord> rows = jdbcTemplate.query("""
                SELECT id, document_id, company_id, branch_id, status, embedding_model,
                       chunk_count, error_message, metadata_json::text AS metadata_json,
                       started_at, finished_at, created_at
                FROM public.ai_knowledge_ingestion_job
                WHERE document_id = :documentId
                ORDER BY created_at DESC
                LIMIT 1
                """, new MapSqlParameterSource("documentId", documentId), ROW_MAPPER);
        return rows.stream().findFirst();
    }

    public int markStarted(UUID id, String embeddingModel) {
        return jdbcTemplate.update("""
                UPDATE public.ai_knowledge_ingestion_job
                SET status = 'RUNNING',
                    embedding_model = :embeddingModel,
                    started_at = now(),
                    error_message = NULL
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("embeddingModel", embeddingModel));
    }

    public int markSucceeded(UUID id, String embeddingModel, int chunkCount) {
        return jdbcTemplate.update("""
                UPDATE public.ai_knowledge_ingestion_job
                SET status = 'SUCCEEDED',
                    embedding_model = :embeddingModel,
                    chunk_count = :chunkCount,
                    finished_at = now(),
                    error_message = NULL
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("embeddingModel", embeddingModel)
                .addValue("chunkCount", chunkCount));
    }

    public int markFailed(UUID id, String errorMessage) {
        return jdbcTemplate.update("""
                UPDATE public.ai_knowledge_ingestion_job
                SET status = 'FAILED',
                    error_message = :errorMessage,
                    finished_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("errorMessage", safeError(errorMessage)));
    }

    private static String safeError(String value) {
        if (value == null || value.isBlank()) {
            return "Knowledge ingestion failed.";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
