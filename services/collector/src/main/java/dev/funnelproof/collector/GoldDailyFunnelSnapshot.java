package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds a versioned Gold snapshot from privacy-filtered Silver events. It intentionally publishes
 * daily aggregate counts only: no anonymous IDs, event IDs, timestamps, or event properties.
 */
public final class GoldDailyFunnelSnapshot {
    private static final List<String> STAGES = List.of(
            "vpv", "signup_completed", "activation_completed", "subscription_started"
    );

    public ObjectNode build(List<ObjectNode> events, Instant generatedAt) {
        TreeMap<LocalDate, Map<String, Set<String>>> usersByDayAndStage = new TreeMap<>();
        for (ObjectNode event : events) {
            try {
                String eventName = event.path("event_name").asText();
                String anonymousId = event.path("anonymous_id").asText();
                if (!STAGES.contains(eventName) || anonymousId.isBlank()) continue;
                LocalDate eventDate = Instant.parse(event.path("occurred_at").asText()).atZone(ZoneOffset.UTC).toLocalDate();
                usersByDayAndStage
                        .computeIfAbsent(eventDate, ignored -> new HashMap<>())
                        .computeIfAbsent(eventName, ignored -> new HashSet<>())
                        .add(anonymousId);
            } catch (Exception ignored) {
                // A malformed persisted row must not appear in a trustworthy aggregate.
            }
        }

        ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
        snapshot.put("gold_schema_version", "1.0.0");
        snapshot.put("metric_status", "local_provisional");
        snapshot.put("event_time_zone", "UTC");
        snapshot.put("generated_at", generatedAt.toString());
        var dailyFunnels = snapshot.putArray("daily_funnels");
        for (Map.Entry<LocalDate, Map<String, Set<String>>> entry : usersByDayAndStage.entrySet()) {
            ObjectNode day = dailyFunnels.addObject();
            day.put("event_date", entry.getKey().toString());
            ObjectNode stageUsers = day.putObject("stage_users");
            for (String stage : STAGES) {
                stageUsers.put(stage, entry.getValue().getOrDefault(stage, Set.of()).size());
            }
        }
        return snapshot;
    }
}
