package com.example.valueinsoftbackend.companyinsights.ai;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.service.AiModelClient;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import com.example.valueinsoftbackend.companyinsights.engine.InsightCandidate;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional AI enrichment using a strict slot/template contract. The backend owns every
 * metric, name, date, and action code; the model may only produce a concise executive
 * summary (ar/en) around those approved values. Any summary that introduces a numeric token
 * not present in the deterministic slots is rejected and the deterministic output stands.
 *
 * <p>Title and description remain the deterministic source of truth and are never replaced.
 */
@Service
@Slf4j
public class CompanyInsightAiNarrativeService {

    private static final Pattern NUMERIC_TOKEN = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    private final AiModelClient modelClient;
    private final AiProperties aiProperties;
    private final com.example.valueinsoftbackend.ai.audit.AiUsageLogService usageLogService;
    private final Gson gson = new Gson();

    public CompanyInsightAiNarrativeService(AiModelClient modelClient,
                                            AiProperties aiProperties,
                                            com.example.valueinsoftbackend.ai.audit.AiUsageLogService usageLogService) {
        this.modelClient = modelClient;
        this.aiProperties = aiProperties;
        this.usageLogService = usageLogService;
    }

    public boolean globallyEnabled() {
        return aiProperties.isEnabled();
    }

    /**
     * Attempt to produce validated ar/en executive summaries for an insight. Returns empty on
     * any failure so the caller keeps the deterministic rendering.
     */
    public Optional<Enrichment> enrich(InsightCandidate candidate) {
        if (!aiProperties.isEnabled()) {
            return Optional.empty();
        }
        try {
            String system = """
                    You are ValueInSoft's company insight narrator.
                    You receive an insight with fixed backend-owned slots (numbers, names, dates) and a
                    deterministic title and description. Write only a concise executive summary.
                    Rules: use ONLY the provided values; never introduce any new number, percentage, date,
                    branch, or product; do not restate figures that are not in the slots. Return strict JSON.
                    """;
            String user = """
                    Insight type: %s
                    Deterministic title (Arabic): %s
                    Deterministic description (Arabic): %s
                    Slots (backend-owned, do not alter): %s
                    Allowed action code: %s

                    Return strict JSON only, no markdown:
                    {"summaryAr": "one concise Arabic sentence", "summaryEn": "one concise English sentence",
                     "recommendedActionCode": "%s"}
                    """.formatted(
                    candidate.type().name(),
                    candidate.title(),
                    candidate.description(),
                    gson.toJson(candidate.slots()),
                    candidate.actionCode() == null ? "" : candidate.actionCode().name(),
                    candidate.actionCode() == null ? "" : candidate.actionCode().name());

            long startedAt = System.nanoTime();
            AiModelResponse response = modelClient.generate(new AiModelRequest(system, user, "COMPANY_INSIGHT_NARRATIVE", ""));
            // Metered billing: background enrichment is still company-billable usage.
            usageLogService.logChatUsage(candidate.companyId(), 0L, null,
                    Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L));
            JsonObject json = JsonParser.parseString(stripFence(response.answer())).getAsJsonObject();

            String summaryAr = stringValue(json, "summaryAr");
            String summaryEn = stringValue(json, "summaryEn");
            String actionCode = stringValue(json, "recommendedActionCode");

            if (summaryAr.isBlank() && summaryEn.isBlank()) {
                return Optional.empty();
            }
            // Validate action code stays within the allowed (backend-owned) action.
            if (candidate.actionCode() != null && !actionCode.isBlank()
                    && !candidate.actionCode().name().equals(actionCode)) {
                return Optional.empty();
            }
            // Reject any numeric token not present in the deterministic rendering / slots.
            String allowedNumbers = candidate.title() + " " + candidate.description() + " " + gson.toJson(candidate.slots());
            if (introducesUnknownNumber(summaryAr, allowedNumbers) || introducesUnknownNumber(summaryEn, allowedNumbers)) {
                log.debug("AI summary rejected (unknown numeric token) type={}", candidate.type());
                return Optional.empty();
            }

            Map<String, Object> localized = deepCopyLocalized(candidate.localized());
            applySummary(localized, "ar", summaryAr);
            applySummary(localized, "en", summaryEn);

            return Optional.of(new Enrichment(localized, summaryAr.isBlank() ? summaryEn : summaryAr,
                    response.modelName()));
        } catch (RuntimeException exception) {
            log.debug("AI enrichment failed type={} reason={}", candidate.type(), exception.getMessage());
            return Optional.empty();
        }
    }

    private boolean introducesUnknownNumber(String text, String allowed) {
        if (text == null || text.isBlank()) {
            return false;
        }
        Matcher matcher = NUMERIC_TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group().replace(",", "");
            String normalizedAllowed = allowed.replace(",", "");
            if (!normalizedAllowed.contains(token)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyLocalized(Map<String, Object> localized) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (localized == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : localized.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> inner) {
                copy.put(entry.getKey(), new LinkedHashMap<>((Map<String, Object>) inner));
            } else {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private void applySummary(Map<String, Object> localized, String locale, String summary) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        Object bucket = localized.get(locale);
        if (bucket instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).put("summary", summary);
        }
    }

    private String stringValue(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString().trim() : "";
    }

    private String stripFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```$", "");
        }
        return text.trim();
    }

    public record Enrichment(Map<String, Object> localized, String executiveSummary, String model) {
    }
}
