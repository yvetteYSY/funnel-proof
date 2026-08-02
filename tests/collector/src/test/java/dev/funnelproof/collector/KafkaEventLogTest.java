package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaEventLogTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void publishesTheKafkaCompatibleTopicKeyAndPrivacyFilteredEnvelope() throws Exception {
        RecordingProducer producer = new RecordingProducer();
        KafkaEventLog eventLog = new KafkaEventLog(producer, MAPPER);
        KafkaEventRecord record = new KafkaEventRecordFactory().create("demo_workspace", event());

        assertTrue(eventLog.publish(record).persisted());
        assertEquals("funnelproof.accepted-events.v1", producer.topic);
        assertEquals(record.partitionKey(), producer.partitionKey);
        ObjectNode value = (ObjectNode) MAPPER.readTree(producer.value);
        assertEquals("demo_workspace", value.path("workspace_id").asText());
        assertEquals("event-identifier-0001", value.path("event").path("event_id").asText());
    }

    @Test
    void usesIdempotentAllAcknowledgementProducerSettings() {
        Properties properties = KafkaProducerSettings.forBootstrapServers("127.0.0.1:9092");

        assertEquals("127.0.0.1:9092", properties.get("bootstrap.servers"));
        assertEquals("all", properties.get("acks"));
        assertEquals(true, properties.get("enable.idempotence"));
        assertEquals(5, properties.get("max.in.flight.requests.per.connection"));
        assertEquals(5_000, properties.get("max.block.ms"));
        assertEquals(5_000, properties.get("request.timeout.ms"));
    }

    private static ObjectNode event() {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("anonymous_id", "anonymous-browser-0001");
        event.put("event_id", "event-identifier-0001");
        return event;
    }

    private static final class RecordingProducer implements KafkaProducerClient {
        private String topic;
        private String partitionKey;
        private byte[] value;

        @Override
        public void publish(String topic, String partitionKey, byte[] value) throws IOException {
            this.topic = topic;
            this.partitionKey = partitionKey;
            this.value = value;
        }

        @Override
        public void close() {
            // No external resource in a unit test.
        }
    }
}
