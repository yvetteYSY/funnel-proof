package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class KafkaEventRecordFactoryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final KafkaEventRecordFactory factory = new KafkaEventRecordFactory();

    @Test
    void keepsOneWorkspaceBrowserJourneyOnOneDeterministicPartitionKey() throws Exception {
        KafkaEventRecord first = factory.create("demo_workspace", event("anonymous-browser-0001", "event-identifier-0001"));
        KafkaEventRecord retry = factory.create("demo_workspace", event("anonymous-browser-0001", "event-identifier-0002"));
        KafkaEventRecord otherBrowser = factory.create("demo_workspace", event("anonymous-browser-0002", "event-identifier-0003"));

        assertEquals(KafkaEventRecordFactory.ACCEPTED_EVENTS_TOPIC, first.topic());
        assertEquals(first.partitionKey(), retry.partitionKey());
        assertNotEquals(first.partitionKey(), otherBrowser.partitionKey());

        ObjectNode serialized = (ObjectNode) MAPPER.readTree(first.serializedValue(MAPPER));
        assertEquals("demo_workspace", serialized.path("workspace_id").asText());
        assertEquals("event-identifier-0001", serialized.path("event").path("event_id").asText());
        assertFalse(serialized.has("workspace_key"));
    }

    private static ObjectNode event(String anonymousId, String eventId) {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("anonymous_id", anonymousId);
        event.put("event_id", eventId);
        return event;
    }
}
