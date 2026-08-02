package dev.funnelproof.collector;

import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Properties;

/** Explicit reliability settings; they complement rather than replace downstream event-id dedupe. */
final class KafkaProducerSettings {
    private KafkaProducerSettings() {
    }

    static Properties forBootstrapServers(String bootstrapServers) {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("Kafka bootstrap servers are required");
        }
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30_000);
        return properties;
    }
}
