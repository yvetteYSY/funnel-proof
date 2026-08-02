package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalEventLogMaterializerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsAndRebuildsTheReadModelFromAcceptedEventLogRecords() throws Exception {
        LocalEventLog eventLog = new LocalEventLog(temporaryDirectory.resolve("event-log"));
        KafkaEventRecordFactory factory = new KafkaEventRecordFactory();
        eventLog.publish(factory.create("demo_workspace", event("event-identifier-0001", "anonymous-browser-0001")));
        eventLog.publish(factory.create("demo_workspace", event("event-identifier-0002", "anonymous-browser-0002")));

        FileEventStore targetStore = new FileEventStore(temporaryDirectory.resolve("rebuild"));
        FileOffsetStore offsetStore = new FileOffsetStore(temporaryDirectory.resolve("checkpoints/bronze.offset"));
        LocalEventLogMaterializer materializer = new LocalEventLogMaterializer();

        MaterializationResult initial = materializer.materialize(eventLog, targetStore, offsetStore);
        MaterializationResult repeat = materializer.materialize(eventLog, targetStore, offsetStore);

        assertEquals(2, initial.processed());
        assertEquals(2, initial.newlyWritten());
        assertEquals(2, initial.checkpoint());
        assertEquals(0, repeat.processed());
        assertEquals(2, targetStore.snapshot("demo_workspace").size());
    }

    @Test
    void checkpointDoesNotAdvancePastAFailedMaterialization() throws Exception {
        LocalEventLog eventLog = new LocalEventLog(temporaryDirectory.resolve("event-log"));
        eventLog.publish(new KafkaEventRecordFactory().create(
                "demo_workspace", event("event-identifier-0001", "anonymous-browser-0001")
        ));
        FileOffsetStore offsetStore = new FileOffsetStore(temporaryDirectory.resolve("checkpoints/bronze.offset"));
        LocalEventLogMaterializer materializer = new LocalEventLogMaterializer();

        assertThrows(IOException.class, () -> materializer.materialize(eventLog, new AlwaysFailStore(), offsetStore));
        assertEquals(0, offsetStore.load());
    }

    private static ObjectNode event(String eventId, String anonymousId) {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("event_id", eventId);
        event.put("anonymous_id", anonymousId);
        return event;
    }

    private static final class AlwaysFailStore implements EventStore {
        @Override
        public StoreAppendResult append(String workspaceId, ObjectNode acceptedEvent) throws IOException {
            throw new IOException("simulated read-model outage");
        }

        @Override
        public List<ObjectNode> snapshot(String workspaceId) {
            return List.of();
        }
    }
}
