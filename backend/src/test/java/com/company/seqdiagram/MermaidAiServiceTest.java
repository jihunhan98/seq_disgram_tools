package com.company.seqdiagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.company.seqdiagram.config.AiProperties;
import com.company.seqdiagram.config.RestClientConfig;
import com.company.seqdiagram.exception.AiServiceException;
import com.company.seqdiagram.service.MermaidAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises the AI client against a real socket, which is the only way to see
 * what actually goes onto the wire.
 */
class MermaidAiServiceTest {

    private HttpServer server;
    private MermaidAiService service;

    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    private final AtomicReference<String> lastContentLength = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastAccept = new AtomicReference<>();
    private final AtomicReference<String> responseContent = new AtomicReference<>();
    /** false = chat shape (choices[0].message.content) instead of the completions shape. */
    private final AtomicReference<Boolean> answerInCompletionShape = new AtomicReference<>(true);
    /** When set, the stub answers with this status and body instead of a diagram. */
    private final AtomicReference<int[]> errorStatus = new AtomicReference<>(null);
    private final AtomicReference<String> errorBody = new AtomicReference<>("");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            lastContentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            if (errorStatus.get() != null) {
                byte[] error = errorBody.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(errorStatus.get()[0], error.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(error);
                }
                return;
            }

            Object choice = answerInCompletionShape.get()
                    ? java.util.Map.of("text", responseContent.get())
                    : java.util.Map.of("message", java.util.Map.of("content", responseContent.get()));
            String body = new ObjectMapper().writeValueAsString(
                    java.util.Map.of("choices", java.util.List.of(choice)));
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();

        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        properties.setApiKey("EMPTY");
        properties.setModel("gpt-4");

        service = new MermaidAiService(
                new RestClientConfig().aiRestClient(properties), properties, new ObjectMapper());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /**
     * The body is handed over as a Map, so Jackson has to produce well-formed
     * JSON and the JSON content type has to survive to the wire.
     */
    @Test
    void sendsJsonWithTheHeadersThePythonSdkSends() throws Exception {
        responseContent.set("sequenceDiagram\n    A->>B: hi");

        service.generate("로그인 흐름");

        assertThat(lastContentType.get()).contains("application/json");
        assertThat(lastAccept.get()).contains("application/json");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer EMPTY");
        // Content-Length rather than chunked, matching the python SDK.
        assertThat(lastContentLength.get()).isNotNull();

        // Parses as JSON with exactly the three expected fields.
        var parsed = new ObjectMapper().readTree(lastBody.get());
        assertThat(parsed.get("model").asText()).isEqualTo("gpt-4");
        assertThat(parsed.get("max_tokens").asInt()).isEqualTo(500);
        assertThat(parsed.get("prompt").asText()).contains("로그인 흐름");
        assertThat(parsed.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("model", "prompt", "max_tokens");
    }

    /**
     * The in-house API serves the completions contract, so the request has to
     * go to /completions with a single `prompt` string - the equivalent of
     * client.completions.create(model=..., prompt=..., max_tokens=...).
     */
    @Test
    void postsAPromptToTheCompletionsEndpoint() {
        responseContent.set("sequenceDiagram\n    A->>B: hi");

        service.generate("로그인 흐름을 그려줘");

        assertThat(lastPath.get()).isEqualTo("/v1/completions");
        assertThat(lastBody.get())
                .contains("\"model\":\"gpt-4\"")
                .contains("\"prompt\"")
                .contains("\"max_tokens\":500")
                .contains("로그인 흐름을 그려줘")
                // Only these three fields go on the wire: no chat-shaped
                // message array, and nothing the caller did not ask for.
                .doesNotContain("\"messages\"")
                .doesNotContain("\"temperature\"");
    }

    /**
     * Regression guard for "body가 비어있다": the completions contract returns
     * choices[0].text, and reading only choices[0].message.content sees nothing.
     */
    @Test
    void readsTheAnswerFromChoicesText() {
        responseContent.set("sequenceDiagram\n    A->>B: 요청");

        assertThat(service.generate("x")).isEqualTo("sequenceDiagram\n    A->>B: 요청");
    }

    /** A server that answers in the chat shape still works. */
    @Test
    void alsoReadsTheAnswerFromChoicesMessageContent() {
        answerInCompletionShape.set(false);
        responseContent.set("sequenceDiagram\n    A->>B: 요청");

        assertThat(service.generate("x")).isEqualTo("sequenceDiagram\n    A->>B: 요청");
    }

    @Test
    void refineCarriesTheCurrentDiagramAndTheInstruction() {
        responseContent.set("sequenceDiagram\n    A->>B: hi\n    B->>C: audit");

        String result = service.refine("sequenceDiagram\n    A->>B: hi", "감사 로그 추가");

        assertThat(lastBody.get())
                .contains("A->>B: hi")
                .contains("감사 로그 추가");
        assertThat(result).contains("audit");
    }

    @Test
    void unwrapsAFencedResponse() {
        responseContent.set("설명입니다.\n\n```mermaid\nsequenceDiagram\n    A->>B: hi\n```\n");

        assertThat(service.generate("x")).isEqualTo("sequenceDiagram\n    A->>B: hi");
    }

    /**
     * A 400 carries the reason in its body ("model does not exist", "maximum
     * context length is N tokens", ...). That text has to reach the caller,
     * otherwise a rejected request is indistinguishable from any other failure.
     */
    @Test
    void surfacesTheServersOwnExplanationForA400() {
        errorStatus.set(new int[] { 400 });
        errorBody.set("{\"object\":\"error\",\"message\":\"This model's maximum context "
                + "length is 2048 tokens, however you requested 2600 tokens\",\"type\":"
                + "\"invalid_request_error\"}");

        assertThatThrownBy(() -> service.generate("로그인 흐름"))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("maximum context length is 2048 tokens");
    }

    @Test
    void failsWhenTheModelAnswersWithoutADiagram() {
        responseContent.set("죄송하지만 도와드릴 수 없습니다.");

        assertThatThrownBy(() -> service.generate("x"))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("Mermaid");
    }

    @Test
    void failsFastWhenTheEndpointIsNotConfigured() {
        AiProperties empty = new AiProperties();
        MermaidAiService unconfigured = new MermaidAiService(
                new RestClientConfig().aiRestClient(empty), empty, new ObjectMapper());

        assertThatThrownBy(() -> unconfigured.generate("x"))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("app.ai.base-url");
    }

    @Test
    void trailingSlashesInTheBaseUrlAreTolerated() {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://host:6100/v1///");

        assertThat(properties.normalizedBaseUrl()).isEqualTo("http://host:6100/v1");
    }
}
