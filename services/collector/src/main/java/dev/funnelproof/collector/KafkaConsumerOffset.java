package dev.funnelproof.collector;

/** Kafka commit offset: the next record to read for one topic partition. */
public record KafkaConsumerOffset(String topic, int partition, long nextOffset) {
}
