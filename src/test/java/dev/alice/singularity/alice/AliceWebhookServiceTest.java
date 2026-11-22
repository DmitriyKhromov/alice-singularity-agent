package dev.alice.singularity.alice;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.alice.singularity.agent.TaskAgent;
import dev.alice.singularity.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AliceWebhookServiceTest {

    private final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void greetingOnNewSessionWithoutCallingAgent() {
        TaskAgent agent = mock(TaskAgent.class);
        AliceWebhookService service = service(agent);

        Map<String, Object> response = service.handle(request(true, "sess", ""));
        assertEquals("Поставьте Диме новую задачу", text(response));
        assertFalse(endSession(response));
        verify(agent, never()).chat(anyString());
    }

    @Test
    void mutateAcksImmediatelyAndStatusReturnsResult() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TaskAgent agent = mock(TaskAgent.class);
        when(agent.chat(anyString())).thenAnswer(inv -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return """
                    Status: success
                    Action: create task
                    Data: {"id":"T-1","title":"молоко"}
                    Errors: null
                    """;
        });
        AliceWebhookService service = service(agent);

        Map<String, Object> ack = service.handle(request(false, "sess-m", "создай задачу молоко"));
        assertEquals(AliceWebhookService.ACK_MUTATE, text(ack));
        assertTrue(started.await(2, TimeUnit.SECONDS));

        Map<String, Object> pending = service.handle(request(false, "sess-m", "готово?"));
        assertEquals(AliceWebhookService.STILL_RUNNING, text(pending));

        release.countDown();
        // wait bg complete
        Thread.sleep(200);
        Map<String, Object> done = service.handle(request(false, "sess-m", "что с задачей"));
        assertTrue(text(done).contains("молоко"), text(done));
    }

    @Test
    void whilePendingDoesNotStartSecondJob() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        TaskAgent agent = mock(TaskAgent.class);
        when(agent.chat(anyString())).thenAnswer(inv -> {
            calls.incrementAndGet();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return """
                    Status: success
                    Action: create task
                    Data: {"id":"T-1","title":"a"}
                    Errors: null
                    """;
        });
        AliceWebhookService service = service(agent);

        assertEquals(AliceWebhookService.ACK_MUTATE,
                text(service.handle(request(false, "sess-b", "поставь пробежка"))));
        // second mutate while pending → status, not second ack
        assertEquals(AliceWebhookService.STILL_RUNNING,
                text(service.handle(request(false, "sess-b", "создай ещё одну"))));
        release.countDown();
        Thread.sleep(200);
        assertEquals(1, calls.get());
    }

    @Test
    void listFallsBackToBackgroundOnTimeout() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        TaskAgent agent = mock(TaskAgent.class);
        when(agent.chat(anyString())).thenAnswer(inv -> {
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return """
                    Status: success
                    Action: get tasks
                    Data: {"count":2,"tasks":[{"id":"T-1","title":"A"},{"id":"T-2","title":"B"}]}
                    Errors: null
                    """;
        });
        AliceWebhookService service = service(agent);

        Map<String, Object> ack = service.handle(request(false, "sess-l", "какие у меня задачи"));
        assertEquals(AliceWebhookService.ACK_LIST, text(ack));
        release.countDown();
        Thread.sleep(200);
        Map<String, Object> done = service.handle(request(false, "sess-l", "ну что"));
        assertTrue(text(done).contains("2"), text(done));
    }

    private AliceWebhookService service(TaskAgent agent) {
        AppProperties props = new AppProperties(
                "Europe/Moscow",
                new AppProperties.GigaChat("k", "GIGACHAT_API_PERS", "GigaChat-2-Max"),
                new AppProperties.Singularity("t", "https://api.singularity-app.com"),
                new AppProperties.Alice(
                        "094E2C764EA423085EEEAB3212CD711F73AC33558D55EC41C291E541FD7241FD",
                        "Поставьте Диме новую задачу"
                )
        );
        return new AliceWebhookService(
                new AliceAuth(props),
                new AliceSessionStore(),
                new AliceJobStore(),
                new AliceSpeechMapper(new ObjectMapper()),
                agent,
                props,
                executor
        );
    }

    private static AliceDialogModels.AliceRequest request(boolean isNew, String sessionId, String command) {
        return new AliceDialogModels.AliceRequest(
                "1.0",
                new AliceDialogModels.AliceRequest.Session(
                        isNew,
                        sessionId,
                        "skill",
                        0,
                        "EEAF",
                        new AliceDialogModels.AliceRequest.User(
                                "094E2C764EA423085EEEAB3212CD711F73AC33558D55EC41C291E541FD7241FD"
                        ),
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

    @SuppressWarnings("unchecked")
    private static boolean endSession(Map<String, Object> response) {
        return Boolean.TRUE.equals(((Map<String, Object>) response.get("response")).get("end_session"));
    }
}
