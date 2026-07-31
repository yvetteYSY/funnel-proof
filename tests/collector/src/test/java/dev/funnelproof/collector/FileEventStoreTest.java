package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileEventStoreTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path dataDirectory;

    @Test
    void survivesRestartAndDeduplicatesEventIdsWithinOneWorkspace() throws Exception {
        FileEventStore initialStore = new FileEventStore(dataDirectory);
        ObjectNode event = acceptedEvent("018f4f70-8ab1-7cc4-a67d-fae57c0c0f80");

        assertTrue(initialStore.append("demo_workspace", event).persisted());
        assertFalse(initialStore.append("demo_workspace", event).persisted());

        FileEventStore restartedStore = new FileEventStore(dataDirectory);
        assertEquals(1, restartedStore.snapshot("demo_workspace").size());
        assertEquals("subscription_started", restartedStore.snapshot("demo_workspace").getFirst().path("event_name").asText());
        assertEquals(0, restartedStore.snapshot("another_workspace").size());
    }

    private static ObjectNode acceptedEvent(String eventId) {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("event_id", eventId);
        event.put("event_name", "subscription_started");
        event.put("occurred_at", "2026-07-30T18:45:00.123Z");
        event.put("received_at", "2026-07-30T18:45:00.240Z");
        event.put("anonymous_id", "018f4f70-8ab1-7cc4-a67d-fae57c0c0f81");
        return event;
    }
}
