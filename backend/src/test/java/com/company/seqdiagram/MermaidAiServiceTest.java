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
    private final AtomicReference<String> lastContentLength = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> responseContent = new AtomicReference<>();
    /** false = chat shape (choices[0].message.content) instead of the completions shape. */
    private final AtomicReference<Boolean> answerInCompletionShape = new AtomicReference<>(true);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastContentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

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
     * Regression guard: a streamed request body would be sent chunked with no
     * Content-Length, which OpenAI-compatible servers and corporate proxies
     * routinely reject.
     */
    @Test
    void sendsABufferedBodyWithContentLength() {
        responseContent.set("sequenceDiagram\n    A->>B: hi");

        service.generate("로그인 흐름");

        assertThat(lastContentLength.get()).isNotNull();
        assertThat(Integer.parseInt(lastContentLength.get()))
                .isEqualTo(lastBody.get().getBytes(StandardCharsets.UTF_8).length);
        assertThat(lastAuthorization.get()).isEqualTo("Bearer EMPTY");
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
                .contains("\"max_tokens\":2000")
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
