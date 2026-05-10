package com.example.valueinsoftbackend.ai.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class AiResponseSanitizerService {

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?i)bearer\\s+[a-z0-9._\\-]+"),
            Pattern.compile("(?i)jwt\\s*[:=]\\s*[^\\s,;]+"),
            Pattern.compile("(?i)api[_\\- ]?key\\s*[:=]\\s*[^\\s,;]+"),
            Pattern.compile("(?i)secret\\s*[:=]\\s*[^\\s,;]+"),
            Pattern.compile("(?i)c_\\d+\\.\"?[a-z0-9_]+\"?"),
            Pattern.compile("(?i)public\\.\"?[a-z0-9_]+\"?")
    );

    public String sanitize(String answer) {
        if (answer == null || answer.isBlank()) {
            return "I could not generate an answer for that request.";
        }

        String sanitized = answer;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("[redacted]");
        }
        return sanitized.trim();
    }
}
