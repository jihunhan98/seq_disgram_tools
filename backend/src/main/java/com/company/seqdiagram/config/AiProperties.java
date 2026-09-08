package com.company.seqdiagram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the in-house OpenAI-compatible Playground API.
 * The real values live in the git-ignored config/application-local.yml.
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Base URL ending in /v1, e.g. http://host:port/v1 */
    private String baseUrl = "";
    private String apiKey = "EMPTY";
    private String model = "gpt-4";
    private double temperature = 0.2;
    /** Upper bound on the generated diagram; a long diagram needs room. */
    private int maxTokens = 2000;
    private int timeoutSeconds = 120;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    /** Base URL without a trailing slash, so paths can be appended directly. */
    public String normalizedBaseUrl() {
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
