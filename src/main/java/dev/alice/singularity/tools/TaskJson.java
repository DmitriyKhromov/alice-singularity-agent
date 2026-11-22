package dev.alice.singularity.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class TaskJson {

    private TaskJson() {
    }

    static String compactTask(ObjectMapper json, String body) throws Exception {
        JsonNode task = json.readTree(body);
        ObjectNode item = json.createObjectNode();
        item.put("id", text(task, "id"));
        item.put("title", text(task, "title"));
        if (task.hasNonNull("start")) {
            item.put("start", task.get("start").asText());
        } else {
            item.putNull("start");
        }
        if (task.has("priority") && !task.get("priority").isNull()) {
            item.put("priority", task.get("priority").asInt());
        }
        return json.writeValueAsString(item);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
