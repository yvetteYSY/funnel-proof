package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GoldDailyFunnelSnapshotTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsEventTimeDailyStageCountsWithoutIdentifiers() throws Exception {
        ObjectNode snapshot = new GoldDailyFunnelSnapshot().build(List.of(
                event("anonymous-user-0001", "vpv", "2026-08-01T23:59:00Z"),
                event("anonymous-user-0001", "signup_completed", "2026-08-02T00:01:00Z"),
                event("anonymous-user-0002", "vpv", "2026-08-02T00:02:00Z"),
                event("anonymous-user-0002", "vpv", "2026-08-02T00:03:00Z")
        ), Instant.parse("2026-08-03T00:00:00Z"));

        assertEquals("1.0.0", snapshot.path("gold_schema_version").asText());
        assertEquals(2, snapshot.path("daily_funnels").size());
        assertEquals(1, snapshot.path("daily_funnels").get(1).path("stage_users").path("vpv").asInt());
        assertEquals(1, snapshot.path("daily_funnels").get(1).path("stage_users").path("signup_completed").asInt());
        assertFalse(snapshot.toString().contains("anonymous-user"));
    }

    @Test
    void atomicallyReplacesTheConfiguredGoldTarget() throws Exception {
        Path target = temporaryDirectory.resolve("gold/daily-funnel.v1.json");
        ObjectNode snapshot = new GoldDailyFunnelSnapshot().build(List.of(), Instant.parse("2026-08-03T00:00:00Z"));

        new GoldSnapshotWriter().replace(target, snapshot);

        assertEquals("1.0.0", MAPPER.readTree(Files.readString(target)).path("gold_schema_version").asText());
    }

    private static ObjectNode event(String anonymousId, String eventName, String occurredAt) {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("anonymous_id", anonymousId);
        event.put("event_name", eventName);
        event.put("occurred_at", occurredAt);
        return event;
    }
}
