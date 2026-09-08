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
 * Drives the client against a real socket standing in for the ai-model FastAPI
 * server, which is the only way to see what actually goes onto the wire.
 */
class MermaidAiServiceTest {

    private HttpServer server;
    private MermaidAiService service;

    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    /** When set, the stub answers with this status and body instead of a diagram. */
    private final AtomicReference<Integer> errorStatus = new AtomicReference<>(null);
    private final AtomicReference<String> errorBody = new AtomicReference<>("");
    private final AtomicReference<String> mermaidCode =
            new AtomicReference<>("sequenceDiagram\n    A->>B: hi");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            int status = 200;
            String body;
            if ("/health".equals(lastPath.get())) {
                body = "{\"status\":\"ok\",\"llmApiConfigured\":true,\"llmApiModel\":\"gpt-4\"}";
            } else if (errorStatus.get() != null) {
                status = errorStatus.get();
                body = errorBody.get();
            } else {
                body = new ObjectMapper().writeValueAsString(
                        java.util.Map.of("mermaidCode", mermaidCode.get(), "elapsedMs", 12));
            }

            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();

        service = new MermaidAiService(
                new RestClientConfig().aiRestClient(propertiesFor(port())), propertiesFor(port()));
    }

    private int port() {
        return server.getAddress().getPort();
    }

    private AiProperties propertiesFor(int port) {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + port + "/");
        return properties;
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsTheRequirementToGenerate() throws Exception {
        service.generate("로그인 흐름을 그려줘");

        assertThat(lastPath.get()).isEqualTo("/generate");
        assertThat(lastContentType.get()).contains("application/json");

        var parsed = new ObjectMapper().readTree(lastBody.get());
        assertThat(parsed.get("requirement").asText()).isEqualTo("로그인 흐름을 그려줘");
        assertThat(parsed.fieldNames()).toIterable().containsExactly("requirement");
    }

    @Test
    void postsTheCurrentCodeAndInstructionToRefine() throws Exception {
        mermaidCode.set("sequenceDiagram\n    A->>B: hi\n    B->>C: audit");

        String result = service.refine("sequenceDiagram\n    A->>B: hi", "감사 로그 추가");

        assertThat(lastPath.get()).isEqualTo("/refine");
        var parsed = new ObjectMapper().readTree(lastBody.get());
        assertThat(parsed.get("mermaidCode").asText()).isEqualTo("sequenceDiagram\n    A->>B: hi");
        assertThat(parsed.get("instruction").asText()).isEqualTo("감사 로그 추가");
        assertThat(result).contains("audit");
    }

    @Test
    void returnsTheMermaidCodeFromTheResponse() {
        mermaidCode.set("sequenceDiagram\n    사용자->>API: 요청");

        assertThat(service.generate("x")).isEqualTo("sequenceDiagram\n    사용자->>API: 요청");
    }

    /**
     * The AI server puts the reason in `detail` - including whatever the
     * in-house LLM said about a 400. That text has to reach the caller,
     * otherwise a rejected request looks like any other failure.
     */
    @Test
    void surfacesTheDetailFromTheAiServer() {
        errorStatus.set(502);
        errorBody.set("{\"detail\":\"사내 LLM 이 400 를 반환했습니다: "
                + "{\\\"message\\\":\\\"The model `gpt-4` does not exist\\\"}\"}");

        assertThatThrownBy(() -> service.generate("로그인 흐름"))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("The model `gpt-4` does not exist");
    }

    @Test
    void reportsAnUnreachableAiServerWithTheCommandToStartIt() {
        AiProperties down = new AiProperties();
        down.setBaseUrl("http://127.0.0.1:1");
        MermaidAiService offline = new MermaidAiService(
                new RestClientConfig().aiRestClient(down), down);

        assertThatThrownBy(() -> offline.generate("x"))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("run-ai.sh");
    }

    @Test
    void failsFastWhenTheAiServerAddressIsNotSet() {
        AiProperties empty = new AiProperties();
        MermaidAiService unconfigured = new MermaidAiService(
                new RestClientConfig().aiRestClient(empty), empty);

        assertThatThrownBy(() -> unconfigured.generate("x"))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("app.ai.base-url");
    }

    @Test
    void healthPassesThroughTheAiServersReport() {
        var health = service.health();

        assertThat(health).isNotNull();
        assertThat(health.path("llmApiConfigured").asBoolean()).isTrue();
        assertThat(health.path("llmApiModel").asText()).isEqualTo("gpt-4");
    }

    @Test
    void trailingSlashesInTheBaseUrlAreTolerated() {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://host:5002///");

        assertThat(properties.normalizedBaseUrl()).isEqualTo("http://host:5002");
    }
}
