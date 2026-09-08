package com.company.seqdiagram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Existing Mermaid code plus a natural-language change request. */
public record RefineRequest(
        @NotBlank(message = "mermaidCode is required")
        @Size(max = 40000, message = "mermaidCode is too large")
        String mermaidCode,

        @NotBlank(message = "instruction is required")
        @Size(max = 8000, message = "instruction must be at most 8000 characters")
        String instruction) {
}
