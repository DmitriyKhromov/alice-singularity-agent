package dev.alice.singularity.alice;

import dev.alice.singularity.AliceSingularityApplication;
import dev.alice.singularity.singularity.SingularityClient;
import dev.alice.singularity.support.EnvKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Живой async-сценарий Алисы: ack → статус → задача в Singularity.
 * Созданные задачи удаляются в {@link AfterAll}.
 */
class AliceAsyncWebhookIT {

    private static final String OWNER =
            "094E2C764EA423085EEEAB3212CD711F73AC33558D55EC41C291E541FD7241FD";

    private static final Set<String> createdTaskIds = new LinkedHashSet<>();
    private static ConfigurableApplicationContext context;
    private static AliceWebhookService webhook;
    private static AliceSessionStore sessions;
    private static SingularityClient singularityClient;

    @BeforeAll
    static void startApp() {
        EnvKeys.loadDotEnvIntoSystemProperties();
        assumeTrue(EnvKeys.hasLiveKeys(), "Skip Alice async IT: keys not set");

        System.setProperty("ALICE_ALLOWED_USER_IDS", OWNER);
        context = new SpringApplicationBuilder(AliceSingularityApplication.class)
                .profiles("test")
                .properties(
                        "app.cli.enabled=false",
                        "spring.main.web-application-type=none",
                        "spring.main.banner-mode=off",
                        "app.alice.allowed-user-ids=" + OWNER
                )
                .run();
        webhook = context.getBean(AliceWebhookService.class);
        sessions = context.getBean(AliceSessionStore.class);
        singularityClient = context.getBean(SingularityClient.class);
    }

    @AfterAll
    static void stopApp() {
        try {
            deleteCreatedTasks();
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    @Test
    void asyncCreateThenStatusThenCleanupIdsTracked() throws Exception {
        String sessionId = "async-it-" + UUID.randomUUID();
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String title = "тест асинхрон " + marker;

        Map<String, Object> ack = webhook.handle(request(sessionId, 1, "создай задачу " + title));
        assertEquals(AliceWebhookService.ACK_MUTATE, text(ack));

        String result = null;
        for (int i = 0; i < 60; i++) {
            Thread.sleep(500);
            Map<String, Object> status = webhook.handle(request(sessionId, 2 + i, "готово?"));
            String body = text(status);
            if (AliceWebhookService.STILL_RUNNING.equals(body)) {
                continue;
            }
            result = body;
            break;
        }
        assertTrue(result != null && !result.isBlank(), "expected status result, got: " + result);
        assertFalse(result.contains("обязательно"), "create failed: " + result);
        assertTrue(
                result.contains(marker) || result.contains("Создала") || result.contains("задач"),
                "expected create speech, got: " + result
        );

        AliceSessionStore.SessionState state = sessions.get(sessionId);
        assertTrue(state != null && state.lastTasks() != null && !state.lastTasks().isEmpty(),
                "expected task refs in session after DONE for cleanup");
        for (AliceSessionStore.TaskRef ref : state.lastTasks()) {
            if (ref.id() != null && ref.id().startsWith("T-")) {
                createdTaskIds.add(ref.id());
            }
        }
        assertFalse(createdTaskIds.isEmpty(), "no task id captured for cleanup");
    }

    private static void deleteCreatedTasks() {
        if (singularityClient == null || createdTaskIds.isEmpty()) {
            return;
        }
        String today = LocalDate.now().toString();
        for (String id : createdTaskIds) {
            try {
                singularityClient.updateTask(id, null, null, null, null, null, today);
                System.out.println("[AliceAsyncWebhookIT] deleted task " + id);
            } catch (Exception ex) {
                System.err.println("[AliceAsyncWebhookIT] failed to delete " + id + ": " + ex.getMessage());
            }
        }
    }

    private static AliceDialogModels.AliceRequest request(String sessionId, int messageId, String command) {
        return new AliceDialogModels.AliceRequest(
                "1.0",
                new AliceDialogModels.AliceRequest.Session(
                        false,
                        sessionId,
                        "skill",
                        messageId,
                        "EEAF",
                        new AliceDialogModels.AliceRequest.User(OWNER),
                        new AliceDialogModels.AliceRequest.Application("EEAF")
                ),
                new AliceDialogModels.AliceRequest.RequestBody(command, command, "SimpleUtterance", null, null),
                null,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private static String text(Map<String, Object> response) {
        return (String) ((Map<String, Object>) response.get("response")).get("text");
    }
}
