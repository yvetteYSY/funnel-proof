package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka-to-Bronze materializer. Durable store writes happen before offset commits. Invalid
 * Kafka values are safely audited and committed so one poison record cannot block a partition.
 */
public final class KafkaEventMaterializer {
    private final ObjectMapper mapper = new ObjectMapper();
    private final KafkaEventRecordFactory recordFactory = new KafkaEventRecordFactory();

    public KafkaMaterializationResult materializeOnce(
            KafkaConsumerClient consumer,
            EventStore eventStore,
            DeadLetterSink deadLetterSink,
            Duration pollTimeout
    ) throws IOException {
        List<KafkaConsumerRecord> records = consumer.poll(pollTimeout);
        Map<String, KafkaConsumerOffset> commits = new LinkedHashMap<>();
        long materialized = 0;
        long deadLettered = 0;

        for (KafkaConsumerRecord record : records) {
            try {
                KafkaEventRecord decoded = decode(record);
                eventStore.append(decoded.workspaceId(), decoded.event());
                materialized++;
            } catch (InvalidKafkaEventException exception) {
                deadLetterSink.record(record, exception.reason());
                deadLettered++;
            }
            KafkaConsumerOffset nextOffset = new KafkaConsumerOffset(record.topic(), record.partition(), record.offset() + 1);
            commits.put(record.topic() + ":" + record.partition(), nextOffset);
        }
        if (!commits.isEmpty()) consumer.commitSync(List.copyOf(commits.values()));
        return new KafkaMaterializationResult(records.size(), materialized, deadLettered, List.copyOf(commits.values()));
    }

    private KafkaEventRecord decode(KafkaConsumerRecord record) throws InvalidKafkaEventException {
        try {
            JsonNode parsed = mapper.readTree(record.value());
            String workspaceId = parsed.path("workspace_id").asText();
            if (!(parsed.path("event") instanceof ObjectNode event)) throw new InvalidKafkaEventException("invalid_event_envelope");
            KafkaEventRecord decoded = recordFactory.create(workspaceId, event);
            if (!KafkaEventRecordFactory.ACCEPTED_EVENTS_TOPIC.equals(record.topic()) || !record.key().equals(decoded.partitionKey())) {
                throw new InvalidKafkaEventException("invalid_topic_or_partition_key");
            }
            return decoded;
        } catch (InvalidKafkaEventException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidKafkaEventException("invalid_event_envelope");
        }
    }

    private static final class InvalidKafkaEventException extends Exception {
        private final String reason;

        private InvalidKafkaEventException(String reason) {
            this.reason = reason;
        }

        private String reason() {
            return reason;
        }
    }
}
