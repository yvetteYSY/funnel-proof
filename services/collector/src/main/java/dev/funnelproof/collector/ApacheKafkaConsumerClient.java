package dev.funnelproof.collector;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Opt-in Apache Kafka consumer; no connection is made unless the runner is invoked. */
final class ApacheKafkaConsumerClient implements KafkaConsumerClient {
    private final KafkaConsumer<String, byte[]> consumer;

    ApacheKafkaConsumerClient(String bootstrapServers, String groupId, String topic) {
        Properties properties = KafkaConsumerSettings.forBootstrapServers(bootstrapServers, groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topic));
    }

    @Override
    public List<KafkaConsumerRecord> poll(Duration timeout) throws IOException {
        try {
            List<KafkaConsumerRecord> result = new ArrayList<>();
            consumer.poll(timeout).forEach(record -> result.add(new KafkaConsumerRecord(
                    record.topic(), record.partition(), record.offset(), record.key(), record.value()
            )));
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            throw new IOException("Kafka poll failed", exception);
        }
    }

    @Override
    public void commitSync(List<KafkaConsumerOffset> offsets) throws IOException {
        try {
            Map<TopicPartition, OffsetAndMetadata> commitOffsets = offsets.stream().collect(java.util.stream.Collectors.toMap(
                    offset -> new TopicPartition(offset.topic(), offset.partition()),
                    offset -> new OffsetAndMetadata(offset.nextOffset())
            ));
            consumer.commitSync(commitOffsets);
        } catch (RuntimeException exception) {
            throw new IOException("Kafka offset commit failed", exception);
        }
    }

    @Override
    public void close() {
        consumer.close();
    }
}
