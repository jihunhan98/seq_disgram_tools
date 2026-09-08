package com.company.seqdiagram.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.seqdiagram.service.MermaidAiService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 로그인 화면과 헤더의 AI 상태 표시용. AI 서버가 살아 있는지, 그쪽에 사내 LLM
 * 주소가 채워져 있는지를 그대로 전달한다.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final MermaidAiService aiService;

    public HealthController(MermaidAiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");

        JsonNode aiHealth = aiService.health();
        if (aiHealth == null) {
            // AI 서버가 안 떠 있거나 주소가 비어 있는 경우.
            result.put("aiConfigured", false);
            result.put("aiModel", "");
            result.put("aiMessage", "AI 서버에 연결하지 못했습니다. ./run-ai.sh 로 실행하세요.");
        } else {
            result.put("aiConfigured", aiHealth.path("llmApiConfigured").asBoolean(false));
            result.put("aiModel", aiHealth.path("llmApiModel").asText(""));
        }
        return result;
    }
}
