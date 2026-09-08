package com.company.seqdiagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM SEQ_DIAGRAM");
    }

    @Test
    void createsReadsOverwritesAndDeletes() throws Exception {
        String created = mockMvc.perform(post("/api/diagrams")
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

        mockMvc.perform(get("/api/diagrams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].title").value("결제 승인"))
                // Summaries must not carry the code payload.
                .andExpect(jsonPath("$[0].mermaidCode").doesNotExist());

        mockMvc.perform(get("/api/diagrams/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mermaidCode").value(CODE));

        // An update overwrites in place - no revision is kept.
        mockMvc.perform(put("/api/diagrams/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"결제 승인 v2","mermaidCode":"sequenceDiagram\\n    A->>B: x"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("결제 승인 v2"))
                .andExpect(jsonPath("$.mermaidCode").value("sequenceDiagram\n    A->>B: x"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SEQ_DIAGRAM", Integer.class)).isEqualTo(1);

        mockMvc.perform(delete("/api/diagrams/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/diagrams/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void storesCodeLargerThanTheVarcharLimit() throws Exception {
        String big = "sequenceDiagram\n" + "    A->>B: message\n".repeat(3000);

        String created = mockMvc.perform(post("/api/diagrams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"대형","mermaidCode":"%s"}
                                """.formatted(big.replace("\n", "\\n"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = Long.parseLong(created.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        mockMvc.perform(get("/api/diagrams/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mermaidCode").value(big));
    }

    @Test
    void rejectsMissingTitle() throws Exception {
        mockMvc.perform(post("/api/diagrams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"mermaidCode\":\"sequenceDiagram\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("title")));
    }

    @Test
    void updateOfUnknownIdIsNotFound() throws Exception {
        mockMvc.perform(put("/api/diagrams/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"mermaidCode\":\"sequenceDiagram\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void generateReturnsMermaidFromTheAiService() throws Exception {
        given(aiService.generate(anyString())).willReturn(CODE);

        mockMvc.perform(post("/api/ai/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"로그인 흐름을 그려줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mermaidCode").value(CODE));
    }

    @Test
    void refinePassesCurrentCodeAndInstruction() throws Exception {
        given(aiService.refine(anyString(), anyString())).willReturn(CODE);

        mockMvc.perform(post("/api/ai/refine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mermaidCode\":\"sequenceDiagram\",\"instruction\":\"실패 흐름 추가\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mermaidCode").value(CODE));
    }

    @Test
    void aiFailureBecomesBadGateway() throws Exception {
        willThrow(new AiServiceException("AI down")).given(aiService).generate(anyString());

        mockMvc.perform(post("/api/ai/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"x\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("AI down"));
    }

    @Test
    void healthReportsAiConfiguration() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.aiConfigured").value(true));
    }
}
