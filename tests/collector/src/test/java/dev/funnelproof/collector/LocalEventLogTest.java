package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalEventLogTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path eventLogDirectory;

    @Test
    void survivesRestartAndAcknowledgesDuplicateLogicalEvents() throws Exception {
        KafkaEventRecord record = new KafkaEventRecordFactory().create("demo_workspace", event());
        LocalEventLog initialLog = new LocalEventLog(eventLogDirectory);

        assertTrue(initialLog.publish(record).persisted());

        LocalEventLog restartedLog = new LocalEventLog(eventLogDirectory);
        assertFalse(restartedLog.publish(record).persisted());
        String persisted = Files.readString(eventLogDirectory.resolve("accepted-events.ndjson"));
        assertTrue(persisted.contains("\"workspace_id\":\"demo_workspace\""));
        assertFalse(persisted.contains("workspace_key"));
    }

    private static ObjectNode event() {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("anonymous_id", "anonymous-browser-0001");
        event.put("event_id", "event-identifier-0001");
        return event;
    }
}
