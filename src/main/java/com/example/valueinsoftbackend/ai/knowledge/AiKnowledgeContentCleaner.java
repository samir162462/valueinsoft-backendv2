package com.example.valueinsoftbackend.ai.knowledge;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class AiKnowledgeContentCleaner {

    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1>");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[a-z0-9._\\-]{20,}");
    private static final Pattern JWT = Pattern.compile("\\beyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}\\b");
    private static final Pattern PASSWORD_ASSIGNMENT = Pattern.compile("(?i)(password|passwd|pwd)\\s*[:=]\\s*['\\\"]?[^\\s,'\\\"}]{3,}");
    private static final Pattern API_KEY_ASSIGNMENT = Pattern.compile("(?i)(api[_-]?key|secret[_-]?key|access[_-]?key|token)\\s*[:=]\\s*['\\\"]?[a-z0-9._\\-+/=]{16,}");
    private static final Pattern JSON_SECRET = Pattern.compile("(?i)(\"(?:password|passwd|pwd|api[_-]?key|secret[_-]?key|access[_-]?key|token)\"\\s*:\\s*\")[^\"]+\"");
    private static final Pattern CONNECTION_STRING = Pattern.compile("(?i)\\b(?:jdbc:postgresql|postgres(?:ql)?://|mysql://|mongodb(?:\\+srv)?://)[^\\s]+");
    private static final Pattern AWS_ACCESS_KEY = Pattern.compile("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b");
    private static final Pattern WHITESPACE = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern MANY_NEWLINES = Pattern.compile("\\n{3,}");

    public String clean(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String cleaned = content;
        cleaned = SCRIPT_OR_STYLE.matcher(cleaned).replaceAll(" ");
        cleaned = HTML_TAG.matcher(cleaned).replaceAll(" ");
        cleaned = CONTROL_CHARS.matcher(cleaned).replaceAll(" ");
        cleaned = redactSecrets(cleaned);
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll(" *\\n *", "\n");
        cleaned = MANY_NEWLINES.matcher(cleaned).replaceAll("\n\n");
        return cleaned.trim();
    }

    public String redactSecrets(String content) {
        if (content == null || content.isBlank()) {
            return content == null ? "" : content;
        }
        String redacted = content;
        redacted = BEARER_TOKEN.matcher(redacted).replaceAll("Bearer [REDACTED]");
        redacted = JWT.matcher(redacted).replaceAll("[REDACTED_JWT]");
        redacted = JSON_SECRET.matcher(redacted).replaceAll("$1[REDACTED]\"");
        redacted = PASSWORD_ASSIGNMENT.matcher(redacted).replaceAll("$1=[REDACTED]");
        redacted = API_KEY_ASSIGNMENT.matcher(redacted).replaceAll("$1=[REDACTED]");
        redacted = CONNECTION_STRING.matcher(redacted).replaceAll("[REDACTED_CONNECTION_STRING]");
        redacted = AWS_ACCESS_KEY.matcher(redacted).replaceAll("[REDACTED_AWS_ACCESS_KEY]");
        return redacted;
    }
}
