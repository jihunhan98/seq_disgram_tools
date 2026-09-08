package com.company.seqdiagram.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * Client for the in-house AI API.
     *
     * The request body is handed to RestClient as a Map and Jackson serialises
     * it. Buffering that output is what lets the request go out with a
     * Content-Length header instead of chunked, which is how the openai python
     * SDK sends it; without the wrapper the two differ on that one header.
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
                .requestFactory(new BufferingClientHttpRequestFactory(factory))
                .build();
    }
}
