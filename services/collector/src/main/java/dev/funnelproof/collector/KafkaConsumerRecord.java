package dev.funnelproof.collector;

/** Minimal Kafka record needed for safe materialization and offset commits. */
public record KafkaConsumerRecord(String topic, int partition, long offset, String key, byte[] value) {
    public KafkaConsumerRecord {
        value = value.clone();
    }
}
