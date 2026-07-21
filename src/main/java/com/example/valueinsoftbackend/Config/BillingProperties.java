package com.example.valueinsoftbackend.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "vls.billing")
@Slf4j
public class BillingProperties {

    private String paymentProvider = "paymob";
    private String mockCheckoutBaseUrl = "https://mock-billing.valueinsoft.local/checkout";
    private boolean renewalSchedulerEnabled = true;
    private String renewalSchedulerCron = "0 10 2 * * *";
    private int renewalLeadDays = 5;
    private boolean dunningSchedulerEnabled = true;
    private String dunningSchedulerCron = "0 40 2 * * *";
    private int dunningGraceDays = 3;
    private int dunningMaxAttempts = 3;
    private int manualRetryCooldownMinutes = 15;
    private int manualRetryMaxAttempts = 5;
    private int checkoutOutboxBatchSize = 10;
    private int checkoutOutboxMaxAttempts = 5;
    private int checkoutOutboxRetryDelaySeconds = 60;
    private boolean balanceFirstPaymentApisEnabled = true;

    @PostConstruct
    void initialize() {
        log.info(
                "Billing configured: provider={} renewalSchedulerEnabled={} dunningSchedulerEnabled={} balanceFirstPaymentApisEnabled={}",
                paymentProvider,
                renewalSchedulerEnabled,
                dunningSchedulerEnabled,
                balanceFirstPaymentApisEnabled
        );
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public void setPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
    }

    public String getMockCheckoutBaseUrl() {
        return mockCheckoutBaseUrl;
    }

    public void setMockCheckoutBaseUrl(String mockCheckoutBaseUrl) {
        this.mockCheckoutBaseUrl = mockCheckoutBaseUrl;
    }

    public boolean isRenewalSchedulerEnabled() {
        return renewalSchedulerEnabled;
    }

    public void setRenewalSchedulerEnabled(boolean renewalSchedulerEnabled) {
        this.renewalSchedulerEnabled = renewalSchedulerEnabled;
    }

    public String getRenewalSchedulerCron() {
        return renewalSchedulerCron;
    }

    public void setRenewalSchedulerCron(String renewalSchedulerCron) {
        this.renewalSchedulerCron = renewalSchedulerCron;
    }

    public int getRenewalLeadDays() {
        return renewalLeadDays;
    }

    public void setRenewalLeadDays(int renewalLeadDays) {
        this.renewalLeadDays = renewalLeadDays;
    }

    public boolean isDunningSchedulerEnabled() {
        return dunningSchedulerEnabled;
    }

    public void setDunningSchedulerEnabled(boolean dunningSchedulerEnabled) {
        this.dunningSchedulerEnabled = dunningSchedulerEnabled;
    }

    public String getDunningSchedulerCron() {
        return dunningSchedulerCron;
    }

    public void setDunningSchedulerCron(String dunningSchedulerCron) {
        this.dunningSchedulerCron = dunningSchedulerCron;
    }

    public int getDunningGraceDays() {
        return dunningGraceDays;
    }

    public void setDunningGraceDays(int dunningGraceDays) {
        this.dunningGraceDays = dunningGraceDays;
    }

    public int getDunningMaxAttempts() {
        return dunningMaxAttempts;
    }

    public void setDunningMaxAttempts(int dunningMaxAttempts) {
        this.dunningMaxAttempts = dunningMaxAttempts;
    }

    public int getManualRetryCooldownMinutes() {
        return manualRetryCooldownMinutes;
    }

    public void setManualRetryCooldownMinutes(int manualRetryCooldownMinutes) {
        this.manualRetryCooldownMinutes = manualRetryCooldownMinutes;
    }

    public int getManualRetryMaxAttempts() {
        return manualRetryMaxAttempts;
    }

    public void setManualRetryMaxAttempts(int manualRetryMaxAttempts) {
        this.manualRetryMaxAttempts = manualRetryMaxAttempts;
    }

    public int getCheckoutOutboxBatchSize() {
        return checkoutOutboxBatchSize;
    }

    public void setCheckoutOutboxBatchSize(int checkoutOutboxBatchSize) {
        this.checkoutOutboxBatchSize = checkoutOutboxBatchSize;
    }

    public int getCheckoutOutboxMaxAttempts() {
        return checkoutOutboxMaxAttempts;
    }

    public void setCheckoutOutboxMaxAttempts(int checkoutOutboxMaxAttempts) {
        this.checkoutOutboxMaxAttempts = checkoutOutboxMaxAttempts;
    }

    public int getCheckoutOutboxRetryDelaySeconds() {
        return checkoutOutboxRetryDelaySeconds;
    }

    public void setCheckoutOutboxRetryDelaySeconds(int checkoutOutboxRetryDelaySeconds) {
        this.checkoutOutboxRetryDelaySeconds = checkoutOutboxRetryDelaySeconds;
    }

    public boolean isBalanceFirstPaymentApisEnabled() {
        return balanceFirstPaymentApisEnabled;
    }

    public void setBalanceFirstPaymentApisEnabled(boolean balanceFirstPaymentApisEnabled) {
        this.balanceFirstPaymentApisEnabled = balanceFirstPaymentApisEnabled;
    }
}
