package com.company.seqdiagram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 서버(ai-model, FastAPI) 접속 설정.
 *
 * 사내 LLM API 주소·모델명·키는 여기 없다 — 그쪽은 AI 서버가 직접 들고 있고,
 * 커밋되지 않는 ai-model/.env 에서 읽는다.
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** ai-model 서버 주소. 예) http://localhost:5002 */
    private String baseUrl = "";
    /** 생성에 시간이 걸리므로 넉넉히 잡는다. */
    private int timeoutSeconds = 150;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    /** 뒤 슬래시를 떼어 경로를 그대로 이어붙일 수 있게 한다. */
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

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
