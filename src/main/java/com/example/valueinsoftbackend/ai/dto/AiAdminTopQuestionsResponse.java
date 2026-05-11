package com.example.valueinsoftbackend.ai.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AiAdminTopQuestionsResponse(
        Instant generatedAt,
        LocalDate fromDate,
        LocalDate toDate,
        List<AiAdminTopQuestionDto> questions
) {
}
