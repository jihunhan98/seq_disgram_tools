package com.company.seqdiagram.config;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.company.seqdiagram.domain.Member;
import com.company.seqdiagram.exception.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMember.class)
                && Member.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer container,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Object member = request == null ? null : request.getAttribute(AuthInterceptor.MEMBER_ATTRIBUTE);

        if (member instanceof Member resolved) {
            return resolved;
        }
        throw new UnauthorizedException("로그인이 필요합니다");
    }
}
