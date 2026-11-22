package dev.alice.singularity.alice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliceSpeechMapperTest {

    private final AliceSpeechMapper mapper = new AliceSpeechMapper(new ObjectMapper());

    @Test
    void mapsCreateSuccess() {
        var mapped = mapper.toSpeech("""
                Status: success
                Action: create task
                Data: {"id":"T-1","title":"купить молоко"}
                Errors: null
                """);
        assertEquals("Создала задачу «купить молоко».", mapped.text());
    }

    @Test
    void mapsListWithFirstThree() {
        var mapped = mapper.toSpeech("""
                Status: success
                Action: get tasks
                Data: {"count":5,"tasks":[{"id":"T-1","title":"A"},{"id":"T-2","title":"B"},{"id":"T-3","title":"C"},{"id":"T-4","title":"D"}]}
                Errors: null
                """);
        assertEquals("5 задач, первые 3: A, B, C.", mapped.text());
        assertEquals(4, mapped.listedTasks().size());
    }

    @Test
    void mapsError() {
        var mapped = mapper.toSpeech("""
                Status: error
                Action: none
                Data: null
                Errors: данный запрос вне границ моей компетенции
                """);
        assertTrue(mapped.text().contains("компетенции"));
    }
}
