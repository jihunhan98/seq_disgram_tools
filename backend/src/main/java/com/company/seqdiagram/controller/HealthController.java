package com.company.seqdiagram.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.seqdiagram.config.AiProperties;

/** Lets the frontend show whether the AI API has been configured at all. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final AiProperties aiProperties;

    public HealthController(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "aiConfigured", aiProperties.isConfigured(),
                "aiModel", aiProperties.getModel());
    }
}
