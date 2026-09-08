package com.company.seqdiagram.dto;

import java.time.OffsetDateTime;

import com.company.seqdiagram.domain.Diagram;

/** List-view projection: everything except the (potentially large) code. */
public record DiagramSummary(
        Long id,
        String title,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static DiagramSummary from(Diagram diagram) {
        return new DiagramSummary(
                diagram.id(),
                diagram.title(),
                diagram.description(),
                diagram.createdAt(),
                diagram.updatedAt());
    }
}
