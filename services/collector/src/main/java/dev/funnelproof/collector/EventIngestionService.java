package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/**
 * Preserves the event-log-first ingestion rule. If materialization fails after a durable publish,
 * a client retry replays the same event_id and safely completes the local read-model write.
 */
public final class EventIngestionService {
    private final EventLog eventLog;
    private final EventStore eventStore;
    private final KafkaEventRecordFactory recordFactory;

    public EventIngestionService(EventLog eventLog, EventStore eventStore) {
        this(eventLog, eventStore, new KafkaEventRecordFactory());
    }

    EventIngestionService(EventLog eventLog, EventStore eventStore, KafkaEventRecordFactory recordFactory) {
        this.eventLog = eventLog;
        this.eventStore = eventStore;
        this.recordFactory = recordFactory;
    }

    public EventLogAppendResult ingest(String workspaceId, ObjectNode acceptedEvent) throws IOException {
        EventLogAppendResult eventLogResult = eventLog.publish(recordFactory.create(workspaceId, acceptedEvent));
        eventStore.append(workspaceId, acceptedEvent);
        return eventLogResult;
    }
}
