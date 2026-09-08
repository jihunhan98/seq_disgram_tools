package com.company.seqdiagram.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.company.seqdiagram.config.AiProperties;
import com.company.seqdiagram.exception.AiServiceException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * AI 서버(ai-model, FastAPI) 호출 클라이언트.
 *
 * <p>사내 LLM API 와 직접 이야기하지 않는다. 그 호출은 AI 서버가 파이썬에서
 * 처리하고(OpenAI 호환 Chat Completions), 여기서는 다이어그램을 요청해 Mermaid
 * 코드만 받아 온다.
 *
 * <p>다이어그램을 만들 대체 수단이 없으므로 실패를 감추지 않는다. AI 서버가
 * 돌려준 이유를 그대로 담아 예외로 올려, 화면과 로그에서 확인할 수 있게 한다.
 */
@Service
public class MermaidAiService {

    private static final Logger log = LoggerFactory.getLogger(MermaidAiService.class);

    private final RestClient restClient;
    private final AiProperties properties;

    public MermaidAiService(RestClient aiRestClient, AiProperties properties) {
        this.restClient = aiRestClient;
        this.properties = properties;
    }

    /** 자연어 설명으로 새 다이어그램을 만든다. */
    public String generate(String requirement) {
        return call("/generate", Map.of("requirement", requirement));
    }

    /** 현재 코드에 자연어 수정 요청을 반영한다. */
    public String refine(String currentMermaid, String instruction) {
        return call("/refine", Map.of("mermaidCode", currentMermaid, "instruction", instruction));
    }

    /** AI 서버의 상태. 연결되지 않으면 null 을 돌려주고 호출자가 판단한다. */
    public JsonNode health() {
        if (!properties.isConfigured()) {
            return null;
        }
        try {
            return restClient.get().uri("/health").retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("AI 서버 상태 확인 실패: {}", e.getMessage());
            return null;
        }
    }

    private String call(String path, Map<String, Object> body) {
        if (!properties.isConfigured()) {
            throw new AiServiceException(
                    "AI 서버 주소가 설정되지 않았습니다. application.yml 의 app.ai.base-url 을 확인하세요.");
        }

        String url = properties.normalizedBaseUrl() + path;

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException e) {
            // FastAPI 는 실패 이유를 detail 에 담아 보낸다. 사내 LLM 이 돌려준
            // 오류 본문도 여기에 실려 오므로 그대로 올린다.
            String detail = detailOf(e.getResponseBodyAsString());
            log.error("AI 서버 {} — POST {}: {}", e.getStatusCode(), url, detail);
            throw new AiServiceException(detail, e);
        } catch (RestClientException e) {
            log.error("AI 서버 호출 실패 — POST {}", url, e);
            throw new AiServiceException(
                    "AI 서버(" + properties.normalizedBaseUrl() + ")에 연결하지 못했습니다. "
                            + "./run-ai.sh 로 실행 중인지 확인하세요.", e);
        }

        if (response == null || !response.hasNonNull("mermaidCode")) {
            throw new AiServiceException("AI 서버 응답에 mermaidCode 가 없습니다.");
        }
        return response.get("mermaidCode").asText();
    }

    /** FastAPI 의 {"detail": "..."} 에서 사람이 읽을 메시지를 꺼낸다. */
    private String detailOf(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "AI 서버가 오류를 반환했습니다.";
        }
        try {
            JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody);
            JsonNode detail = node.path("detail");
            if (detail.isTextual() && !detail.asText().isBlank()) {
                return detail.asText();
            }
        } catch (Exception e) {
            // JSON 이 아니면 본문을 그대로 보여준다.
        }
        return responseBody;
    }
}
