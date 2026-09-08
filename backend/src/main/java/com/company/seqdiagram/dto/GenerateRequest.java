package com.company.seqdiagram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Natural-language description of the flow to draw. */
public record GenerateRequest(
        @NotBlank(message = "requirement is required")
        @Size(max = 8000, message = "requirement must be at most 8000 characters")
        String requirement) {
}
