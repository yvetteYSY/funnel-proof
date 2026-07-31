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

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectorApplicationTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private InMemoryEventStore store;
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        store = new InMemoryEventStore();
        server = CollectorApplication.createServer(0, new EventValidator(), store);
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
        assertEquals(1, store.snapshot().size());
        assertEquals("client_verified", store.snapshot().getFirst().path("event_time_quality").asText());
        assertEquals("/pricing", store.snapshot().getFirst().path("properties").path("page_path").asText());
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
        assertEquals(0, store.snapshot().size());
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
}
