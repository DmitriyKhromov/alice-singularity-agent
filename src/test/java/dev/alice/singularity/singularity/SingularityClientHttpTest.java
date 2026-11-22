package dev.alice.singularity.singularity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.alice.singularity.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest
class SingularityClientHttpTest {

    private SingularityClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        AppProperties properties = new AppProperties(
                "Europe/Moscow",
                new AppProperties.GigaChat("key", "GIGACHAT_API_PERS", "GigaChat-2-Max"),
                new AppProperties.Singularity("test-token", wm.getHttpBaseUrl())
        );
        client = new SingularityClient(properties, new ObjectMapper());
    }

    @Test
    void createTaskPostsNormalizedStart() {
        stubFor(post(urlEqualTo("/v2/task"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"T-1","title":"пробежка","start":"2026-11-23T00:00:00+03:00","priority":1}
                                """)));

        String body = client.createTask("пробежка", "завтра", 1, null);

        assertTrue(body.contains("T-1"));
        verify(postRequestedFor(urlEqualTo("/v2/task"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withRequestBody(containing("\"title\":\"пробежка\""))
                .withRequestBody(containing("\"start\""))
                .withRequestBody(containing("+03:00")));
    }

    @Test
    void listTasksUsesQueryParams() {
        stubFor(get(urlPathEqualTo("/v2/task"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"tasks\":[]}")));

        client.listTasks(10, 0, "2026-11-22", "2026-11-22", false, false);

        verify(getRequestedFor(urlPathEqualTo("/v2/task"))
                .withQueryParam("maxCount", equalTo("10"))
                .withQueryParam("startDateFrom", equalTo("2026-11-22"))
                .withQueryParam("startDateTo", equalTo("2026-11-22")));
    }

    @Test
    void updateTaskPatchesTitle() {
        stubFor(patch(urlEqualTo("/v2/task/T-abc"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"T-abc","title":"Купить хлеб","start":null,"priority":1}
                                """)));

        String body = client.updateTask("T-abc", "Купить хлеб", null, null, null, null, null);

        assertTrue(body.contains("Купить хлеб"));
        verify(patchRequestedFor(urlEqualTo("/v2/task/T-abc"))
                .withRequestBody(equalToJson("""
                        {"title":"Купить хлеб"}
                        """)));
    }

    @Test
    void httpErrorBecomesSingularityException() {
        stubFor(post(urlEqualTo("/v2/task"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"Bad Request\"}")));

        SingularityException ex = assertThrows(SingularityException.class,
                () -> client.createTask("x", null, 1, null));
        assertEquals(400, ex.statusCode());
    }
}
