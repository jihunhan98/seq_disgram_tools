package com.company.seqdiagram.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * Client for the in-house AI API.
     *
     * The JDK HTTP client is used rather than the simple factory because it
     * sends a buffered body with a Content-Length header; the simple factory
     * streams the request chunked, which OpenAI-compatible servers and the
     * proxies in front of them frequently reject.
     *
     * Generation can take a while, so the read timeout follows
     * app.ai.timeout-seconds rather than the framework default.
     */
    @Bean
    public RestClient aiRestClient(AiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
