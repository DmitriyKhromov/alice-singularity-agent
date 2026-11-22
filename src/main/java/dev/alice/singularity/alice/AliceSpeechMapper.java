package dev.alice.singularity.alice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AliceSpeechMapper {

    private static final Pattern STATUS = Pattern.compile("(?im)^Status:\\s*(.+)$");
    private static final Pattern ACTION = Pattern.compile("(?im)^Action:\\s*(.+)$");
    private static final Pattern DATA = Pattern.compile("(?ims)^Data:\\s*(.+?)(?=^Errors:|\\z)");
    private static final Pattern ERRORS = Pattern.compile("(?im)^Errors:\\s*(.+)$");

    private final ObjectMapper json;

    public AliceSpeechMapper(ObjectMapper json) {
        this.json = json;
    }

    public record MappedSpeech(String text, List<AliceSessionStore.TaskRef> listedTasks) {
    }

    public MappedSpeech toSpeech(String contract) {
        String sanitized = contract == null ? "" : contract.trim();
        String status = first(STATUS, sanitized);
        String action = first(ACTION, sanitized);
        String dataRaw = first(DATA, sanitized);
        String errors = first(ERRORS, sanitized);

        boolean success = status != null && status.trim().equalsIgnoreCase("success");
        if (!success) {
            String err = cleanErrors(errors);
            if (err != null) {
                return new MappedSpeech(err, List.of());
            }
            return new MappedSpeech("Не получилось выполнить запрос.", List.of());
        }

        JsonNode data = parseJson(dataRaw);
        String actionLower = action == null ? "" : action.toLowerCase(Locale.ROOT);

        if (data != null && data.has("tasks")) {
            return mapList(data);
        }
        if (data != null && data.has("title")) {
            return mapTaskMutation(data, actionLower);
        }
        if (data != null && !data.isNull()) {
            return new MappedSpeech("Готово.", List.of());
        }
        return new MappedSpeech("Готово.", List.of());
    }

    private MappedSpeech mapList(JsonNode data) {
        int count = data.path("count").asInt(data.path("tasks").size());
        JsonNode tasks = data.path("tasks");
        List<AliceSessionStore.TaskRef> refs = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        if (tasks.isArray()) {
            for (JsonNode t : tasks) {
                String id = text(t, "id");
                String title = text(t, "title");
                if (id != null || title != null) {
                    refs.add(new AliceSessionStore.TaskRef(
                            id == null ? "" : id,
                            title == null ? "без названия" : title
                    ));
                }
                if (titles.size() < 3 && title != null && !title.isBlank()) {
                    titles.add(title.trim());
                }
            }
        }
        if (count <= 0) {
            return new MappedSpeech("Задач пока нет.", refs);
        }
        if (titles.isEmpty()) {
            return new MappedSpeech(count + " " + tasksWord(count) + ".", refs);
        }
        String joined = String.join(", ", titles);
        String text = count + " " + tasksWord(count) + ", первые "
                + titles.size() + ": " + joined + ".";
        return new MappedSpeech(text, refs);
    }

    private MappedSpeech mapTaskMutation(JsonNode data, String actionLower) {
        String title = text(data, "title");
        String start = text(data, "start");
        if (title == null || title.isBlank()) {
            return new MappedSpeech("Готово.", List.of());
        }
        if (actionLower.contains("rename") || actionLower.contains("переимен")) {
            return new MappedSpeech("Переименовала в «" + title + "».", List.of());
        }
        if (start != null && !start.isBlank()) {
            return new MappedSpeech("Поставила «" + title + "» на " + shortDate(start) + ".", List.of());
        }
        return new MappedSpeech("Создала задачу «" + title + "».", List.of());
    }

    private JsonNode parseJson(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("null")) {
            return null;
        }
        try {
            return json.readTree(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String first(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return null;
        }
        return m.group(1).trim();
    }

    private static String cleanErrors(String errors) {
        if (errors == null) {
            return null;
        }
        String value = errors.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("null")) {
            return null;
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText(null);
        return s == null || s.isBlank() ? null : s;
    }

    private static String shortDate(String start) {
        if (start.length() >= 10 && start.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}")) {
            return start.substring(0, 10);
        }
        return start;
    }

    private static String tasksWord(int count) {
        int n = Math.abs(count) % 100;
        int n1 = n % 10;
        if (n > 10 && n < 20) {
            return "задач";
        }
        if (n1 == 1) {
            return "задача";
        }
        if (n1 >= 2 && n1 <= 4) {
            return "задачи";
        }
        return "задач";
    }
}
