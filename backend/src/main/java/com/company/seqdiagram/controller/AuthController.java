package com.company.seqdiagram.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.seqdiagram.config.AuthInterceptor;
import com.company.seqdiagram.config.CurrentMember;
import com.company.seqdiagram.domain.Member;
import com.company.seqdiagram.dto.AuthResponse;
import com.company.seqdiagram.dto.LoginRequest;
import com.company.seqdiagram.dto.SignupRequest;
import com.company.seqdiagram.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 아이디 / 비밀번호 / 이름 / 사번으로 가입하고 바로 로그인 상태가 된다. */
    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        authService.logout(AuthInterceptor.bearerToken(request));
    }

    /** Lets the frontend restore a session from a stored token on reload. */
    @GetMapping("/me")
    public Member me(@CurrentMember Member member) {
        return member;
    }
}
