package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Opt-in production adapter. Kafka producer idempotence protects producer retries; stable event_id
 * remains necessary for end-to-end dedupe in stream and storage layers.
 */
public final class KafkaEventLog implements EventLog, AutoCloseable {
    private final KafkaProducerClient producer;
    private final ObjectMapper mapper;

    public static KafkaEventLog forBootstrapServers(String bootstrapServers) {
        return new KafkaEventLog(new ApacheKafkaProducerClient(bootstrapServers), new ObjectMapper());
    }

    KafkaEventLog(KafkaProducerClient producer, ObjectMapper mapper) {
        this.producer = producer;
        this.mapper = mapper;
    }

    @Override
    public EventLogAppendResult publish(KafkaEventRecord record) throws IOException {
        producer.publish(record.topic(), record.partitionKey(), record.serializedValue(mapper));
        return EventLogAppendResult.written();
    }

    @Override
    public void close() {
        producer.close();
    }
}
