package dev.alice.singularity.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.alice.singularity.singularity.SingularityClient;
import dev.alice.singularity.singularity.SingularityException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CreateTaskTool {

    private static final Logger log = LoggerFactory.getLogger(CreateTaskTool.class);

    private final SingularityClient client;
    private final ObjectMapper json;

    public CreateTaskTool(SingularityClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Tool(name = "createTask", value = "Создаёт задачу в SingularityApp. Если дата не указана пользователем — не передавай start. start = YYYY-MM-DD.")
    public String createTask(
            @P("Название задачи") String title,
            @P(value = "Дата начала в формате YYYY-MM-DD", required = false) String start,
            @P(value = "Приоритет: 0 высокий, 1 обычный, 2 низкий", required = false) Integer priority,
            @P(value = "Заметка / описание", required = false) String note
    ) {
        try {
            String body = client.createTask(title, start, priority == null ? 1 : priority, note);
            String compact = TaskJson.compactTask(json, body);
            // Debug output for homework REPORT (file:line referenced there)
            System.out.println("[SingularityTool] tool=createTask status=ok body=" + compact);
            log.info("[SingularityTool] tool=createTask status=ok body={}", compact);
            return compact;
        } catch (SingularityException | IllegalArgumentException | IllegalStateException ex) {
            String error = ex.getMessage();
            System.out.println("[SingularityTool] tool=createTask status=error body=" + error);
            log.warn("[SingularityTool] tool=createTask status=error body={}", error);
            return "{\"error\":\"" + escape(error) + "\"}";
        } catch (Exception ex) {
            String error = ex.getMessage();
            System.out.println("[SingularityTool] tool=createTask status=error body=" + error);
            log.warn("[SingularityTool] tool=createTask status=error body={}", error);
            return "{\"error\":\"" + escape(error) + "\"}";
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
