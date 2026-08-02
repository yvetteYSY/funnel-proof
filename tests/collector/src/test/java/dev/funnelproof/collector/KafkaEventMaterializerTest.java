package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KafkaEventMaterializerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void materializesValidRecordsAndCommitsOnlyAfterTheStoreWrite() throws Exception {
        KafkaEventRecord accepted = new KafkaEventRecordFactory().create("demo_workspace", event());
        RecordingConsumer consumer = new RecordingConsumer(List.of(kafkaRecord(accepted, 7)));
        InMemoryEventStore store = new InMemoryEventStore();
        RecordingDeadLetterSink deadLetters = new RecordingDeadLetterSink();

        KafkaMaterializationResult result = new KafkaEventMaterializer().materializeOnce(
                consumer, store, deadLetters, Duration.ofMillis(1)
        );

        assertEquals(1, result.materialized());
        assertEquals(0, result.deadLettered());
        assertEquals(1, store.snapshot("demo_workspace").size());
        assertEquals(List.of(new KafkaConsumerOffset(KafkaEventRecordFactory.ACCEPTED_EVENTS_TOPIC, 0, 8)), consumer.commits);
    }

    @Test
    void deadLettersMalformedRecordsThenCommitsPastThem() throws Exception {
        KafkaConsumerRecord malformed = new KafkaConsumerRecord(
                KafkaEventRecordFactory.ACCEPTED_EVENTS_TOPIC, 2, 11, "not-a-valid-key", "not-json".getBytes(StandardCharsets.UTF_8)
        );
        RecordingConsumer consumer = new RecordingConsumer(List.of(malformed));
        RecordingDeadLetterSink deadLetters = new RecordingDeadLetterSink();

        KafkaMaterializationResult result = new KafkaEventMaterializer().materializeOnce(
                consumer, new InMemoryEventStore(), deadLetters, Duration.ofMillis(1)
        );

        assertEquals(1, result.deadLettered());
        assertEquals(List.of("invalid_event_envelope"), deadLetters.reasons);
        assertEquals(List.of(new KafkaConsumerOffset(KafkaEventRecordFactory.ACCEPTED_EVENTS_TOPIC, 2, 12)), consumer.commits);
    }

    @Test
    void doesNotCommitWhenTheDurableStoreIsUnavailable() throws Exception {
        KafkaEventRecord accepted = new KafkaEventRecordFactory().create("demo_workspace", event());
        RecordingConsumer consumer = new RecordingConsumer(List.of(kafkaRecord(accepted, 1)));

        assertThrows(IOException.class, () -> new KafkaEventMaterializer().materializeOnce(
                consumer, new FailingStore(), new RecordingDeadLetterSink(), Duration.ofMillis(1)
        ));
        assertEquals(List.of(), consumer.commits);
    }

    private static KafkaConsumerRecord kafkaRecord(KafkaEventRecord event, long offset) throws Exception {
        return new KafkaConsumerRecord(event.topic(), 0, offset, event.partitionKey(), event.serializedValue(MAPPER));
    }

    private static ObjectNode event() {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("anonymous_id", "anonymous-browser-0001");
        event.put("event_id", "event-identifier-0001");
        return event;
    }

    private static final class RecordingConsumer implements KafkaConsumerClient {
        private final List<KafkaConsumerRecord> records;
        private List<KafkaConsumerOffset> commits = List.of();

        private RecordingConsumer(List<KafkaConsumerRecord> records) {
            this.records = records;
        }

        @Override
        public List<KafkaConsumerRecord> poll(Duration timeout) {
            return records;
        }

        @Override
        public void commitSync(List<KafkaConsumerOffset> offsets) {
            commits = offsets;
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingDeadLetterSink implements DeadLetterSink {
        private final List<String> reasons = new ArrayList<>();

        @Override
        public void record(KafkaConsumerRecord record, String reason) {
            reasons.add(reason);
        }
    }

    private static final class FailingStore implements EventStore {
        @Override
        public StoreAppendResult append(String workspaceId, ObjectNode acceptedEvent) throws IOException {
            throw new IOException("simulated durable store outage");
        }

        @Override
        public List<ObjectNode> snapshot(String workspaceId) {
            return List.of();
        }
    }
}
