package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Creates records that a future Kafka producer can publish without knowing workspace secrets. */
public final class KafkaEventRecordFactory {
    public static final String ACCEPTED_EVENTS_TOPIC = "funnelproof.accepted-events.v1";

    public KafkaEventRecord create(String workspaceId, ObjectNode acceptedEvent) {
        String anonymousId = acceptedEvent.path("anonymous_id").asText();
        String eventId = acceptedEvent.path("event_id").asText();
        if (workspaceId.isBlank() || anonymousId.isBlank() || eventId.isBlank()) {
            throw new IllegalArgumentException("accepted event requires workspace_id, anonymous_id, and event_id");
        }
        return new KafkaEventRecord(
                ACCEPTED_EVENTS_TOPIC,
                partitionKey(workspaceId, anonymousId),
                workspaceId,
                eventId,
                acceptedEvent
        );
    }

    /**
     * A length-prefixed composition is unambiguous even when identifiers contain a delimiter.
     * It keeps a browser journey ordered while allowing different browsers in one tenant to spread.
     */
    static String partitionKey(String workspaceId, String anonymousId) {
        return workspaceId.length() + ":" + workspaceId + anonymousId;
    }
}
