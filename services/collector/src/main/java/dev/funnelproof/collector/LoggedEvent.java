package dev.funnelproof.collector;

/** Local-log offset plus its reconstructed Kafka-compatible record. */
public record LoggedEvent(long offset, KafkaEventRecord record) {
}
