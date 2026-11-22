package dev.alice.singularity.alice;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AliceSessionStore {

    public record TaskRef(String id, String title) {
    }

    public record SessionState(List<TaskRef> lastTasks) {
    }

    private final Map<String, SessionState> bySession = new ConcurrentHashMap<>();

    public SessionState get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return bySession.get(sessionId);
    }

    public void putTasks(String sessionId, List<TaskRef> tasks) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        bySession.put(sessionId, new SessionState(List.copyOf(tasks == null ? List.of() : tasks)));
    }

    public void clear(String sessionId) {
        if (sessionId != null) {
            bySession.remove(sessionId);
        }
    }

    public String contextPrompt(String sessionId) {
        SessionState state = get(sessionId);
        if (state == null || state.lastTasks() == null || state.lastTasks().isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        List<TaskRef> tasks = state.lastTasks();
        for (int i = 0; i < tasks.size(); i++) {
            TaskRef t = tasks.get(i);
            lines.add((i + 1) + ") id=" + t.id() + " title=\"" + t.title() + "\"");
        }
        return "Последний список задач в этой сессии (для ссылок вроде «вторая», «первая»):\n"
                + String.join("\n", lines);
    }
}
