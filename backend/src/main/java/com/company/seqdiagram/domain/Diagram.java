package com.company.seqdiagram.domain;

import java.time.OffsetDateTime;

/**
 * A stored sequence diagram. Updates overwrite the row in place - previous
 * revisions are intentionally not kept.
 */
public record Diagram(
        Long id,
        String title,
        String description,
        String mermaidCode,
        String lastPrompt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
