package dev.funnelproof.pipeline.flink;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcceptedEventTest {
    @Test
    void parsesTheCollectorKafkaEnvelopeAndBuildsAWorkspaceScopedDedupeKey() {
        AcceptedEvent event = AcceptedEvent.parse("""
                {"workspace_id":"demo_workspace","event":{"event_id":"event-000000000001","anonymous_id":"anonymous-000000001","event_name":"vpv","occurred_at":"2026-08-18T16:00:00Z","received_at":"2026-08-18T16:00:01Z","schema_version":"1.0.0","tracking_plan_version":"1.0.0","funnel_definition_version":"1.0.0","properties":{"page_path":"/pricing"}}}
                """);

        assertEquals("demo_workspace\u0000event-000000000001", event.dedupeKey());
        assertEquals("vpv", event.eventName());
        assertEquals("2026-08-18T16:00:00Z", event.occurredAt().toString());
    }

    @Test
    void rejectsMalformedKafkaValuesBeforeTheyReachBronze() {
        assertThrows(IllegalArgumentException.class, () -> AcceptedEvent.parse("{\"workspace_id\":\"demo_workspace\",\"event\":{}}"));
    }
}
