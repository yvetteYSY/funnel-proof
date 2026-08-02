package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventIngestionServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void retryCompletesMaterializationAfterTheEventWasDurablyPublished() throws Exception {
        RecordingEventLog eventLog = new RecordingEventLog();
        FailOnceStore eventStore = new FailOnceStore();
        EventIngestionService service = new EventIngestionService(eventLog, eventStore);
        ObjectNode event = event();

        assertThrows(IOException.class, () -> service.ingest("demo_workspace", event));
        EventLogAppendResult retry = service.ingest("demo_workspace", event);

        assertFalse(retry.persisted());
        assertEquals(1, eventLog.persistedRecords);
        assertEquals(1, eventStore.snapshot("demo_workspace").size());
    }

    private static ObjectNode event() {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("anonymous_id", "anonymous-browser-0001");
        event.put("event_id", "event-identifier-0001");
        return event;
    }

    private static final class RecordingEventLog implements EventLog {
        private final Set<String> identities = new HashSet<>();
        private int persistedRecords;

        @Override
        public EventLogAppendResult publish(KafkaEventRecord record) {
            if (!identities.add(record.workspaceId() + record.eventId())) return EventLogAppendResult.duplicate();
            persistedRecords++;
            return EventLogAppendResult.written();
        }
    }

    private static final class FailOnceStore implements EventStore {
        private final InMemoryEventStore delegate = new InMemoryEventStore();
        private boolean fail = true;

        @Override
        public StoreAppendResult append(String workspaceId, ObjectNode acceptedEvent) throws IOException {
            if (fail) {
                fail = false;
                throw new IOException("simulated local read-model failure");
            }
            return delegate.append(workspaceId, acceptedEvent);
        }

        @Override
        public List<ObjectNode> snapshot(String workspaceId) {
            return delegate.snapshot(workspaceId);
        }
    }
}
