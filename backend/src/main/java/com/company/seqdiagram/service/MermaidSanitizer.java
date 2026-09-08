package com.company.seqdiagram.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Models answer with prose and fenced code blocks more often than not. This
 * pulls the Mermaid source back out so the editor only ever receives code.
 */
public final class MermaidSanitizer {

    private static final List<String> DIAGRAM_KEYWORDS = List.of(
            "sequenceDiagram", "flowchart", "graph ", "classDiagram", "stateDiagram",
            "erDiagram", "journey", "gantt", "pie", "mindmap", "timeline");

    private MermaidSanitizer() {
    }

    public static String extract(String rawContent) {
        if (rawContent == null) {
            return "";
        }

        String content = rawContent.strip();
        String fenced = firstFencedBlock(content);
        if (fenced != null) {
            content = fenced;
        }

        return dropLeadingProse(content).strip();
    }

    public static boolean looksLikeDiagram(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return DIAGRAM_KEYWORDS.stream().anyMatch(code::contains);
    }

    /** Returns the body of the first ``` fenced block, or null when there is none. */
    private static String firstFencedBlock(String content) {
        int open = content.indexOf("```");
        if (open < 0) {
            return null;
        }

        int bodyStart = content.indexOf('\n', open);
        if (bodyStart < 0) {
            return null;
        }

        int close = content.indexOf("```", bodyStart);
        if (close < 0) {
            // Unterminated fence: take everything after the opener.
            return content.substring(bodyStart + 1);
        }
        return content.substring(bodyStart + 1, close);
    }

    /**
     * Drops any explanatory lines that precede the first diagram declaration,
     * for the case where the model answered without a code fence.
     */
    private static String dropLeadingProse(String content) {
        String[] lines = content.split("\n", -1);
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (DIAGRAM_KEYWORDS.stream().anyMatch(trimmed::startsWith)) {
                start = i;
                break;
            }
        }
        if (start <= 0) {
            return content;
        }

        List<String> kept = new ArrayList<>(lines.length - start);
        for (int i = start; i < lines.length; i++) {
            kept.add(lines[i]);
        }
        return String.join("\n", kept);
    }
}
