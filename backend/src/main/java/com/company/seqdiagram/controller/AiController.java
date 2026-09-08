package com.company.seqdiagram.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.seqdiagram.dto.GenerateRequest;
import com.company.seqdiagram.dto.MermaidResponse;
import com.company.seqdiagram.dto.RefineRequest;
import com.company.seqdiagram.service.MermaidAiService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final MermaidAiService aiService;

    public AiController(MermaidAiService aiService) {
        this.aiService = aiService;
    }

    /** Natural language in, a fresh Mermaid sequence diagram out. */
    @PostMapping("/generate")
    public MermaidResponse generate(@Valid @RequestBody GenerateRequest request) {
        return new MermaidResponse(aiService.generate(request.requirement()));
    }

    /** Existing Mermaid plus a natural-language change request, regenerated. */
    @PostMapping("/refine")
    public MermaidResponse refine(@Valid @RequestBody RefineRequest request) {
        return new MermaidResponse(aiService.refine(request.mermaidCode(), request.instruction()));
    }
}
