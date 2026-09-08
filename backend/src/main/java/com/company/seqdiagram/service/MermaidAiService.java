package com.company.seqdiagram.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.company.seqdiagram.config.AiProperties;
import com.company.seqdiagram.exception.AiServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Talks to the in-house Playground API, which exposes the OpenAI
 * /v1/chat/completions contract.
 */
@Service
public class MermaidAiService {

    private static final Logger log = LoggerFactory.getLogger(MermaidAiService.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert software architect who writes Mermaid sequence diagrams.

            Rules you must always follow:
            1. Reply with Mermaid source code ONLY. No prose, no explanation, no markdown fences.
            2. The diagram must start with the line `sequenceDiagram`.
            3. Use `participant` (or `actor`) declarations for every party, in the order they
               first take part in the flow, and give each one a short readable alias.
            4. Use `->>` for requests, `-->>` for responses, and `-x` for failures.
            5. Express conditional and repeated behaviour with `alt` / `else` / `opt` / `loop`
               / `par` blocks, and close every block with `end`.
            6. Add `Note over ...` only where it genuinely clarifies the flow.
            7. Keep participant names and messages in the same language the user wrote in.
            8. The output must be valid Mermaid v11 syntax that renders without errors.
            """;

    private final RestClient restClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public MermaidAiService(RestClient aiRestClient, AiProperties properties, ObjectMapper objectMapper) {
        this.restClient = aiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Builds a brand new diagram from a natural-language description. */
    public String generate(String requirement) {
        String userPrompt = """
                Draw a Mermaid sequence diagram for the following requirement.

                Requirement:
                %s
                """.formatted(requirement.strip());

        return complete(userPrompt);
    }

    /** Rewrites an existing diagram according to a natural-language change request. */
    public String refine(String currentMermaid, String instruction) {
        String userPrompt = """
                Below is an existing Mermaid sequence diagram, followed by a change request.
                Apply the change request and return the COMPLETE updated diagram.
                Preserve every part of the original that the change request does not touch.

                Current diagram:
                %s

                Change request:
                %s
                """.formatted(currentMermaid.strip(), instruction.strip());

        return complete(userPrompt);
    }

    private String complete(String userPrompt) {
        if (!properties.isConfigured()) {
            throw new AiServiceException(
                    "AI API is not configured. Set app.ai.base-url in config/application-local.yml.");
        }

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "temperature", properties.getTemperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)));

        // Serialised up front so the request carries a Content-Length header.
        // Streaming the body would send it chunked, which OpenAI-compatible
        // servers and the proxies in front of them often reject.
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new AiServiceException("Could not build the AI request payload", e);
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(properties.normalizedBaseUrl() + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Call to the AI API failed", e);
            throw new AiServiceException("Could not reach the AI API: " + e.getMessage(), e);
        }

        String content = readContent(response);
        String mermaid = MermaidSanitizer.extract(content);

        if (!MermaidSanitizer.looksLikeDiagram(mermaid)) {
            log.warn("AI response did not contain a diagram. Raw content: {}", content);
            throw new AiServiceException(
                    "The AI API did not return Mermaid code. Try rephrasing the request.");
        }

        return mermaid;
    }

    private String readContent(JsonNode response) {
        if (response == null) {
            throw new AiServiceException("The AI API returned an empty response.");
        }

        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
            throw new AiServiceException("The AI API response contained no message content.");
        }

        return content.asText();
    }
}
