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
public class AiKnowledgeDocumentRepository {

    private static final RowMapper<AiKnowledgeDocumentRecord> ROW_MAPPER = (rs, rowNum) -> new AiKnowledgeDocumentRecord(
            rs.getObject("id", UUID.class),
            nullableLong(rs.getObject("company_id")),
            nullableLong(rs.getObject("branch_id")),
            rs.getString("module"),
            rs.getString("language"),
            rs.getString("document_type"),
            rs.getString("title"),
            rs.getString("source_type"),
            rs.getString("source_uri"),
            rs.getString("content_hash"),
            rs.getString("raw_content"),
            rs.getString("normalized_content"),
            rs.getString("status"),
            rs.getString("metadata_json"),
            nullableLong(rs.getObject("created_by_user_id")),
            nullableLong(rs.getObject("updated_by_user_id")),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiKnowledgeDocumentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AiKnowledgeDocumentRecord insert(AiKnowledgeDocumentRecord document) {
        UUID id = document.id() == null ? UUID.randomUUID() : document.id();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO public.ai_knowledge_document
                    (id, company_id, branch_id, module, language, document_type, title,
                     source_type, source_uri, content_hash, raw_content, normalized_content,
                     status, metadata_json, created_by_user_id, updated_by_user_id, created_at, updated_at)
                VALUES
                    (:id, :companyId, :branchId, :module, :language, :documentType, :title,
                     :sourceType, :sourceUri, :contentHash, :rawContent, :normalizedContent,
                     :status, CAST(:metadataJson AS jsonb), :createdByUserId, :updatedByUserId, :createdAt, :updatedAt)
                """, params(document)
                .addValue("id", id)
                .addValue("createdAt", Timestamp.from(document.createdAt() == null ? now : document.createdAt()))
                .addValue("updatedAt", Timestamp.from(document.updatedAt() == null ? now : document.updatedAt())));
        return findById(id).orElseThrow();
    }

    public Optional<AiKnowledgeDocumentRecord> findById(UUID id) {
        List<AiKnowledgeDocumentRecord> rows = jdbcTemplate.query("""
                SELECT id, company_id, branch_id, module, language, document_type, title,
                       source_type, source_uri, content_hash, raw_content, normalized_content,
                       status, metadata_json::text AS metadata_json, created_by_user_id,
                       updated_by_user_id, created_at, updated_at
                FROM public.ai_knowledge_document
                WHERE id = :id
                """, new MapSqlParameterSource("id", id), ROW_MAPPER);
        return rows.stream().findFirst();
    }

    public List<AiKnowledgeDocumentRecord> list(Long companyId, String module, String status, int limit) {
        return jdbcTemplate.query("""
                SELECT id, company_id, branch_id, module, language, document_type, title,
                       source_type, source_uri, content_hash, raw_content, normalized_content,
                       status, metadata_json::text AS metadata_json, created_by_user_id,
                       updated_by_user_id, created_at, updated_at
                FROM public.ai_knowledge_document
                WHERE (CAST(:companyId AS BIGINT) IS NULL OR company_id IS NULL OR company_id = CAST(:companyId AS BIGINT))
                  AND (CAST(:module AS VARCHAR) IS NULL OR module = CAST(:module AS VARCHAR))
                  AND (CAST(:status AS VARCHAR) IS NULL OR status = CAST(:status AS VARCHAR))
                ORDER BY updated_at DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("module", blankToNull(module))
                .addValue("status", blankToNull(status))
                .addValue("limit", Math.max(1, Math.min(limit, 500))), ROW_MAPPER);
    }

