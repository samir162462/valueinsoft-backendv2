package com.example.valueinsoftbackend.companyinsights.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyInsightPropertiesTest {

    @Test
    void recurringDatabaseJobsAreOptIn() {
        CompanyInsightProperties properties = new CompanyInsightProperties();

        assertTrue(properties.isEnabled());
        assertFalse(properties.isScheduledJobsEnabled());
    }
}
