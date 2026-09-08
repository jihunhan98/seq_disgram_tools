package com.company.seqdiagram.config;

import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import com.company.seqdiagram.domain.Member;
import com.company.seqdiagram.exception.UnauthorizedException;
import com.company.seqdiagram.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resolves `Authorization: Bearer <token>` into the calling member and rejects
 * requests that carry no valid session.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String MEMBER_ATTRIBUTE = "currentMember";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Spring keeps the interceptor chain on CORS pre-flight requests, and a
        // browser never sends Authorization on those. Let them through or every
        // cross-origin call from the frontend fails before it is even made.
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        Member member = authService.resolve(bearerToken(request))
                .orElseThrow(() -> new UnauthorizedException("로그인이 필요합니다"));

        request.setAttribute(MEMBER_ATTRIBUTE, member);
        return true;
    }

    public static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return header.substring(7).trim();
    }
}
