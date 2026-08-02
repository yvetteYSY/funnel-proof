package dev.funnelproof.collector;

import java.util.List;

/** One poll's observable result, excluding raw event content. */
public record KafkaMaterializationResult(
        long polled,
        long materialized,
        long deadLettered,
        List<KafkaConsumerOffset> committedOffsets
) {
}
