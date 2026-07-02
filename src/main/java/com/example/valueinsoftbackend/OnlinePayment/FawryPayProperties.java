package com.example.valueinsoftbackend.OnlinePayment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vls.fawrypay")
public class FawryPayProperties {

    private String baseUrl = "https://atfawry.fawrystaging.com";
    private String chargePath = "/ECommerceWeb/Fawry/payments/charge";
    private String merchantCode;
    private String secureHashKey;
    private String returnUrl;
    private String webhookUrl;
    private String language = "en-gb";
    private String paymentMethod;
    private boolean authCaptureModePayment = false;
    private int paymentExpiryMinutes = 1440;
    private String defaultCustomerMobile = "01000000000";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChargePath() {
        return chargePath;
    }

    public void setChargePath(String chargePath) {
        this.chargePath = chargePath;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public void setMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
    }

    public String getSecureHashKey() {
        return secureHashKey;
    }

    public void setSecureHashKey(String secureHashKey) {
        this.secureHashKey = secureHashKey;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public boolean isAuthCaptureModePayment() {
        return authCaptureModePayment;
    }

    public void setAuthCaptureModePayment(boolean authCaptureModePayment) {
        this.authCaptureModePayment = authCaptureModePayment;
    }

    public int getPaymentExpiryMinutes() {
        return paymentExpiryMinutes;
    }

    public void setPaymentExpiryMinutes(int paymentExpiryMinutes) {
        this.paymentExpiryMinutes = paymentExpiryMinutes;
    }

    public String getDefaultCustomerMobile() {
        return defaultCustomerMobile;
    }

    public void setDefaultCustomerMobile(String defaultCustomerMobile) {
        this.defaultCustomerMobile = defaultCustomerMobile;
    }
}
