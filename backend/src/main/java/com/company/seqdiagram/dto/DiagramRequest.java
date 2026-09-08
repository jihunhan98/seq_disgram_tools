package com.company.seqdiagram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload used both for creating and for overwriting a diagram. */
public record DiagramRequest(
        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @NotBlank(message = "mermaidCode is required")
        String mermaidCode,

        @Size(max = 4000, message = "lastPrompt must be at most 4000 characters")
        String lastPrompt) {
}
