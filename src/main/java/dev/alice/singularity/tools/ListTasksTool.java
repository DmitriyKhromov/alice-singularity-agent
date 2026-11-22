package dev.alice.singularity.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.alice.singularity.singularity.SingularityClient;
import dev.alice.singularity.singularity.SingularityException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ListTasksTool {

    private static final Logger log = LoggerFactory.getLogger(ListTasksTool.class);

    private final SingularityClient client;
    private final ObjectMapper json;

    public ListTasksTool(SingularityClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Tool(name = "listTasks", value = "Возвращает компактный список задач из SingularityApp (id, title, start). Для «на сегодня» передай startDateFrom и startDateTo.")
    public String listTasks(
            @P(value = "Максимум задач (по умолчанию 10)", required = false) Integer maxCount,
            @P(value = "Смещение для пагинации", required = false) Integer offset,
            @P(value = "Начало диапазона start (YYYY-MM-DD)", required = false) String startDateFrom,
            @P(value = "Конец диапазона start (YYYY-MM-DD)", required = false) String startDateTo,
            @P(value = "Включать архивные", required = false) Boolean includeArchived,
            @P(value = "Включать удалённые", required = false) Boolean includeRemoved
    ) {
        try {
            int limit = maxCount == null ? 10 : Math.min(maxCount, 20);
            String body = client.listTasks(limit, offset, startDateFrom, startDateTo, includeArchived, includeRemoved);
            String compact = compact(body, limit);
            System.out.println("[SingularityTool] tool=listTasks status=ok body=" + compact);
            log.info("[SingularityTool] tool=listTasks status=ok body={}", compact);
            return compact;
        } catch (SingularityException | IllegalArgumentException | IllegalStateException ex) {
            String error = ex.getMessage();
            System.out.println("[SingularityTool] tool=listTasks status=error body=" + error);
            log.warn("[SingularityTool] tool=listTasks status=error body={}", error);
            return "{\"error\":\"" + escape(error) + "\"}";
        } catch (Exception ex) {
            String error = ex.getMessage();
            System.out.println("[SingularityTool] tool=listTasks status=error body=" + error);
            log.warn("[SingularityTool] tool=listTasks status=error body={}", error);
            return "{\"error\":\"" + escape(error) + "\"}";
        }
    }

    private String compact(String body, int limit) throws Exception {
        JsonNode root = json.readTree(body);
        JsonNode tasksNode = root.path("tasks");
        if (!tasksNode.isArray()) {
            tasksNode = root.isArray() ? root : json.createArrayNode();
        }
        ArrayNode compactTasks = json.createArrayNode();
        int i = 0;
        for (JsonNode task : tasksNode) {
            if (i >= limit) {
                break;
            }
            ObjectNode item = json.createObjectNode();
            item.put("id", text(task, "id"));
            item.put("title", text(task, "title"));
            if (task.hasNonNull("start")) {
                item.put("start", task.get("start").asText());
            } else {
                item.putNull("start");
            }
            compactTasks.add(item);
            i++;
        }
        ObjectNode out = json.createObjectNode();
        out.put("count", compactTasks.size());
        out.set("tasks", compactTasks);
        return json.writeValueAsString(out);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
