package com.company.seqdiagram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.company.seqdiagram.service.MermaidSanitizer;

class MermaidSanitizerTest {

    @Test
    void unwrapsFencedMermaidBlock() {
        String raw = """
                Here is your diagram:

                ```mermaid
                sequenceDiagram
                    participant A
                    A->>B: hello
                ```
                """;

        assertThat(MermaidSanitizer.extract(raw))
                .startsWith("sequenceDiagram")
                .contains("A->>B: hello")
                .doesNotContain("```");
    }

    @Test
    void dropsProseWhenThereIsNoFence() {
        String raw = """
                Sure, here you go.
                sequenceDiagram
                    A->>B: hello
                """;

        assertThat(MermaidSanitizer.extract(raw)).isEqualTo("""
                sequenceDiagram
                    A->>B: hello""");
    }

    @Test
    void keepsPlainDiagramUnchanged() {
        String raw = "sequenceDiagram\n    A->>B: hello";

        assertThat(MermaidSanitizer.extract(raw)).isEqualTo(raw);
    }

    @Test
    void handlesUnterminatedFence() {
        String raw = "```mermaid\nsequenceDiagram\n    A->>B: hi";

        assertThat(MermaidSanitizer.extract(raw)).isEqualTo("sequenceDiagram\n    A->>B: hi");
    }

    @Test
    void rejectsContentWithoutADiagram() {
        assertThat(MermaidSanitizer.looksLikeDiagram("I cannot help with that")).isFalse();
        assertThat(MermaidSanitizer.looksLikeDiagram("sequenceDiagram\nA->>B: x")).isTrue();
        assertThat(MermaidSanitizer.looksLikeDiagram("")).isFalse();
        assertThat(MermaidSanitizer.looksLikeDiagram(null)).isFalse();
    }
}
