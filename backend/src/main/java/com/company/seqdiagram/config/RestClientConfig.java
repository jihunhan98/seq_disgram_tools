package com.company.seqdiagram.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * AI 서버(ai-model, FastAPI) 호출용 클라이언트.
     *
     * <p>RestClient 의 기본 요청 팩토리(JDK HttpClient)는 HTTP/1.1 요청에도
     * {@code Upgrade: h2c} 헤더를 붙이는데, uvicorn 이 이걸 웹소켓 업그레이드로
     * 오인해 422 를 반환한다. 그래서 {@link SimpleClientHttpRequestFactory} 를 쓴다.
     *
     * <p>생성에 시간이 걸리므로 읽기 타임아웃은 app.ai.timeout-seconds 를 따른다.
     */
    @Bean
    public RestClient aiRestClient(AiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        return RestClient.builder()
                .baseUrl(properties.normalizedBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
