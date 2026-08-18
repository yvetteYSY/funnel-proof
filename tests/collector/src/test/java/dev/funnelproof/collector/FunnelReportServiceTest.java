package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
        assertEquals("suppressed_data_sla_unhealthy", report.path("anomaly").path("status").asText());
    }

    @Test
    void detectsAnEventTimeSubscriptionAnomalyFromSevenPriorAggregateDays() {
        List<ObjectNode> events = new ArrayList<>();
        Instant latestReceivedAt = GENERATED_AT.minusSeconds(30);
        int[] baselineSubscriptions = {6, 7, 8, 7, 8, 6, 8};
        for (int day = 0; day < baselineSubscriptions.length; day++) {
            Instant occurredAt = GENERATED_AT.minus(8L - day, ChronoUnit.DAYS);
            for (int user = 0; user < baselineSubscriptions[day]; user++) {
                events.add(event("baseline-" + day + "-user-" + user + "-identifier", "subscription_started", occurredAt.toString(), latestReceivedAt.toString()));
            }
        }
        events.add(event("current-user-identifier-0001", "subscription_started", GENERATED_AT.minus(1, ChronoUnit.DAYS).toString(), latestReceivedAt.toString()));

        ObjectNode report = new FunnelReportService().buildReport(events, GENERATED_AT);

        assertEquals("anomaly", report.path("anomaly").path("status").asText());
        assertEquals("daily_subscription_started_users", report.path("anomaly").path("metric").asText());
        assertEquals(7, report.path("anomaly").path("baseline_points").asInt());
        assertEquals(1.0, report.path("anomaly").path("current_value").asDouble());
        assertEquals(false, report.path("anomaly").has("anonymous_id"));
    }

    private static ObjectNode event(String anonymousId, String eventName, String occurredAt) {
        return event(anonymousId, eventName, occurredAt, occurredAt);
    }

    private static ObjectNode event(String anonymousId, String eventName, String occurredAt, String receivedAt) {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("event_id", anonymousId + eventName);
        event.put("anonymous_id", anonymousId);
        event.put("event_name", eventName);
        event.put("occurred_at", occurredAt);
        event.put("received_at", receivedAt);
        return event;
    }
}
