package com.example.valueinsoftbackend.companyinsights.lifecycle;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightThresholds;
import org.springframework.stereotype.Service;

/**
 * Resolves per-company insight thresholds, falling back to code defaults (seeded with the
 * company currency) when no {@code company_insight_settings} row exists yet.
 */
@Service
public class CompanyInsightSettingsService {

    private final CompanyInsightSettingsRepository settingsRepository;
    private final DbCompany dbCompany;

    public CompanyInsightSettingsService(CompanyInsightSettingsRepository settingsRepository,
                                         DbCompany dbCompany) {
        this.settingsRepository = settingsRepository;
        this.dbCompany = dbCompany;
    }

    public CompanyInsightThresholds resolve(long companyId) {
        return settingsRepository.find(companyId).orElseGet(() -> defaultsFor(companyId));
    }

    public CompanyInsightThresholds save(CompanyInsightThresholds thresholds) {
        return settingsRepository.save(thresholds);
    }

    private CompanyInsightThresholds defaultsFor(long companyId) {
        String currency = null;
        try {
            Company company = dbCompany.getCompanyById(Math.toIntExact(companyId));
            currency = company == null ? null : company.getCurrency();
        } catch (RuntimeException ignored) {
            // use built-in default currency
        }
        return CompanyInsightThresholds.defaults(companyId, currency, "Africa/Cairo");
    }
}
