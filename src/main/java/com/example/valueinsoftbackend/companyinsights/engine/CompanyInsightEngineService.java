package com.example.valueinsoftbackend.companyinsights.engine;

import com.example.valueinsoftbackend.companyinsights.ai.CompanyInsightAiNarrativeService;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightProperties;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightThresholds;
import com.example.valueinsoftbackend.companyinsights.config.InsightCategory;
import com.example.valueinsoftbackend.companyinsights.config.InsightRole;
import com.example.valueinsoftbackend.companyinsights.lifecycle.CompanyInsightRepository;
import com.example.valueinsoftbackend.companyinsights.lifecycle.CompanyInsightSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the deterministic rule set for a company, applies the correlation & suppression
 * layer (priority ranking + per-category active caps), and persists atomic insights.
 *
 * <p>No AI is called here; the deterministic rendering is the source of truth.
 */
@Service
@Slf4j
public class CompanyInsightEngineService {

    private final List<CompanyInsightRule> rules;
    private final CompanyInsightSettingsService settingsService;
    private final CompanyInsightRepository insightRepository;
    private final CompanyInsightProperties properties;
    private final CompanyInsightAiNarrativeService narrativeService;

    public CompanyInsightEngineService(List<CompanyInsightRule> rules,
                                       CompanyInsightSettingsService settingsService,
                                       CompanyInsightRepository insightRepository,
                                       CompanyInsightProperties properties,
                                       CompanyInsightAiNarrativeService narrativeService) {
        this.rules = rules;
        this.settingsService = settingsService;
        this.insightRepository = insightRepository;
        this.properties = properties;
        this.narrativeService = narrativeService;
    }

    /**
     * Generate + persist insights for one company. Returns the number of persisted rows.
     */
    @Transactional
    public int generateForCompany(int companyId, LocalDate asOfDate) {
        CompanyInsightThresholds thresholds = settingsService.resolve(companyId);
        CompanyInsightRule.RuleContext context =
                new CompanyInsightRule.RuleContext(companyId, thresholds, asOfDate);

        List<InsightCandidate> candidates = new ArrayList<>();
        for (CompanyInsightRule rule : rules) {
            try {
                candidates.addAll(rule.evaluate(context));
            } catch (RuntimeException exception) {
                log.warn("Insight rule failed rule={} companyId={} reason={}",
                        rule.getClass().getSimpleName(), companyId, exception.getMessage());
            }
        }

        List<InsightCandidate> finalized = applyCorrelation(candidates, thresholds);

        boolean aiEnabled = thresholds.aiEnrichmentEnabled() && narrativeService.globallyEnabled();
        int aiBudget = properties.getAiMaxInsightsPerRun();

        int persisted = 0;
        int enriched = 0;
        for (InsightCandidate candidate : finalized) {
            persisted += insightRepository.upsertDetected(candidate, properties.getDefaultInsightTtlDays());

            // Optional AI narrative for surfaced (PRIMARY) insights, budget-capped.
            if (aiEnabled && candidate.role() == InsightRole.PRIMARY && enriched < aiBudget) {
                var enrichment = narrativeService.enrich(candidate);
                if (enrichment.isPresent()) {
                    insightRepository.applyAiEnrichment(
                            candidate.companyId(),
                            candidate.type().name(),
                            candidate.insightKey(),
                            candidate.periodStart(),
                            enrichment.get().localized(),
                            enrichment.get().executiveSummary(),
                            enrichment.get().model());
                    enriched++;
                }
            }
        }
        log.info("Company insights generated companyId={} asOf={} candidates={} persisted={} aiEnriched={}",
                companyId, asOfDate, candidates.size(), persisted, enriched);
        return persisted;
    }

    /**
     * Correlation & suppression: rank by priority within each category and mark everything
     * beyond the per-category cap as SUPPRESSED (still persisted, hidden by default in the API).
     * Cross-rule primary/contributing folding is applied by rules via correlation_group;
     * this pass enforces caps and ordering.
     */
    private List<InsightCandidate> applyCorrelation(List<InsightCandidate> candidates,
                                                    CompanyInsightThresholds thresholds) {
        Map<InsightCategory, List<InsightCandidate>> byCategory = new EnumMap<>(InsightCategory.class);
        for (InsightCandidate candidate : candidates) {
            byCategory.computeIfAbsent(candidate.type().category(), key -> new ArrayList<>()).add(candidate);
        }

        int cap = Math.max(1, thresholds.maxActiveInsightsPerCategory());
        List<InsightCandidate> result = new ArrayList<>();
        for (List<InsightCandidate> group : byCategory.values()) {
            group.sort(Comparator.comparingInt(InsightCandidate::priorityScore).reversed());
            for (int i = 0; i < group.size(); i++) {
                InsightCandidate candidate = group.get(i);
                if (i < cap) {
                    result.add(candidate);
                } else {
                    result.add(candidate.suppressed("CATEGORY_CAP"));
                }
            }
        }
        return result;
    }
}
