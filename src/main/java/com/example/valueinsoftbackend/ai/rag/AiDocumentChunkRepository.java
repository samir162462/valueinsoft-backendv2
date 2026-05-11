package com.example.valueinsoftbackend.ai.rag;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AiDocumentChunkRepository {

    private static final RowMapper<AiDocumentChunkRecord> ROW_MAPPER = (rs, rowNum) -> new AiDocumentChunkRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("document_id", UUID.class),
            nullableLong(rs.getObject("company_id")),
            rs.getString("title"),
            rs.getString("module"),
            rs.getString("language"),
            rs.getInt("chunk_index"),
            rs.getString("content"),
            rs.getString("metadata_json"),
            rs.getTimestamp("created_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiDocumentChunkRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceChunks(UUID documentId, List<AiDocumentChunkRecord> chunks) {
        jdbcTemplate.update("DELETE FROM public.ai_document_chunk WHERE document_id = :documentId",
                new MapSqlParameterSource("documentId", documentId));
        for (AiDocumentChunkRecord chunk : chunks) {
            create(chunk);
        }
    }

    public void create(AiDocumentChunkRecord chunk) {
        jdbcTemplate.update("""
                INSERT INTO public.ai_document_chunk
                    (id, document_id, company_id, title, module, language, chunk_index,
                     content, metadata_json, created_at)
                VALUES
                    (:id, :documentId, :companyId, :title, :module, :language, :chunkIndex,
                     :content, :metadataJson, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", chunk.id())
                .addValue("documentId", chunk.documentId())
                .addValue("companyId", chunk.companyId())
                .addValue("title", chunk.title())
                .addValue("module", chunk.module())
                .addValue("language", chunk.language())
                .addValue("chunkIndex", chunk.chunkIndex())
                .addValue("content", chunk.content())
                .addValue("metadataJson", chunk.metadataJson())
                .addValue("createdAt", Timestamp.from(chunk.createdAt() == null ? Instant.now() : chunk.createdAt())));
    }

    public List<AiDocumentChunkRecord> findActiveChunks(Long companyId, String language, int limit) {
        return jdbcTemplate.query("""
                SELECT c.id, c.document_id, c.company_id, c.title, c.module, c.language,
                       c.chunk_index, c.content, c.metadata_json, c.created_at
                FROM public.ai_document_chunk c
                JOIN public.ai_document d ON d.id = c.document_id
                WHERE d.active = true
                  AND (:language IS NULL OR c.language = :language)
                  AND (c.company_id IS NULL OR c.company_id = :companyId)
                ORDER BY c.company_id NULLS FIRST, c.title ASC, c.chunk_index ASC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("language", language)
                .addValue("limit", Math.max(1, Math.min(limit, 500))), ROW_MAPPER);
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