    public List<AiKnowledgeDocumentRecord> listScoped(Long companyId,
                                                      String module,
                                                      String status,
                                                      Long branchId,
                                                      String language,
                                                      int page,
                                                      int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        return jdbcTemplate.query("""
                SELECT id, company_id, branch_id, module, language, document_type, title,
                       source_type, source_uri, content_hash, raw_content, normalized_content,
                       status, metadata_json::text AS metadata_json, created_by_user_id,
                       updated_by_user_id, created_at, updated_at
                FROM public.ai_knowledge_document
                WHERE ((CAST(:companyId AS BIGINT) IS NULL AND company_id IS NULL) OR company_id = CAST(:companyId AS BIGINT))
                  AND (CAST(:module AS VARCHAR) IS NULL OR module = CAST(:module AS VARCHAR))
                  AND (CAST(:status AS VARCHAR) IS NULL OR status = CAST(:status AS VARCHAR))
                  AND (CAST(:branchId AS BIGINT) IS NULL OR branch_id IS NULL OR branch_id = CAST(:branchId AS BIGINT))
                  AND (CAST(:language AS VARCHAR) IS NULL OR language = CAST(:language AS VARCHAR))
                ORDER BY updated_at DESC
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("module", blankToNull(module))
                .addValue("status", blankToNull(status))
                .addValue("branchId", branchId)
                .addValue("language", blankToNull(language))
                .addValue("limit", safeSize)
                .addValue("offset", safePage * safeSize), ROW_MAPPER);
    }

    public long countScoped(Long companyId, String module, String status, Long branchId, String language) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM public.ai_knowledge_document
                WHERE ((CAST(:companyId AS BIGINT) IS NULL AND company_id IS NULL) OR company_id = CAST(:companyId AS BIGINT))
                  AND (CAST(:module AS VARCHAR) IS NULL OR module = CAST(:module AS VARCHAR))
                  AND (CAST(:status AS VARCHAR) IS NULL OR status = CAST(:status AS VARCHAR))
                  AND (CAST(:branchId AS BIGINT) IS NULL OR branch_id IS NULL OR branch_id = CAST(:branchId AS BIGINT))
                  AND (CAST(:language AS VARCHAR) IS NULL OR language = CAST(:language AS VARCHAR))
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("module", blankToNull(module))
                .addValue("status", blankToNull(status))
                .addValue("branchId", branchId)
                .addValue("language", blankToNull(language)), Long.class);
        return count == null ? 0 : count;
    }

    public int updateStatus(UUID id, String status) {
        return jdbcTemplate.update("""
                UPDATE public.ai_knowledge_document
                SET status = :status, updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status));
    }

    public int updateContentHash(UUID id, String contentHash, String normalizedContent) {
        return jdbcTemplate.update("""
                UPDATE public.ai_knowledge_document
                SET content_hash = :contentHash,
                    normalized_content = :normalizedContent,
                    updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("contentHash", contentHash)
                .addValue("normalizedContent", normalizedContent));
    }

    public int updateUpdatedAt(UUID id) {
        return jdbcTemplate.update("""
                UPDATE public.ai_knowledge_document
                SET updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource("id", id));
    }

    private MapSqlParameterSource params(AiKnowledgeDocumentRecord document) {
        return new MapSqlParameterSource()
                .addValue("companyId", document.companyId())
                .addValue("branchId", document.branchId())
                .addValue("module", blankToNull(document.module()))
                .addValue("language", defaultIfBlank(document.language(), "en"))
                .addValue("documentType", defaultIfBlank(document.documentType(), "HELP_ARTICLE"))
                .addValue("title", document.title())
                .addValue("sourceType", defaultIfBlank(document.sourceType(), "MANUAL"))
                .addValue("sourceUri", blankToNull(document.sourceUri()))
                .addValue("contentHash", blankToNull(document.contentHash()))
                .addValue("rawContent", document.rawContent())
                .addValue("normalizedContent", document.normalizedContent())
                .addValue("status", defaultIfBlank(document.status(), "DRAFT"))
                .addValue("metadataJson", defaultIfBlank(document.metadataJson(), "{}"))
                .addValue("createdByUserId", document.createdByUserId())
                .addValue("updatedByUserId", document.updatedByUserId());
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
