package dev.alice.singularity.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.alice.singularity.singularity.SingularityClient;
import dev.alice.singularity.singularity.SingularityException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UpdateTaskTool {

    private static final Logger log = LoggerFactory.getLogger(UpdateTaskTool.class);

    private final SingularityClient client;
    private final ObjectMapper json;

    public UpdateTaskTool(SingularityClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Tool(name = "updateTask", value = "Обновляет задачу SingularityApp по id (например T-...). start = YYYY-MM-DD. Не выдумывай id.")
    public String updateTask(
            @P("ID задачи, например T-xxxxxxxx") String id,
            @P(value = "Новое название", required = false) String title,
            @P(value = "Новая дата start YYYY-MM-DD", required = false) String start,
            @P(value = "Заметка", required = false) String note,
            @P(value = "Приоритет: 0/1/2", required = false) Integer priority,
            @P(value = "Дата архивации YYYY-MM-DD", required = false) String journalDate,
            @P(value = "Дата удаления в корзину YYYY-MM-DD", required = false) String deleteDate
    ) {
        try {
            String body = client.updateTask(id, title, start, note, priority, journalDate, deleteDate);
            String compact = TaskJson.compactTask(json, body);
            System.out.println("[SingularityTool] tool=updateTask status=ok body=" + compact);
            log.info("[SingularityTool] tool=updateTask status=ok body={}", compact);
            return compact;
        } catch (SingularityException | IllegalArgumentException | IllegalStateException ex) {
            String error = ex.getMessage();
            System.out.println("[SingularityTool] tool=updateTask status=error body=" + error);
            log.warn("[SingularityTool] tool=updateTask status=error body={}", error);
            return "{\"error\":\"" + escape(error) + "\"}";
        } catch (Exception ex) {
            String error = ex.getMessage();
            System.out.println("[SingularityTool] tool=updateTask status=error body=" + error);
            log.warn("[SingularityTool] tool=updateTask status=error body={}", error);
            return "{\"error\":\"" + escape(error) + "\"}";
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
