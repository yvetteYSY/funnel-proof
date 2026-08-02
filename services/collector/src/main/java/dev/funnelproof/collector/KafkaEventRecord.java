package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/**
 * Internal, Kafka-compatible record for an already accepted event. The key is deliberately
 * stable for one workspace/browser stream, because Kafka only orders records within a partition.
 */
public record KafkaEventRecord(
        String topic,
        String partitionKey,
        String workspaceId,
        String eventId,
        ObjectNode event
) {
    public KafkaEventRecord {
        event = event.deepCopy();
    }

    public byte[] serializedValue(ObjectMapper mapper) throws IOException {
        ObjectNode value = mapper.createObjectNode();
        value.put("workspace_id", workspaceId);
        value.set("event", event);
        return mapper.writeValueAsBytes(value);
    }
}
