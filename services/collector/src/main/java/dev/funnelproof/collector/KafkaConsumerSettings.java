package dev.funnelproof.collector;

import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.util.Properties;

/** Consumer defaults intentionally disable auto-commit so storage controls checkpoint progress. */
final class KafkaConsumerSettings {
    private KafkaConsumerSettings() {
    }

    static Properties forBootstrapServers(String bootstrapServers, String groupId) {
        if (bootstrapServers == null || bootstrapServers.isBlank() || groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("Kafka bootstrap servers and consumer group id are required");
        }
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        return properties;
    }
}
