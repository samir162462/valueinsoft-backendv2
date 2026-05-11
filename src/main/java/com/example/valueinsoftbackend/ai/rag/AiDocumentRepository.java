package com.example.valueinsoftbackend.ai.rag;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class AiDocumentRepository {

    private static final RowMapper<AiDocumentRecord> ROW_MAPPER = (rs, rowNum) -> new AiDocumentRecord(
            rs.getObject("id", UUID.class),
            nullableLong(rs.getObject("company_id")),
            rs.getString("title"),
            rs.getString("document_type"),
            rs.getString("module"),
            rs.getString("language"),
            rs.getString("content"),
            rs.getString("metadata_json"),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiDocumentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(AiDocumentRecord document) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO public.ai_document
                    (id, company_id, title, document_type, module, language, content,
                     metadata_json, active, created_at, updated_at)
                VALUES
                    (:id, :companyId, :title, :documentType, :module, :language, :content,
                     :metadataJson, :active, :createdAt, :updatedAt)
                ON CONFLICT (id) DO UPDATE SET
                    company_id = EXCLUDED.company_id,
                    title = EXCLUDED.title,
                    document_type = EXCLUDED.document_type,
                    module = EXCLUDED.module,
                    language = EXCLUDED.language,
                    content = EXCLUDED.content,
                    metadata_json = EXCLUDED.metadata_json,
                    active = EXCLUDED.active,
                    updated_at = EXCLUDED.updated_at
                """, new MapSqlParameterSource()
                .addValue("id", document.id())
                .addValue("companyId", document.companyId())
                .addValue("title", document.title())
                .addValue("documentType", document.documentType())
                .addValue("module", document.module())
                .addValue("language", document.language())
                .addValue("content", document.content())
                .addValue("metadataJson", document.metadataJson())
                .addValue("active", document.active())
                .addValue("createdAt", Timestamp.from(document.createdAt() == null ? now : document.createdAt()))
                .addValue("updatedAt", Timestamp.from(now)));
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
