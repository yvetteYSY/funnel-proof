package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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

        addSubscriptionAnomaly(report, events, freshness);

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

    /**
     * Evaluates only daily aggregate subscription counts. Event-time buckets are intentionally
     * recomputed on every request so a late accepted event corrects its historical day without
     * exposing the underlying anonymous journeys.
     */
    private static void addSubscriptionAnomaly(ObjectNode report, List<ObjectNode> events, Freshness freshness) {
        TreeMap<LocalDate, Set<String>> subscriptionsByDay = new TreeMap<>();
        Set<LocalDate> observedDays = new HashSet<>();
        LocalDate latestEventDay = null;

        for (ObjectNode event : events) {
            try {
                LocalDate eventDay = Instant.parse(event.path("occurred_at").asText()).atZone(ZoneOffset.UTC).toLocalDate();
                observedDays.add(eventDay);
                if (latestEventDay == null || eventDay.isAfter(latestEventDay)) latestEventDay = eventDay;
                if ("subscription_started".equals(event.path("event_name").asText())) {
                    String anonymousId = event.path("anonymous_id").asText();
                    if (!anonymousId.isBlank()) {
                        subscriptionsByDay.computeIfAbsent(eventDay, ignored -> new HashSet<>()).add(anonymousId);
                    }
                }
            } catch (Exception ignored) {
                // A malformed persisted record cannot create an anomaly finding or an observed day.
            }
        }

        List<Double> baseline = new ArrayList<>();
        double currentValue = 0;
        String asOfEventDate = null;
        if (latestEventDay != null) {
            LocalDate currentEventDay = latestEventDay;
            asOfEventDate = currentEventDay.toString();
            currentValue = subscriptionsByDay.getOrDefault(currentEventDay, Set.of()).size();
            for (LocalDate observedDay : observedDays.stream().filter(day -> day.isBefore(currentEventDay)).sorted().toList()) {
                baseline.add((double) subscriptionsByDay.getOrDefault(observedDay, Set.of()).size());
            }
            if (baseline.size() > 7) baseline = new ArrayList<>(baseline.subList(baseline.size() - 7, baseline.size()));
        }

        var result = new RobustAnomalyDetector().detect(
                "daily_subscription_started_users", baseline, currentValue, "healthy".equals(freshness.status())
        );
        ObjectNode anomaly = report.putObject("anomaly");
        anomaly.put("metric", result.metric());
        anomaly.put("status", result.status());
        anomaly.put("current_value", currentValue);
        anomaly.put("baseline_points", baseline.size());
        if (asOfEventDate != null) anomaly.put("as_of_event_date", asOfEventDate);
        if (result.baselineMedian() != null) anomaly.put("baseline_median", result.baselineMedian());
        if (result.baselineMad() != null) anomaly.put("baseline_mad", result.baselineMad());
        if (result.robustZScore() != null) anomaly.put("robust_z_score", result.robustZScore());
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
