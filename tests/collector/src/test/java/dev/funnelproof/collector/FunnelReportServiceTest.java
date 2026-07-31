package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FunnelReportServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant GENERATED_AT = Instant.parse("2026-07-30T18:50:00Z");

    @Test
    void buildsAnOrderedFunnelAndExplainsTheLargestObservedDrop() {
        List<ObjectNode> events = List.of(
                event("a-visitor-identifier-0001", "vpv", "2026-07-30T18:45:00Z"),
                event("a-visitor-identifier-0001", "signup_completed", "2026-07-30T18:45:01Z"),
                event("a-visitor-identifier-0001", "activation_completed", "2026-07-30T18:45:02Z"),
                event("a-visitor-identifier-0001", "subscription_started", "2026-07-30T18:45:03Z"),
                event("b-visitor-identifier-0002", "vpv", "2026-07-30T18:45:00Z"),
                event("b-visitor-identifier-0002", "signup_completed", "2026-07-30T18:45:01Z"),
                event("c-visitor-identifier-0003", "signup_completed", "2026-07-30T18:45:01Z")
        );

        ObjectNode report = new FunnelReportService().buildReport(events, GENERATED_AT);

        assertEquals("healthy", report.path("data_sla").path("status").asText());
        assertEquals(2, report.path("stages").get(0).path("users").asInt());
        assertEquals(2, report.path("stages").get(1).path("users").asInt());
        assertEquals(1, report.path("stages").get(2).path("users").asInt());
        assertEquals(1, report.path("stages").get(3).path("users").asInt());
        assertEquals("informational", report.path("commentary").path("status").asText());
        assertEquals(true, report.path("commentary").path("summary").asText().contains("signup_completed to activation_completed"));
    }

    @Test
    void suppressesCommentaryWhenTheLatestAcceptedEventIsStale() {
        ObjectNode report = new FunnelReportService().buildReport(
                List.of(event("a-visitor-identifier-0001", "vpv", "2026-07-30T18:40:00Z")),
                GENERATED_AT
        );

        assertEquals("stale", report.path("data_sla").path("status").asText());
        assertEquals("suppressed_data_sla_unhealthy", report.path("commentary").path("status").asText());
    }

    private static ObjectNode event(String anonymousId, String eventName, String occurredAt) {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("event_id", anonymousId + eventName);
        event.put("anonymous_id", anonymousId);
        event.put("event_name", eventName);
        event.put("occurred_at", occurredAt);
        event.put("received_at", occurredAt);
        return event;
    }
}
