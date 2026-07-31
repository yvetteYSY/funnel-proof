package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, local-only funnel calculation over accepted events. A report is explicitly
 * provisional: production canonical reporting is rebuilt from privacy-filtered lake events.
 */
public final class FunnelReportService {
    private static final List<String> STAGES = List.of(
            "vpv", "signup_completed", "activation_completed", "subscription_started"
    );
    private static final Duration FRESHNESS_OBJECTIVE = Duration.ofMinutes(5);

    public ObjectNode buildReport(List<ObjectNode> events, Instant generatedAt) {
        ObjectNode report = JsonNodeFactory.instance.objectNode();
        report.put("generated_at", generatedAt.toString());
        report.put("metric_status", "local_provisional");
        report.put("funnel_definition_version", "1.0.0");
        report.put("identity_scope", "anonymous_id");
        report.put("accepted_event_count", events.size());

        Freshness freshness = freshness(events, generatedAt);
        ObjectNode dataSla = report.putObject("data_sla");
        dataSla.put("status", freshness.status());
        dataSla.put("objective", "latest accepted event received within 5 minutes");
        if (freshness.latestReceivedAt() != null) {
            dataSla.put("latest_received_at", freshness.latestReceivedAt().toString());
            dataSla.put("age_seconds", freshness.ageSeconds());
        }

        int[] counts = stageCounts(events);
        ArrayNode stages = report.putArray("stages");
        for (int index = 0; index < STAGES.size(); index++) {
            ObjectNode stage = stages.addObject();
            stage.put("event_name", STAGES.get(index));
            stage.put("users", counts[index]);
            if (index == 0) {
                stage.putNull("conversion_from_previous");
            } else if (counts[index - 1] == 0) {
                stage.putNull("conversion_from_previous");
            } else {
                stage.put("conversion_from_previous", roundRate(counts[index], counts[index - 1]));
            }
        }

        ObjectNode commentary = report.putObject("commentary");
        if (!"healthy".equals(freshness.status())) {
            commentary.put("status", "suppressed_data_sla_unhealthy");
            commentary.put("summary", "Funnel commentary is suppressed because no fresh accepted event is available.");
        } else if (counts[0] == 0) {
            commentary.put("status", "insufficient_volume");
            commentary.put("summary", "No qualified first-party page views are available for the selected funnel.");
        } else {
            int largestDropIndex = largestDropIndex(counts);
            commentary.put("status", "informational");
            commentary.put("summary", String.format(
                    "Largest observed drop-off is from %s to %s (%.1f%% conversion).",
                    STAGES.get(largestDropIndex - 1), STAGES.get(largestDropIndex),
                    roundRate(counts[largestDropIndex], counts[largestDropIndex - 1]) * 100
            ));
        }
        return report;
    }

    private static int[] stageCounts(List<ObjectNode> events) {
        Map<String, List<ObjectNode>> eventsByAnonymousId = new HashMap<>();
        for (ObjectNode event : events) {
            String anonymousId = event.path("anonymous_id").asText();
            if (!anonymousId.isBlank()) {
                eventsByAnonymousId.computeIfAbsent(anonymousId, ignored -> new ArrayList<>()).add(event);
            }
        }

        int[] counts = new int[STAGES.size()];
        for (List<ObjectNode> userEvents : eventsByAnonymousId.values()) {
            userEvents.sort(Comparator
                    .comparing((ObjectNode event) -> Instant.parse(event.path("occurred_at").asText()))
                    .thenComparingInt(event -> stageIndex(event.path("event_name").asText())));

            int completedStage = -1;
            for (ObjectNode event : userEvents) {
                int stage = stageIndex(event.path("event_name").asText());
                if (stage == completedStage + 1) completedStage = stage;
            }
            for (int index = 0; index <= completedStage; index++) counts[index]++;
        }
        return counts;
    }

    private static Freshness freshness(List<ObjectNode> events, Instant generatedAt) {
        Instant latestReceivedAt = null;
        for (ObjectNode event : events) {
            try {
                Instant receivedAt = Instant.parse(event.path("received_at").asText());
                if (latestReceivedAt == null || receivedAt.isAfter(latestReceivedAt)) latestReceivedAt = receivedAt;
            } catch (Exception ignored) {
                // EventValidator guarantees this; a malformed persisted row simply cannot make data look fresh.
            }
        }
        if (latestReceivedAt == null) return new Freshness("no_data", null, null);
        long ageSeconds = Math.max(0, Duration.between(latestReceivedAt, generatedAt).toSeconds());
        String status = ageSeconds <= FRESHNESS_OBJECTIVE.toSeconds() ? "healthy" : "stale";
        return new Freshness(status, latestReceivedAt, ageSeconds);
    }

    private static int stageIndex(String eventName) {
        return STAGES.indexOf(eventName);
    }

    private static int largestDropIndex(int[] counts) {
        int answer = 1;
        double lowestRate = Double.POSITIVE_INFINITY;
        for (int index = 1; index < counts.length; index++) {
            if (counts[index - 1] == 0) continue;
            double rate = (double) counts[index] / counts[index - 1];
            if (rate < lowestRate) {
                lowestRate = rate;
                answer = index;
            }
        }
        return answer;
    }

    private static double roundRate(int numerator, int denominator) {
        return Math.round(((double) numerator / denominator) * 10_000.0) / 10_000.0;
    }

    private record Freshness(String status, Instant latestReceivedAt, Long ageSeconds) {
    }
}
