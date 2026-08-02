package dev.funnelproof.collector;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectorApplicationTest {
    private final HttpClient client = HttpClient.newHttpClient();
    @TempDir
    Path eventLogDirectory;
    private InMemoryEventStore store;
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        store = new InMemoryEventStore();
        server = CollectorApplication.createServer(
                0,
                new EventValidator(),
                store,
                new EventIngestionService(new LocalEventLog(eventLogDirectory), store),
                WorkspaceKeyResolver.fixed(Map.of("fp_public_local_test", "test_workspace"))
        );
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void acceptsValidFirstPartyEventAndPersistsOnlyTheSanitizedRecord() throws Exception {
        HttpResponse<String> response = post("""
                {
                  "event_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f50",
                  "event_name":"vpv",
                  "occurred_at":"2026-07-30T18:45:00.123Z",
                  "anonymous_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f51",
                  "session_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f52",
                  "properties":{"page_path":"/pricing"},
                  "context":{"platform":"web","sdk_version":"0.1.0"},
                  "schema_version":"1.0.0",
                  "tracking_plan_version":"1.0.0",
                  "funnel_definition_version":"1.0.0",
                  "consent":{"analytics":true}
                }
                """);

        assertEquals(202, response.statusCode());
        assertEquals(1, store.snapshot("test_workspace").size());
        assertEquals("client_verified", store.snapshot("test_workspace").getFirst().path("event_time_quality").asText());
        assertEquals("/pricing", store.snapshot("test_workspace").getFirst().path("properties").path("page_path").asText());
    }

    @Test
    void rejectsSensitivePayloadWithoutAppendingItToTheStore() throws Exception {
        HttpResponse<String> response = post("""
                {
                  "event_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f60",
                  "event_name":"activation_completed",
                  "occurred_at":"2026-07-30T18:45:00.123Z",
                  "anonymous_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f61",
                  "session_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f62",
                  "properties":{"activation_action":"first_project_created","email":"person@example.com"},
                  "context":{"platform":"web","sdk_version":"0.1.0"},
                  "schema_version":"1.0.0",
                  "tracking_plan_version":"1.0.0",
                  "funnel_definition_version":"1.0.0",
                  "consent":{"analytics":true}
                }
                """);

        assertEquals(422, response.statusCode());
        assertEquals(0, store.snapshot("test_workspace").size());
    }

    @Test
    void treatsAClientRetryAsAnAcceptedDuplicateRatherThanASecondEvent() throws Exception {
        String validEvent = """
                {
                  "event_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f70",
                  "event_name":"signup_completed",
                  "occurred_at":"2026-07-30T18:45:00.123Z",
                  "anonymous_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f71",
                  "session_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f72",
                  "properties":{"signup_method":"github"},
                  "context":{"platform":"web","sdk_version":"0.1.0"},
                  "schema_version":"1.0.0",
                  "tracking_plan_version":"1.0.0",
                  "funnel_definition_version":"1.0.0",
                  "consent":{"analytics":true}
                }
                """;

        assertEquals(202, post(validEvent).statusCode());
        HttpResponse<String> retryResponse = post(validEvent);

        assertEquals(202, retryResponse.statusCode());
        assertEquals(true, retryResponse.body().contains("\"duplicate\":true"));
        assertEquals(1, store.snapshot("test_workspace").size());
    }

    @Test
    void scopesFunnelReportToTheAuthenticatedWorkspace() throws Exception {
        HttpResponse<String> response = get("/fp/insights/funnel");

        assertEquals(200, response.statusCode());
        assertEquals(true, response.body().contains("\"metric_status\":\"local_provisional\""));
        assertEquals(true, response.body().contains("\"accepted_event_count\":0"));
    }

    private HttpResponse<String> post(String body) throws Exception {
        int port = server.getAddress().getPort();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/fp/collect"))
                .header("content-type", "application/json")
                .header("x-funnel-proof-workspace-key", "fp_public_local_test")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        int port = server.getAddress().getPort();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("x-funnel-proof-workspace-key", "fp_public_local_test")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
