package com.example.valueinsoftbackend.ai.memory;

import com.example.valueinsoftbackend.ai.service.AiModelClient;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claude-style memory layer for the AI assistant:
 * - Per-user long-term memory (language, name, preferences, explicit "remember" notes).
 * - Rolling conversation summary so long chats keep context beyond the recent-message window.
 * All operations fail safely: memory must never break the chat flow.
 */
@Service
@Slf4j
public class AiMemoryService {

    private static final int MAX_MEMORY_VALUE_CHARS = 500;
    private static final int SUMMARY_TRIGGER_MESSAGE_COUNT = 12;
    private static final int SUMMARY_REFRESH_EVERY_MESSAGES = 8;
    private static final int SUMMARY_SOURCE_MESSAGE_LIMIT = 40;

    private static final Pattern NAME_EN = Pattern.compile("\\bmy name is\\s+([\\p{L} .'-]{2,60})", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_AR = Pattern.compile("(?:اسمي|أنا اسمي)\\s+([\\p{L} .'-]{2,60})");
    private static final Pattern REMEMBER_EN = Pattern.compile("\\bremember(?: that)?[:,]?\\s+(.{3,300})", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMEMBER_AR = Pattern.compile("(?:تذكر أن|تذكر|احفظ أن|احفظ)\\s+(.{3,300})");
    private static final Pattern PREFER_EN = Pattern.compile("\\bi (?:prefer|always want|like my answers)\\s+(.{3,200})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFER_AR = Pattern.compile("(?:أفضل أن|أفضل دائما|أريد دائما)\\s+(.{3,200})");

    private final AiUserMemoryRepository userMemoryRepository;
    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiModelClient aiModelClient;

    public AiMemoryService(AiUserMemoryRepository userMemoryRepository,
                           AiConversationRepository conversationRepository,
                           AiMessageRepository messageRepository,
                           AiModelClient aiModelClient) {
        this.userMemoryRepository = userMemoryRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.aiModelClient = aiModelClient;
    }

    /**
     * Formats the user's long-term memory as a prompt block. Empty string when no memory exists.
     */
    public String buildUserMemoryContext(long companyId, long userId) {
        try {
            List<AiUserMemoryRecord> records = userMemoryRepository.findByUser(companyId, userId, 20);
            if (records.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder("USER MEMORY (long-term facts about this user; use them naturally, do not repeat them back unprompted):\n");
            for (AiUserMemoryRecord record : records) {
                builder.append("- ").append(record.memoryKey()).append(": ").append(record.memoryValue()).append('\n');
            }
            return builder.toString().trim();
        } catch (RuntimeException exception) {
            log.warn("AI user memory load failed safely: {}", exception.getMessage());
            return "";
        }
    }

    /**
     * Extracts durable facts from a user message and stores them. Heuristic and fail-safe.
     */
    public void captureUserFacts(long companyId, long userId, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        try {
            String sanitized = sanitize(message);

            userMemoryRepository.upsert(companyId, userId, "preferred_language",
                    containsArabic(sanitized) ? "ar" : "en", "AUTO");

            firstGroup(NAME_EN, sanitized).or(() -> firstGroup(NAME_AR, sanitized))
                    .ifPresent(name -> userMemoryRepository.upsert(companyId, userId, "display_name", truncate(name), "AUTO"));

            firstGroup(REMEMBER_EN, sanitized).or(() -> firstGroup(REMEMBER_AR, sanitized))
                    .ifPresent(note -> appendListMemory(companyId, userId, "notes", truncate(note)));

            firstGroup(PREFER_EN, sanitized).or(() -> firstGroup(PREFER_AR, sanitized))
                    .ifPresent(preference -> appendListMemory(companyId, userId, "preferences", truncate(preference)));
        } catch (RuntimeException exception) {
            log.warn("AI user memory capture failed safely: {}", exception.getMessage());
        }
    }

    /**
     * Returns the stored rolling summary for a conversation, or empty string.
     */
    public String conversationSummary(UUID conversationId) {
        try {
            return conversationRepository.findSummary(conversationId)
                    .map(AiConversationRepository.ConversationSummary::summary)
                    .filter(summary -> summary != null && !summary.isBlank())
                    .orElse("");
        } catch (RuntimeException exception) {
            log.warn("AI conversation summary load failed safely: {}", exception.getMessage());
            return "";
        }
    }

    /**
     * Refreshes the rolling conversation summary in the background when the chat is long enough.
     * Never blocks or fails the request thread.
     */
    public void maybeSummarizeAsync(UUID conversationId) {
        CompletableFuture.runAsync(() -> {
            try {
                int totalMessages = messageRepository.countByConversation(conversationId);
                if (totalMessages < SUMMARY_TRIGGER_MESSAGE_COUNT) {
                    return;
                }
                AiConversationRepository.ConversationSummary current = conversationRepository.findSummary(conversationId)
                        .orElse(new AiConversationRepository.ConversationSummary(null, 0));
                if (totalMessages - current.summaryMessageCount() < SUMMARY_REFRESH_EVERY_MESSAGES) {
                    return;
                }
                String newSummary = generateSummary(current.summary(), conversationId);
                if (!newSummary.isBlank()) {
                    conversationRepository.updateSummary(conversationId, newSummary, totalMessages);
                    log.debug("AI conversation summary refreshed conversationId={} messages={}", conversationId, totalMessages);
                }
            } catch (RuntimeException exception) {
                log.warn("AI conversation summarization failed safely for {}: {}", conversationId, exception.getMessage());
            }
        });
    }

    private String generateSummary(String previousSummary, UUID conversationId) {
        List<AiMessageRecord> messages = messageRepository.findByConversation(conversationId, SUMMARY_SOURCE_MESSAGE_LIMIT);
        if (messages.isEmpty()) {
            return "";
        }
        StringBuilder transcript = new StringBuilder();
        for (AiMessageRecord message : messages) {
            String role = "ASSISTANT".equalsIgnoreCase(message.role()) ? "Assistant" : "User";
            transcript.append(role).append(": ").append(sanitize(message.content())).append('\n');
            if (transcript.length() > 8_000) {
                break;
            }
        }
        String systemPrompt = """
                You maintain a rolling memory summary of a business assistant conversation.
                Produce an updated summary in at most 150 words.
                Keep: user goals, decisions made, key figures mentioned, open questions, user preferences.
                Drop: greetings, filler, repeated content.
                Write in the dominant language of the conversation.
                Output only the summary text, nothing else.
                """;
        String userMessage = (previousSummary == null || previousSummary.isBlank()
                ? "There is no previous summary."
                : "Previous summary:\n" + previousSummary)
                + "\n\nRecent conversation:\n" + transcript;
        AiModelResponse response = aiModelClient.generate(new AiModelRequest(systemPrompt, userMessage, "SUMMARY", ""));
        String answer = response == null || response.answer() == null ? "" : response.answer().trim();
        return answer.length() > 2_000 ? answer.substring(0, 2_000) : answer;
    }

    private void appendListMemory(long companyId, long userId, String key, String newItem) {
        String existing = userMemoryRepository.findByUser(companyId, userId, 20).stream()
                .filter(record -> key.equals(record.memoryKey()))
                .map(AiUserMemoryRecord::memoryValue)
                .findFirst()
                .orElse("");
        String combined = existing.isBlank() ? "- " + newItem : existing + "\n- " + newItem;
        if (combined.length() > 1_500) {
            combined = combined.substring(combined.length() - 1_500);
            int firstBullet = combined.indexOf("- ");
            if (firstBullet > 0) {
                combined = combined.substring(firstBullet);
            }
        }
        userMemoryRepository.upsert(companyId, userId, key, combined, "AUTO");
    }

    private java.util.Optional<String> firstGroup(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            String group = matcher.group(1);
            return group == null || group.isBlank()
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(group.trim());
        }
        return java.util.Optional.empty();
    }

    private boolean containsArabic(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x0600 && codePoint <= 0x06FF);
    }

    private String truncate(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_MEMORY_VALUE_CHARS
                ? normalized
                : normalized.substring(0, MAX_MEMORY_VALUE_CHARS - 3).trim() + "...";
    }

    private String sanitize(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("(?i)(api[_ -]?key|token|secret|password)\\s*[:=]\\s*\\S+", "$1=[REDACTED]")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
