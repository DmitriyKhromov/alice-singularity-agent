package dev.alice.singularity.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.alice.singularity.singularity.SingularityClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListTasksToolTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void returnsCompactTaskList() throws Exception {
        SingularityClient client = mock(SingularityClient.class);
        when(client.listTasks(anyInt(), nullable(Integer.class), nullable(String.class), nullable(String.class), eq(false), eq(false)))
                .thenReturn("""
                        {"tasks":[
                          {"id":"T-1","title":"A","start":"2026-11-23T00:00:00+03:00","note":"huge","priority":1},
                          {"id":"T-2","title":"B","start":null,"recurrence":{"x":1}}
                        ]}
                        """);

        ListTasksTool tool = new ListTasksTool(client, json);
        String compact = tool.listTasks(10, null, null, null, false, false);

        JsonNode node = json.readTree(compact);
        assertEquals(2, node.path("count").asInt());
        assertEquals("T-1", node.path("tasks").get(0).path("id").asText());
        assertEquals("A", node.path("tasks").get(0).path("title").asText());
        assertTrue(node.path("tasks").get(0).has("start"));
        assertTrue(!compact.contains("recurrence"));
        assertTrue(!compact.contains("huge"));
    }

    @Test
    void createAndUpdateCompactJson() throws Exception {
        SingularityClient client = mock(SingularityClient.class);
        when(client.createTask(any(), isNull(), anyInt(), isNull()))
                .thenReturn("""
                        {"id":"T-9","title":"молоко","start":null,"priority":1,"note":"","projectId":""}
                        """);
        when(client.updateTask(any(), any(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn("""
                        {"id":"T-9","title":"Купить хлеб","start":null,"priority":1,"projectId":""}
                        """);

        CreateTaskTool create = new CreateTaskTool(client, json);
        UpdateTaskTool update = new UpdateTaskTool(client, json);

        String created = create.createTask("молоко", null, 1, null);
        String updated = update.updateTask("T-9", "Купить хлеб", null, null, null, null, null);

        assertEquals("T-9", json.readTree(created).path("id").asText());
        assertEquals("молоко", json.readTree(created).path("title").asText());
        assertEquals("Купить хлеб", json.readTree(updated).path("title").asText());
        assertTrue(!created.contains("projectId"));
    }
}
