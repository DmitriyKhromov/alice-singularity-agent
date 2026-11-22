package dev.alice.singularity.alice;

import dev.alice.singularity.agent.TaskAgent;
import dev.alice.singularity.cli.AgentCliRunner;
import dev.alice.singularity.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class AliceWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AliceWebhookService.class);

    private final AliceAuth auth;
    private final AliceSessionStore sessions;
    private final AliceSpeechMapper speechMapper;
    private final TaskAgent taskAgent;
    private final String greeting;

    public AliceWebhookService(
            AliceAuth auth,
            AliceSessionStore sessions,
            AliceSpeechMapper speechMapper,
            TaskAgent taskAgent,
            AppProperties properties
    ) {
        this.auth = auth;
        this.sessions = sessions;
        this.speechMapper = speechMapper;
        this.taskAgent = taskAgent;
        AppProperties.Alice alice = properties.alice();
        this.greeting = alice == null || alice.greeting() == null || alice.greeting().isBlank()
                ? "Поставьте Диме новую задачу"
                : alice.greeting().trim();
    }

    public Map<String, Object> handle(AliceDialogModels.AliceRequest request) {
        if (!auth.isAllowed(request)) {
            log.warn("Alice rejected user_id={}", AliceAuth.accountUserId(request));
            return AliceDialogModels.response(request, "Этот навык доступен только владельцу.", false);
        }

        String utterance = utterance(request);
        boolean isNew = request.session() != null && Boolean.TRUE.equals(request.session().isNew());
        if (isNew || utterance.isBlank()) {
            return AliceDialogModels.response(request, greeting, false);
        }

        if (isStop(utterance)) {
            String sessionId = sessionId(request);
            sessions.clear(sessionId);
            return AliceDialogModels.response(request, "Хорошо, на связи.", true);
        }

        String sessionId = sessionId(request);
        String prompt = buildPrompt(sessionId, utterance);
        try {
            String contract = AgentCliRunner.sanitizeContract(taskAgent.chat(prompt));
            log.info("Alice agent contract session={} contract={}", sessionId, oneLine(contract));
            AliceSpeechMapper.MappedSpeech mapped = speechMapper.toSpeech(contract);
            if (mapped.listedTasks() != null && !mapped.listedTasks().isEmpty()) {
                sessions.putTasks(sessionId, mapped.listedTasks());
            }
            return AliceDialogModels.response(request, mapped.text(), false);
        } catch (Exception ex) {
            log.error("Alice agent failed session={}", sessionId, ex);
            return AliceDialogModels.response(
                    request,
                    "Не успела обработать запрос. Повторите, пожалуйста.",
                    false
            );
        }
    }

    private String buildPrompt(String sessionId, String utterance) {
        String context = sessions.contextPrompt(sessionId);
        if (context == null) {
            return utterance;
        }
        return context + "\n\nЗапрос пользователя: " + utterance;
    }

    private static String utterance(AliceDialogModels.AliceRequest request) {
        if (request.request() == null) {
            return "";
        }
        if (request.request().command() != null && !request.request().command().isBlank()) {
            return request.request().command().trim();
        }
        if (request.request().originalUtterance() != null) {
            return request.request().originalUtterance().trim();
        }
        return "";
    }

    private static String sessionId(AliceDialogModels.AliceRequest request) {
        return request.session() == null ? null : request.session().sessionId();
    }

    private static boolean isStop(String utterance) {
        String v = utterance.toLowerCase(Locale.ROOT);
        return v.equals("хватит") || v.equals("стоп") || v.equals("выход") || v.equals("достаточно");
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\n', ' ');
    }
}
