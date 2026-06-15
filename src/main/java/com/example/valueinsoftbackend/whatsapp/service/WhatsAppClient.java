package com.example.valueinsoftbackend.whatsapp.service;

import com.example.valueinsoftbackend.whatsapp.config.WhatsAppProperties;
import com.example.valueinsoftbackend.whatsapp.exception.WhatsAppException;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@Slf4j
public class WhatsAppClient {

    private final RestTemplate restTemplate;
    private final Gson gson;

    public WhatsAppClient(WhatsAppProperties properties, Gson gson) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getRequestTimeoutMs());
        factory.setReadTimeout(properties.getRequestTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
        this.gson = gson;
    }

    public WhatsAppResponse sendRequest(String graphApiVersion, String phoneNumberId, String accessToken, Object payload) {
        String url = String.format("https://graph.facebook.com/%s/%s/messages", graphApiVersion, phoneNumberId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        String jsonPayload = gson.toJson(payload);
        HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

        long startTime = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            long latencyMs = System.currentTimeMillis() - startTime;
            
            return new WhatsAppResponse(
                    true, 
                    response.getBody(), 
                    null, 
                    null, 
                    jsonPayload, // Sanitize payload in production if it contains sensitive data
                    latencyMs
            );
        } catch (HttpClientErrorException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            return handleClientError(e, jsonPayload, latencyMs);
        } catch (HttpServerErrorException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            throw new WhatsAppException(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR", "SERVER_ERROR", "Meta API server error: " + e.getMessage());
        } catch (RestClientException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            throw new WhatsAppException(HttpStatus.INTERNAL_SERVER_ERROR, "NETWORK_ERROR", "NETWORK_ERROR", "Network failure connecting to Meta API: " + e.getMessage());
        }
    }

    private WhatsAppResponse handleClientError(HttpClientErrorException e, String requestPayload, long latencyMs) {
        String errorBody = e.getResponseBodyAsString();
        String errorCategory = "INVALID_REQUEST";
        String errorCode = "UNKNOWN";
        String errorMessage = e.getMessage();

        try {
            Map<?, ?> errorMap = gson.fromJson(errorBody, Map.class);
            if (errorMap != null && errorMap.containsKey("error")) {
                Map<?, ?> errorDetails = (Map<?, ?>) errorMap.get("error");
                if (errorDetails.containsKey("message")) {
                    errorMessage = (String) errorDetails.get("message");
                }
                if (errorDetails.containsKey("code")) {
                    errorCode = String.valueOf(errorDetails.get("code"));
                }
            }
        } catch (Exception parseException) {
            log.warn("Failed to parse Meta API error response", parseException);
        }

        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            errorCategory = "INVALID_TOKEN";
        } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            errorCategory = "RATE_LIMITED";
        } else if (errorCode.equals("131009") || errorCode.equals("131026")) {
            errorCategory = "TEMPLATE_ERROR";
        }

        return new WhatsAppResponse(
                false, 
                errorBody, 
                errorCategory, 
                errorCode + ": " + errorMessage, 
                requestPayload, 
                latencyMs
        );
    }

    public static class WhatsAppResponse {
        public final boolean success;
        public final String responseBody;
        public final String errorCategory;
        public final String errorMessage;
        public final String requestPayload;
        public final long latencyMs;

        public WhatsAppResponse(boolean success, String responseBody, String errorCategory, String errorMessage, String requestPayload, long latencyMs) {
            this.success = success;
            this.responseBody = responseBody;
            this.errorCategory = errorCategory;
            this.errorMessage = errorMessage;
            this.requestPayload = requestPayload;
            this.latencyMs = latencyMs;
        }
    }
}
