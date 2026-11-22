package dev.alice.singularity.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentCliRunnerSanitizeTest {

    @Test
    void replacesSuperquotePlaceholders() {
        String raw = """
                Status: success
                Action: create task
                Data: {<|superquote|>id<|superquote|>: <|superquote|>T-1<|superquote|>}
                Errors: null
                """;
        String cleaned = AgentCliRunner.sanitizeContract(raw);
        assertFalse(cleaned.contains("<|superquote|>"));
        assertEquals("""
                Status: success
                Action: create task
                Data: {"id": "T-1"}
                Errors: null
                """.replace("\n", System.lineSeparator()).trim(), cleaned.replace("\n", System.lineSeparator()));
    }

    @Test
    void nullAnswerBecomesEmpty() {
        assertEquals("", AgentCliRunner.sanitizeContract(null));
    }
}
