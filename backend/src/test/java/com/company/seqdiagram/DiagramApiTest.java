package com.company.seqdiagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.company.seqdiagram.exception.AiServiceException;
import com.company.seqdiagram.service.MermaidAiService;

/**
 * Drives the REST API against H2 in Oracle-compatibility mode, so the SQL that
 * ships to production is the SQL under test. The AI API itself is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiagramApiTest {

    private static final String CODE = "sequenceDiagram\n    User->>API: 요청\n    API-->>User: 응답";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MermaidAiService aiService;

    private String token;

    @BeforeEach
    void signUpFreshMember() throws Exception {
        jdbcTemplate.update("DELETE FROM SEQ_DIAGRAM");
        jdbcTemplate.update("DELETE FROM MEMBER_SESSION");
        jdbcTemplate.update("DELETE FROM MEMBER");

        token = signup("hong", "hong@1234", "홍길동", "E1001");
    }

    private String signup(String username, String password, String name, String employeeNo)
            throws Exception {
        String body = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","name":"%s","employeeNo":"%s"}
                                """.formatted(username, password, name, employeeNo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    /** Every diagram call carries the caller's session token. */
    private MockHttpServletRequestBuilder as(String sessionToken, MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + sessionToken);
    }

    private long createDiagram(String sessionToken, String title, String code) throws Exception {
        String created = mockMvc.perform(as(sessionToken, post("/api/diagrams"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","mermaidCode":"%s"}
                                """.formatted(title, code.replace("\n", "\\n"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(created.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

    // ---------------------------------------------------------------- 회원 --

    @Test
    void signsUpAndLogsInWithTheSameCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hong\",\"password\":\"hong@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.member.username").value("hong"))
                .andExpect(jsonPath("$.member.name").value("홍길동"))
                .andExpect(jsonPath("$.member.employeeNo").value("E1001"))
                // The password must never travel back to the client.
                .andExpect(jsonPath("$.member.password").doesNotExist());
    }

    @Test
    void storesThePasswordHashedRatherThanInTheClear() {
        String stored = jdbcTemplate.queryForObject(
                "SELECT PASSWORD FROM MEMBER WHERE USERNAME = 'hong'", String.class);

        assertThat(stored).isNotEqualTo("hong@1234").startsWith("$2");
    }

    @Test
    void rejectsADuplicateUsernameAndADuplicateEmployeeNumber() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"hong","password":"other@1234","name":"다른","employeeNo":"E9999"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"other","password":"other@1234","name":"다른","employeeNo":"E1001"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 등록된 사번입니다"));
    }

    @Test
    void rejectsAWrongPasswordAndAnUnknownAccountAlike() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hong\",\"password\":\"wrong-one\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"hong@1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다"));
    }

    @Test
    void rejectsATooShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"shorty","password":"1234","name":"짧은","employeeNo":"E2"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("비밀번호는 8자 이상")));
    }

    @Test
    void meReturnsTheLoggedInMemberAndLogoutInvalidatesTheToken() throws Exception {
        mockMvc.perform(as(token, get("/api/auth/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("hong"));

        mockMvc.perform(as(token, post("/api/auth/logout"))).andExpect(status().isOk());

        mockMvc.perform(as(token, get("/api/auth/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCallsWithNoTokenOrAGarbageToken() throws Exception {
        mockMvc.perform(get("/api/diagrams")).andExpect(status().isUnauthorized());
        mockMvc.perform(as("not-a-real-token", get("/api/diagrams")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Regression guard: Spring keeps interceptors on CORS pre-flight requests
     * and browsers never send Authorization on them, so an interceptor that
     * demands a token here breaks every cross-origin call from the frontend.
     */
    @Test
    void corsPreflightForAnAuthenticatedEndpointSucceeds() throws Exception {
        mockMvc.perform(options("/api/diagrams")
                        .header("Origin", "http://localhost:5001")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "http://localhost:5001"));
    }

    @Test
    void healthStaysPublicAndReportsWhatTheAiServerSays() throws Exception {
        given(aiService.health()).willReturn(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"llmApiConfigured\":true,\"llmApiModel\":\"gpt-4\"}"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.aiConfigured").value(true))
                .andExpect(jsonPath("$.aiModel").value("gpt-4"));
    }

    /** AI 서버가 안 떠 있으면 화면이 그 사실과 실행 방법을 보여줘야 한다. */
    @Test
    void healthSaysHowToStartTheAiServerWhenItIsDown() throws Exception {
        given(aiService.health()).willReturn(null);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiConfigured").value(false))
                .andExpect(jsonPath("$.aiMessage").value(
                        org.hamcrest.Matchers.containsString("run-ai.sh")));
    }

    // ------------------------------------------------------- 회원별 다이어그램 --

    @Test
    void aMemberOnlySeesTheirOwnDiagrams() throws Exception {
        long mine = createDiagram(token, "내 다이어그램", CODE);

        String otherToken = signup("kimcs", "kim@12345", "김철수", "E1002");
        createDiagram(otherToken, "남의 다이어그램", CODE);

        mockMvc.perform(as(token, get("/api/diagrams")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("내 다이어그램"));

        mockMvc.perform(as(otherToken, get("/api/diagrams")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("남의 다이어그램"));

        // Another member's diagram is not readable, editable or deletable,
        // even with its id in hand.
        mockMvc.perform(as(otherToken, get("/api/diagrams/" + mine)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(otherToken, put("/api/diagrams/" + mine))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"탈취\",\"mermaidCode\":\"sequenceDiagram\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(otherToken, delete("/api/diagrams/" + mine)))
                .andExpect(status().isNotFound());

        // ...and it is untouched afterwards.
        mockMvc.perform(as(token, get("/api/diagrams/" + mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("내 다이어그램"));
    }

    @Test
    void createsReadsOverwritesAndDeletes() throws Exception {
        String created = mockMvc.perform(as(token, post("/api/diagrams"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"결제 승인","description":"리뷰용","mermaidCode":"%s","lastPrompt":"결제 흐름"}
                                """.formatted(CODE.replace("\n", "\\n"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("결제 승인"))
                .andExpect(jsonPath("$.mermaidCode").value(CODE))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        long id = Long.parseLong(created.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        mockMvc.perform(as(token, get("/api/diagrams")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].title").value("결제 승인"))
                // Summaries must not carry the code payload.
                .andExpect(jsonPath("$[0].mermaidCode").doesNotExist());

        mockMvc.perform(as(token, get("/api/diagrams/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mermaidCode").value(CODE));

        // An update overwrites in place - no revision is kept.
        mockMvc.perform(as(token, put("/api/diagrams/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"결제 승인 v2","mermaidCode":"sequenceDiagram\\n    A->>B: x"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("결제 승인 v2"))
                .andExpect(jsonPath("$.mermaidCode").value("sequenceDiagram\n    A->>B: x"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SEQ_DIAGRAM", Integer.class)).isEqualTo(1);

        mockMvc.perform(as(token, delete("/api/diagrams/" + id))).andExpect(status().isNoContent());
        mockMvc.perform(as(token, get("/api/diagrams/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void storesCodeLargerThanTheVarcharLimit() throws Exception {
        String big = "sequenceDiagram\n" + "    A->>B: message\n".repeat(3000);
        long id = createDiagram(token, "대형", big);

        mockMvc.perform(as(token, get("/api/diagrams/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mermaidCode").value(big));
    }

    @Test
    void rejectsMissingTitle() throws Exception {
        mockMvc.perform(as(token, post("/api/diagrams"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"mermaidCode\":\"sequenceDiagram\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("title")));
    }

    @Test
    void updateOfUnknownIdIsNotFound() throws Exception {
        mockMvc.perform(as(token, put("/api/diagrams/999999"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"mermaidCode\":\"sequenceDiagram\"}"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ AI --

    @Test
    void generateReturnsMermaidFromTheAiService() throws Exception {
        given(aiService.generate(anyString())).willReturn(CODE);

        mockMvc.perform(as(token, post("/api/ai/generate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"로그인 흐름을 그려줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mermaidCode").value(CODE));
    }

    @Test
    void refinePassesCurrentCodeAndInstruction() throws Exception {
        given(aiService.refine(anyString(), anyString())).willReturn(CODE);

        mockMvc.perform(as(token, post("/api/ai/refine"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mermaidCode\":\"sequenceDiagram\",\"instruction\":\"실패 흐름 추가\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mermaidCode").value(CODE));
    }

    @Test
    void aiFailureBecomesBadGateway() throws Exception {
        willThrow(new AiServiceException("AI down")).given(aiService).generate(anyString());

        mockMvc.perform(as(token, post("/api/ai/generate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"x\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("AI down"));
    }
}
