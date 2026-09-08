package com.company.seqdiagram.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.company.seqdiagram.config.AiProperties;
import com.company.seqdiagram.exception.AiServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Talks to the in-house Playground API through the OpenAI completions
 * contract - POST /v1/completions with a single `prompt` string, the
 * equivalent of the python SDK's
 * {@code client.completions.create(model=..., prompt=..., max_tokens=...)}.
 *
 * The answer comes back as {@code choices[0].text}. (The chat contract puts it
 * in {@code choices[0].message.content} instead, which is why a chat-shaped
 * reader sees an empty body here.)
 */
@Service
public class MermaidAiService {

    private static final Logger log = LoggerFactory.getLogger(MermaidAiService.class);

    /**
     * A completion model has no system role, so the rules are folded into the
     * prompt itself and the text ends on a cue the model can only continue
     * with the diagram.
     */
    private static final String RULES = """
            You are an expert software architect who writes Mermaid sequence diagrams.

            Rules you must always follow:
            1. Output Mermaid source code ONLY. No prose, no explanation, no markdown fences.
            2. The diagram must start with the line `sequenceDiagram`.
            3. Use `participant` (or `actor`) declarations for every party, in the order they
               first take part in the flow, and give each one a short readable alias.
            4. Use `->>` for requests, `-->>` for responses, and `-x` for failures.
            5. Express conditional and repeated behaviour with `alt` / `else` / `opt` / `loop`
               / `par` blocks, and close every block with `end`.
            6. Add `Note over ...` only where it genuinely clarifies the flow.
            7. Keep participant names and messages in the same language the request was written in.
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
        String prompt = """
                %s
                Write a Mermaid sequence diagram for the following requirement.

                Requirement:
                %s

                Mermaid code:
                """.formatted(RULES, requirement.strip());

        return complete(prompt);
    }

    /** Rewrites an existing diagram according to a natural-language change request. */
    public String refine(String currentMermaid, String instruction) {
        String prompt = """
                %s
                Below is an existing Mermaid sequence diagram, followed by a change request.
                Apply the change request and write out the COMPLETE updated diagram.
                Preserve every part of the original that the change request does not touch.

                Current diagram:
                %s

                Change request:
                %s

                Updated Mermaid code:
                """.formatted(RULES, currentMermaid.strip(), instruction.strip());

        return complete(prompt);
    }

    private String complete(String prompt) {
        if (!properties.isConfigured()) {
            throw new AiServiceException(
                    "AI API is not configured. Set app.ai.base-url in config/application-local.yml.");
        }

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "prompt", prompt,
                "max_tokens", properties.getMaxTokens());

        // Serialised up front so the request carries a Content-Length header.
        // Streaming the body would send it chunked, which OpenAI-compatible
        // servers and the proxies in front of them often reject.
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new AiServiceException("Could not build the AI request payload", e);
        }

        String url = properties.normalizedBaseUrl() + "/completions";
        log.debug("POST {} body={}", url, payload);

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException e) {
            // The server explains a 400 in its response body ("model does not
            // exist", "maximum context length is N tokens", ...). Surface it
            // instead of swallowing it, or there is nothing to debug from.
            String responseBody = e.getResponseBodyAsString();
            log.error("AI API {} for POST {}\n  request : {}\n  response: {}",
                    e.getStatusCode(), url, payload, responseBody);
            throw new AiServiceException(
                    "AI API가 " + e.getStatusCode() + " 를 반환했습니다: "
                            + (responseBody.isBlank() ? "(응답 본문 없음)" : responseBody), e);
        } catch (RestClientException e) {
            log.error("Call to the AI API failed: POST {}", url, e);
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

    /**
     * Reads the generated text. `choices[0].text` is the completions shape;
     * `choices[0].message.content` is accepted as well so that a server which
     * answers in the chat shape still works.
     */
    private String readContent(JsonNode response) {
        if (response == null) {
            throw new AiServiceException("The AI API returned an empty response.");
        }

        JsonNode choice = response.path("choices").path(0);
        String text = textOf(choice.path("text"));
        if (text == null) {
            text = textOf(choice.path("message").path("content"));
        }

        if (text == null) {
            log.warn("AI response had no usable text. Body: {}", response);
            throw new AiServiceException(
                    "The AI API response contained no text. Check that "
                            + properties.normalizedBaseUrl() + "/completions is the right endpoint.");
        }
        return text;
    }

    private static String textOf(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        return node.asText();
    }
}
