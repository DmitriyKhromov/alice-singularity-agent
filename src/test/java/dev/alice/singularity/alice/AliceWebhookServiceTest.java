package dev.alice.singularity.alice;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.alice.singularity.agent.TaskAgent;
import dev.alice.singularity.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AliceWebhookServiceTest {

    @Test
    void greetingOnNewSessionWithoutCallingAgent() {
        TaskAgent agent = mock(TaskAgent.class);
        AppProperties props = new AppProperties(
                "Europe/Moscow",
                new AppProperties.GigaChat("k", "GIGACHAT_API_PERS", "GigaChat-2-Max"),
                new AppProperties.Singularity("t", "https://api.singularity-app.com"),
                new AppProperties.Alice(
                        "094E2C764EA423085EEEAB3212CD711F73AC33558D55EC41C291E541FD7241FD",
                        "Поставьте Диме новую задачу"
                )
        );
        AliceWebhookService service = new AliceWebhookService(
                new AliceAuth(props),
                new AliceSessionStore(),
                new AliceSpeechMapper(new ObjectMapper()),
                agent,
                props
        );

        AliceDialogModels.AliceRequest request = new AliceDialogModels.AliceRequest(
                "1.0",
                new AliceDialogModels.AliceRequest.Session(
                        true,
                        "sess",
                        "skill",
                        0,
                        "EEAF4E1BDCB3EC385847BFD90962B262F6F7393CFA96AB72BB2EE0DC7F1D7078",
                        new AliceDialogModels.AliceRequest.User(
                                "094E2C764EA423085EEEAB3212CD711F73AC33558D55EC41C291E541FD7241FD"
                        ),
                        new AliceDialogModels.AliceRequest.Application(
                                "EEAF4E1BDCB3EC385847BFD90962B262F6F7393CFA96AB72BB2EE0DC7F1D7078"
                        )
                ),
                new AliceDialogModels.AliceRequest.RequestBody("", "", "SimpleUtterance", null, null),
                null,
                null
        );

        Map<String, Object> response = service.handle(request);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.get("response");
        assertEquals("Поставьте Диме новую задачу", body.get("text"));
        assertFalse((Boolean) body.get("end_session"));
        verify(agent, never()).chat(org.mockito.ArgumentMatchers.anyString());
    }
}
