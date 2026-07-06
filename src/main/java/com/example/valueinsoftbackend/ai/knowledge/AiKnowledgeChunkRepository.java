package com.example.valueinsoftbackend.ai.knowledge;

import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingService;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class AiKnowledgeChunkRepository {

    private static final RowMapper<AiKnowledgeChunkRecord> ROW_MAPPER = (rs, rowNum) -> new AiKnowledgeChunkRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("document_id", UUID.class),
            nullableLong(rs.getObject("company_id")),
            nullableLong(rs.getObject("branch_id")),
            rs.getString("module"),
            rs.getString("language"),
            rs.getInt("chunk_index"),
            rs.getString("heading"),
            rs.getString("content"),
            rs.getInt("token_count"),
            null,
            rs.getString("embedding_model"),
            rs.getString("status"),
            rs.getString("metadata_json"),
            toInstant(rs.getTimestamp("created_at"))
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AiEmbeddingService embeddingService;
    private volatile Boolean vectorColumn;

    public AiKnowledgeChunkRepository(NamedParameterJdbcTemplate jdbcTemplate, AiEmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    public int deleteByDocumentId(UUID documentId) {
        return jdbcTemplate.update("""
                DELETE FROM public.ai_knowledge_chunk
                WHERE document_id = :documentId
                """, new MapSqlParameterSource("documentId", documentId));
    }

    public void batchInsert(List<AiKnowledgeChunkRecord> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        if (!hasVectorColumn()) {
            batchInsertArrayFallback(chunks);
            return;
        }
        MapSqlParameterSource[] params = chunks.stream()
                .map(this::params)
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                INSERT INTO public.ai_knowledge_chunk
                    (id, document_id, company_id, branch_id, module, language, chunk_index,
                     heading, content, token_count, embedding, embedding_model, status, metadata_json, created_at)
                VALUES
                    (:id, :documentId, :companyId, :branchId, :module, :language, :chunkIndex,
                     :heading, :content, :tokenCount, CAST(:embedding AS vector), :embeddingModel,
                     :status, CAST(:metadataJson AS jsonb), :createdAt)
                """, params);
    }

    public void batchInsertKeywordOnly(List<AiKnowledgeChunkRecord> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] params = chunks.stream()
                .map(this::keywordOnlyParams)
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                INSERT INTO public.ai_knowledge_chunk
                    (id, document_id, company_id, branch_id, module, language, chunk_index,
                     heading, content, token_count, embedding, embedding_model, status, metadata_json, created_at)
                VALUES
                    (:id, :documentId, :companyId, :branchId, :module, :language, :chunkIndex,
                     :heading, :content, :tokenCount, NULL, :embeddingModel,
                     :status, CAST(:metadataJson AS jsonb), :createdAt)
                """, params);
    }

    private void batchInsertArrayFallback(List<AiKnowledgeChunkRecord> chunks) {
        jdbcTemplate.getJdbcTemplate().batchUpdate("""
                INSERT INTO public.ai_knowledge_chunk
                    (id, document_id, company_id, branch_id, module, language, chunk_index,
                     heading, content, token_count, embedding, embedding_model, status, metadata_json, created_at)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?,
                     ?, ?, ?, ?, ?,
                     ?, CAST(? AS jsonb), ?)
                """, chunks, 100, (statement, chunk) -> {
            if (chunk.embedding() == null) {
                throw new IllegalArgumentException("Knowledge chunk embedding is required for ingestion.");
            }
            embeddingService.validateDimension(chunk.embedding());
            int index = 1;
            statement.setObject(index++, chunk.id() == null ? UUID.randomUUID() : chunk.id());
            statement.setObject(index++, chunk.documentId());
            setNullableLong(statement, index++, chunk.companyId());
            setNullableLong(statement, index++, chunk.branchId());
            setNullableString(statement, index++, blankToNull(chunk.module()));
            statement.setString(index++, defaultIfBlank(chunk.language(), "en"));
            statement.setInt(index++, chunk.chunkIndex());
            setNullableString(statement, index++, blankToNull(chunk.heading()));
            statement.setString(index++, chunk.content());
            statement.setInt(index++, chunk.tokenCount());
            Array embeddingArray = statement.getConnection().createArrayOf("float8", toDoubleArray(chunk.embedding()));
            statement.setArray(index++, embeddingArray);
            setNullableString(statement, index++, chunk.embeddingModel());
            statement.setString(index++, defaultIfBlank(chunk.status(), "ACTIVE"));
            statement.setString(index++, defaultIfBlank(chunk.metadataJson(), "{}"));
            statement.setTimestamp(index, Timestamp.from(chunk.createdAt() == null ? Instant.now() : chunk.createdAt()));
        });
    }

    public int countByDocumentId(UUID documentId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM public.ai_knowledge_chunk
                WHERE document_id = :documentId
                """, new MapSqlParameterSource("documentId", documentId), Integer.class);
        return count == null ? 0 : count;
    }

    public List<AiKnowledgeChunkRecord> findByDocumentId(UUID documentId) {
        return jdbcTemplate.query("""
                SELECT id, document_id, company_id, branch_id, module, language, chunk_index,
                       heading, content, token_count, embedding_model, status,
                       metadata_json::text AS metadata_json, created_at
                FROM public.ai_knowledge_chunk
                WHERE document_id = :documentId
                ORDER BY chunk_index ASC
                """, new MapSqlParameterSource("documentId", documentId), ROW_MAPPER);
    }

    public List<AiRetrievedChunk> vectorSearch(AiRetrievalRequest request, float[] queryEmbedding) {
        if (!hasVectorColumn()) {
            throw new InvalidDataAccessResourceUsageException("pgvector is not installed for the current database.");
        }
        embeddingService.validateDimension(queryEmbedding);
        String vectorText = toPgVectorText(queryEmbedding);
        Set<Long> allowedBranches = request.allowedBranchIds() == null ? Set.of() : request.allowedBranchIds();
        Set<String> allowedModules = request.allowedModules() == null ? Set.of() : request.allowedModules();
        boolean modulesEmpty = allowedModules.isEmpty();
        String language = request.language() == null || request.language().isBlank() ? "en" : request.language().trim();
        int topK = Math.max(1, request.topK() == null ? 5 : Math.min(request.topK(), 25));
        double threshold = request.similarityThreshold() == null ? 0.60 : request.similarityThreshold();

        return jdbcTemplate.getJdbcTemplate().query(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT c.id AS chunk_id,
                           c.document_id,
                           d.title AS document_title,
                           c.company_id,
                           c.branch_id,
                           c.module,
                           c.language,
                           c.heading,
                           c.content,
                           CASE
                               WHEN length(c.content) <= 260 THEN c.content
                               ELSE substring(c.content from 1 for 257) || '...'
                           END AS content_preview,
                           1 - (c.embedding <=> CAST(? AS vector)) AS similarity,
                           d.source_type,
                           d.source_uri
                    FROM public.ai_knowledge_chunk c
                    JOIN public.ai_knowledge_document d ON d.id = c.document_id
                    WHERE d.status = 'ACTIVE'
                      AND c.status = 'ACTIVE'
                      AND c.embedding IS NOT NULL
                      AND (
                            (?::bigint IS NOT NULL AND c.company_id = ?::bigint)
                            OR (? = true AND c.company_id IS NULL)
                      )
                      AND (
                            c.branch_id IS NULL
                            OR c.branch_id = ANY(?::bigint[])
                      )
                      AND (
                            ?::bigint IS NULL
                            OR c.branch_id IS NULL
                            OR c.branch_id = ?::bigint
                      )
                      AND (
                            ? = true
                            OR c.module = ANY(?::text[])
                            OR c.module IS NULL
                      )
                      AND (
                            c.language = ?
                            OR c.language = 'en'
                      )
                      AND (1 - (c.embedding <=> CAST(? AS vector))) >= ?
                    ORDER BY c.embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """);
            Array branchArray = connection.createArrayOf("int8", allowedBranches.toArray(Long[]::new));
            Array moduleArray = connection.createArrayOf("text", allowedModules.toArray(String[]::new));
            int index = 1;
            statement.setString(index++, vectorText);
            setNullableLong(statement, index++, request.companyId());
            setNullableLong(statement, index++, request.companyId());
            statement.setBoolean(index++, request.allowGlobalDocs());
            statement.setArray(index++, branchArray);
            setNullableLong(statement, index++, request.selectedBranchId());
            setNullableLong(statement, index++, request.selectedBranchId());
            statement.setBoolean(index++, modulesEmpty);
            statement.setArray(index++, moduleArray);
            statement.setString(index++, language);
            statement.setString(index++, vectorText);
            statement.setDouble(index++, threshold);
            statement.setString(index++, vectorText);
            statement.setInt(index, topK);
            return statement;
        }, (rs, rowNum) -> new AiRetrievedChunk(
                rs.getObject("chunk_id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("document_title"),
                nullableLong(rs.getObject("company_id")),
                nullableLong(rs.getObject("branch_id")),
                rs.getString("module"),
                rs.getString("language"),
                rs.getString("heading"),
                rs.getString("content"),
                rs.getString("content_preview"),
                rs.getDouble("similarity"),
                rs.getString("source_type"),
                rs.getString("source_uri"),
                Map.of(),
                "VECTOR"
        ));
    }

    public boolean hasVectorColumn() {
        Boolean cached = vectorColumn;
        if (cached != null) {
            return cached;
        }
        Boolean detected = jdbcTemplate.queryForObject("""
                SELECT format_type(a.atttypid, a.atttypmod) LIKE 'vector%'
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public'
                  AND c.relname = 'ai_knowledge_chunk'
                  AND a.attname = 'embedding'
                  AND NOT a.attisdropped
                """, new MapSqlParameterSource(), Boolean.class);
        vectorColumn = Boolean.TRUE.equals(detected);
        return vectorColumn;
    }

    private MapSqlParameterSource params(AiKnowledgeChunkRecord chunk) {
        if (chunk.embedding() == null) {
            throw new IllegalArgumentException("Knowledge chunk embedding is required for ingestion.");
        }
        embeddingService.validateDimension(chunk.embedding());
        return new MapSqlParameterSource()
                .addValue("id", chunk.id() == null ? UUID.randomUUID() : chunk.id())
                .addValue("documentId", chunk.documentId())
                .addValue("companyId", chunk.companyId())
                .addValue("branchId", chunk.branchId())
                .addValue("module", blankToNull(chunk.module()))
                .addValue("language", defaultIfBlank(chunk.language(), "en"))
                .addValue("chunkIndex", chunk.chunkIndex())
                .addValue("heading", blankToNull(chunk.heading()))
                .addValue("content", chunk.content())
                .addValue("tokenCount", chunk.tokenCount())
                .addValue("embedding", toPgVectorText(chunk.embedding()))
                .addValue("embeddingModel", chunk.embeddingModel())
                .addValue("status", defaultIfBlank(chunk.status(), "ACTIVE"))
                .addValue("metadataJson", defaultIfBlank(chunk.metadataJson(), "{}"))
                .addValue("createdAt", Timestamp.from(chunk.createdAt() == null ? Instant.now() : chunk.createdAt()));
    }

    private MapSqlParameterSource keywordOnlyParams(AiKnowledgeChunkRecord chunk) {
        return new MapSqlParameterSource()
                .addValue("id", chunk.id() == null ? UUID.randomUUID() : chunk.id())
                .addValue("documentId", chunk.documentId())
                .addValue("companyId", chunk.companyId())
                .addValue("branchId", chunk.branchId())
                .addValue("module", blankToNull(chunk.module()))
                .addValue("language", defaultIfBlank(chunk.language(), "en"))
                .addValue("chunkIndex", chunk.chunkIndex())
                .addValue("heading", blankToNull(chunk.heading()))
                .addValue("content", chunk.content())
                .addValue("tokenCount", chunk.tokenCount())
                .addValue("embeddingModel", defaultIfBlank(chunk.embeddingModel(), "keyword-fallback"))
                .addValue("status", defaultIfBlank(chunk.status(), "ACTIVE"))
                .addValue("metadataJson", defaultIfBlank(chunk.metadataJson(), "{}"))
                .addValue("createdAt", Timestamp.from(chunk.createdAt() == null ? Instant.now() : chunk.createdAt()));
    }

    static String toPgVectorText(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(vector[index]));
        }
        return builder.append(']').toString();
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
            return;
        }
        statement.setLong(index, value);
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private static Double[] toDoubleArray(float[] vector) {
        Double[] values = new Double[vector.length];
        for (int index = 0; index < vector.length; index++) {
            values[index] = (double) vector[index];
        }
        return values;
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
