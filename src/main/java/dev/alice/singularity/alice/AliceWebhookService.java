package dev.alice.singularity.alice;

import dev.alice.singularity.agent.TaskAgent;
import dev.alice.singularity.cli.AgentCliRunner;
import dev.alice.singularity.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AliceWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AliceWebhookService.class);

    /** Запас до лимита Диалогов 4.5с (сеть + JSON). */
    static final long LIST_SYNC_TIMEOUT_MS = 3000L;
    static final long OTHER_SYNC_TIMEOUT_MS = 3500L;

    static final String ACK_MUTATE = "Ставлю задачу";
    static final String ACK_LIST = "Смотрю задачи";
    static final String STILL_RUNNING = "Ещё делаю, спросите «готово?» чуть позже";
    static final String BUSY = "Ещё делаю предыдущую задачу. Спросите «готово?»";

    private final AliceAuth auth;
    private final AliceSessionStore sessions;
    private final AliceJobStore jobs;
    private final AliceSpeechMapper speechMapper;
    private final TaskAgent taskAgent;
    private final String greeting;
    private final ExecutorService executor;

    @Autowired
    public AliceWebhookService(
            AliceAuth auth,
            AliceSessionStore sessions,
            AliceJobStore jobs,
            AliceSpeechMapper speechMapper,
            TaskAgent taskAgent,
            AppProperties properties
    ) {
        this(
                auth,
                sessions,
                jobs,
                speechMapper,
                taskAgent,
                properties,
                Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "alice-bg");
                    t.setDaemon(true);
                    return t;
                })
        );
    }

    AliceWebhookService(
            AliceAuth auth,
            AliceSessionStore sessions,
            AliceJobStore jobs,
            AliceSpeechMapper speechMapper,
            TaskAgent taskAgent,
            AppProperties properties,
            ExecutorService executor
    ) {
        this.auth = auth;
        this.sessions = sessions;
        this.jobs = jobs;
        this.speechMapper = speechMapper;
        this.taskAgent = taskAgent;
        this.executor = executor;
        AppProperties.Alice alice = properties.alice();
        this.greeting = alice == null || alice.greeting() == null || alice.greeting().isBlank()
                ? "Поставьте Диме новую задачу"
                : alice.greeting().trim();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
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

        String sessionId = sessionId(request);
        if (isStop(utterance)) {
            jobs.clear(sessionId);
            sessions.clear(sessionId);
            return AliceDialogModels.response(request, "Хорошо, на связи.", true);
        }

        Optional<AliceJobStore.Job> active = jobs.get(sessionId);
        if (active.isPresent()) {
            return statusResponse(request, sessionId, active.get());
        }

        AliceIntent.Kind kind = AliceIntent.classify(utterance);
        String prompt = buildPrompt(sessionId, utterance);

        return switch (kind) {
            case MUTATE -> startBackground(request, sessionId, prompt, ACK_MUTATE);
            case LIST -> runList(request, sessionId, prompt);
            case OTHER -> runOtherSync(request, sessionId, prompt);
        };
    }

    private Map<String, Object> statusResponse(
            AliceDialogModels.AliceRequest request,
            String sessionId,
            AliceJobStore.Job job
    ) {
        return switch (job.status()) {
            case PENDING -> AliceDialogModels.response(request, STILL_RUNNING, false);
            case DONE -> {
                if (job.listedTasks() != null && !job.listedTasks().isEmpty()) {
                    sessions.putTasks(sessionId, job.listedTasks());
                }
                String text = job.speech() == null || job.speech().isBlank() ? "Готово." : job.speech();
                jobs.clear(sessionId);
                yield AliceDialogModels.response(request, text, false);
            }
            case FAILED -> {
                String text = job.speech() == null || job.speech().isBlank()
                        ? "Не получилось выполнить запрос."
                        : job.speech();
                jobs.clear(sessionId);
                yield AliceDialogModels.response(request, text, false);
            }
        };
    }

    private Map<String, Object> startBackground(
            AliceDialogModels.AliceRequest request,
            String sessionId,
            String prompt,
            String ack
    ) {
        if (!jobs.tryStart(sessionId)) {
            return AliceDialogModels.response(request, BUSY, false);
        }
        executor.execute(() -> runAgentJob(sessionId, prompt));
        return AliceDialogModels.response(request, ack, false);
    }

    private Map<String, Object> runList(
            AliceDialogModels.AliceRequest request,
            String sessionId,
            String prompt
    ) {
        Future<AliceSpeechMapper.MappedSpeech> future = executor.submit(() -> invokeAgent(prompt));
        try {
            AliceSpeechMapper.MappedSpeech mapped = future.get(LIST_SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            applyMapped(sessionId, mapped);
            return AliceDialogModels.response(request, mapped.text(), false);
        } catch (TimeoutException ex) {
            if (!jobs.tryStart(sessionId)) {
                future.cancel(true);
                return AliceDialogModels.response(request, BUSY, false);
            }
            executor.execute(() -> finishFutureJob(sessionId, future));
            return AliceDialogModels.response(request, ACK_LIST, false);
        } catch (Exception ex) {
            future.cancel(true);
            log.error("Alice list failed session={}", sessionId, ex);
            return AliceDialogModels.response(request, "Не успела обработать запрос. Повторите, пожалуйста.", false);
        }
    }

    private Map<String, Object> runOtherSync(
            AliceDialogModels.AliceRequest request,
            String sessionId,
            String prompt
    ) {
        Future<AliceSpeechMapper.MappedSpeech> future = executor.submit(() -> invokeAgent(prompt));
        try {
            AliceSpeechMapper.MappedSpeech mapped = future.get(OTHER_SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            applyMapped(sessionId, mapped);
            return AliceDialogModels.response(request, mapped.text(), false);
        } catch (TimeoutException ex) {
            future.cancel(true);
            return AliceDialogModels.response(
                    request,
                    "Не успела обработать запрос. Повторите, пожалуйста.",
                    false
            );
        } catch (Exception ex) {
            future.cancel(true);
            log.error("Alice other failed session={}", sessionId, ex);
            return AliceDialogModels.response(request, "Не успела обработать запрос. Повторите, пожалуйста.", false);
        }
    }

    private void runAgentJob(String sessionId, String prompt) {
        try {
            AliceSpeechMapper.MappedSpeech mapped = invokeAgent(prompt);
            jobs.complete(sessionId, mapped.text(), mapped.listedTasks());
            log.info("Alice bg done session={} speech={}", sessionId, mapped.text());
        } catch (Exception ex) {
            log.error("Alice bg failed session={}", sessionId, ex);
            jobs.fail(sessionId, "Не получилось выполнить запрос. Повторите.");
        }
    }

    private void finishFutureJob(String sessionId, Future<AliceSpeechMapper.MappedSpeech> future) {
        try {
            AliceSpeechMapper.MappedSpeech mapped = future.get();
            jobs.complete(sessionId, mapped.text(), mapped.listedTasks());
            log.info("Alice list-bg done session={} speech={}", sessionId, mapped.text());
        } catch (Exception ex) {
            log.error("Alice list-bg failed session={}", sessionId, ex);
            jobs.fail(sessionId, "Не получилось получить список. Повторите.");
        }
    }

    private AliceSpeechMapper.MappedSpeech invokeAgent(String prompt) {
        String contract = AgentCliRunner.sanitizeContract(taskAgent.chat(prompt));
        log.info("Alice agent contract={}", oneLine(contract));
        return speechMapper.toSpeech(contract);
    }

    private void applyMapped(String sessionId, AliceSpeechMapper.MappedSpeech mapped) {
        if (mapped.listedTasks() != null && !mapped.listedTasks().isEmpty()) {
            sessions.putTasks(sessionId, mapped.listedTasks());
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
