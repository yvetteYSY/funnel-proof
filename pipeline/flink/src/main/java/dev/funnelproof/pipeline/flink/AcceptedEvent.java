package dev.funnelproof.pipeline.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.time.Instant;

/** Privacy-filtered Kafka envelope used by the streaming pipeline. */
public record AcceptedEvent(
        String workspaceId,
        String eventId,
        String anonymousId,
        String eventName,
        Instant occurredAt,
        Instant receivedAt,
        String schemaVersion,
        String trackingPlanVersion,
        String funnelDefinitionVersion,
        String acceptedEventJson
) implements Serializable {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AcceptedEvent parse(String kafkaValue) {
        try {
            JsonNode root = MAPPER.readTree(kafkaValue);
            JsonNode event = root.path("event");
            String workspaceId = requiredText(root, "workspace_id");
            String eventId = requiredText(event, "event_id");
            String anonymousId = requiredText(event, "anonymous_id");
            String eventName = requiredText(event, "event_name");
            return new AcceptedEvent(
                    workspaceId,
                    eventId,
                    anonymousId,
                    eventName,
                    Instant.parse(requiredText(event, "occurred_at")),
                    Instant.parse(requiredText(event, "received_at")),
                    requiredText(event, "schema_version"),
                    requiredText(event, "tracking_plan_version"),
                    requiredText(event, "funnel_definition_version"),
                    MAPPER.writeValueAsString(event)
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid accepted Kafka envelope", exception);
        }
    }

    public String dedupeKey() {
        return workspaceId + "\u0000" + eventId;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw new IllegalArgumentException("missing " + field);
        return value;
    }
}
