package com.example.valueinsoftbackend.ai.memory;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiMessageRepositoryTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findByConversationLoadsLatestMessagesThenReturnsThemChronologically() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        AiMessageRepository repository = new AiMessageRepository(jdbcTemplate);

        repository.findByConversation(UUID.randomUUID(), 12);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        String sql = sqlCaptor.getValue().replaceAll("\\s+", " ").trim().toLowerCase();

        assertTrue(sql.contains("from ( select"));
        assertTrue(sql.contains("order by created_at desc limit :limit"));
        assertTrue(sql.endsWith("order by created_at asc"));
    }
}
