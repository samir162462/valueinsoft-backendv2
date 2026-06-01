package com.example.valueinsoftbackend.Configuration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationAiKnowledgeTest {

    @Test
    void aiKnowledgePgvectorFoundationMigrationExists() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V98__ai_knowledge_pgvector_foundation.sql"
        );

        assertTrue(migration.exists(), "Missing AI knowledge pgvector migration resource");

        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

        assertTrue(sql.contains("pg_available_extensions"));
        assertTrue(sql.contains("create extension if not exists vector"));
        assertTrue(sql.contains("create extension if not exists pgcrypto"));
        assertTrue(sql.contains("create table if not exists public.ai_knowledge_document"));
        assertTrue(sql.contains("create table if not exists public.ai_knowledge_chunk"));
        assertTrue(sql.contains("create table if not exists public.ai_knowledge_ingestion_job"));
        assertTrue(sql.contains("embedding vector(768) null"));
        assertTrue(sql.contains("embedding double precision[] null"));
        assertTrue(sql.contains("using hnsw (embedding vector_cosine_ops)"));
        assertTrue(sql.contains("where embedding is not null"));
        assertTrue(sql.contains("idx_ai_knowledge_document_scope"));
        assertTrue(sql.contains("idx_ai_knowledge_chunk_scope"));
    }
}
