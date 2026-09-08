package com.company.seqdiagram.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.seqdiagram.domain.Member;
import com.company.seqdiagram.dto.AuthResponse;
import com.company.seqdiagram.dto.LoginRequest;
import com.company.seqdiagram.dto.SignupRequest;
import com.company.seqdiagram.exception.ConflictException;
import com.company.seqdiagram.exception.UnauthorizedException;
import com.company.seqdiagram.repository.MemberRepository;
import com.company.seqdiagram.repository.SessionRepository;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository memberRepository;
    private final SessionRepository sessionRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final int sessionHours;

    public AuthService(MemberRepository memberRepository,
                       SessionRepository sessionRepository,
                       @Value("${app.auth.session-hours:12}") int sessionHours) {
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
        this.sessionHours = sessionHours;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String username = request.username().strip();
        String employeeNo = request.employeeNo().strip();

        if (memberRepository.usernameExists(username)) {
            throw new ConflictException("이미 사용 중인 아이디입니다");
        }
        if (memberRepository.employeeNoExists(employeeNo)) {
            throw new ConflictException("이미 등록된 사번입니다");
        }

        long id = memberRepository.insert(
                username,
                passwordEncoder.encode(request.password()),
                request.name().strip(),
                employeeNo);

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Member " + id + " vanished after insert"));
        return new AuthResponse(issueToken(id), member);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = request.username().strip();

        Optional<String> hash = memberRepository.findPasswordHash(username);
        // Same message either way, so the response cannot be used to probe for
        // valid account names.
        if (hash.isEmpty() || !passwordEncoder.matches(request.password(), hash.get())) {
            throw new UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다");
        }

        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다"));

        sessionRepository.deleteExpired();
        return new AuthResponse(issueToken(member.id()), member);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionRepository.delete(hash(token));
        }
    }

    @Transactional(readOnly = true)
    public Optional<Member> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findMemberId(hash(token)).flatMap(memberRepository::findById);
    }

    private String issueToken(long memberId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        sessionRepository.create(hash(token), memberId, sessionHours);
        return token;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
