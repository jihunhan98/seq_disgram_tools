package com.company.seqdiagram.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The frontend is a set of plain static files served from a separate port
 * (5001), so the API on 5000 has to allow cross-origin calls from it.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final AuthInterceptor authInterceptor;
    private final CurrentMemberArgumentResolver currentMemberArgumentResolver;

    public WebConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins,
                     AuthInterceptor authInterceptor,
                     CurrentMemberArgumentResolver currentMemberArgumentResolver) {
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
        this.authInterceptor = authInterceptor;
        this.currentMemberArgumentResolver = currentMemberArgumentResolver;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Everything under /api needs a session except signing up, logging in
        // and the health probe the login screen uses.
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/signup", "/api/auth/login", "/api/health");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentMemberArgumentResolver);
    }
}
