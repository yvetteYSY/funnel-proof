package dev.funnelproof.collector;

import java.nio.file.Path;
import java.time.Duration;

/** Runs one bounded Kafka poll; invoke repeatedly under a local supervisor or future stream runtime. */
public final class KafkaMaterializerApplication {
    private KafkaMaterializerApplication() {
    }

    public static void main(String[] args) throws Exception {
        String bootstrapServers = System.getenv().getOrDefault("FUNNEL_PROOF_KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:9092");
        String groupId = System.getenv().getOrDefault("FUNNEL_PROOF_KAFKA_CONSUMER_GROUP", "funnelproof-local-bronze-v1");
        int pollMilliseconds = Integer.parseInt(System.getenv().getOrDefault("FUNNEL_PROOF_KAFKA_POLL_MS", "1000"));
        Path dataDirectory = Path.of(System.getenv().getOrDefault("FUNNEL_PROOF_DATA_DIR", ".funnel-proof/events"));
        Path deadLetterDirectory = Path.of(System.getenv().getOrDefault("FUNNEL_PROOF_DEAD_LETTER_DIR", ".funnel-proof/dead-letter"));

        try (KafkaConsumerClient consumer = new ApacheKafkaConsumerClient(
                bootstrapServers, groupId, KafkaEventRecordFactory.ACCEPTED_EVENTS_TOPIC
        )) {
            KafkaMaterializationResult result = new KafkaEventMaterializer().materializeOnce(
                    consumer,
                    new FileEventStore(dataDirectory),
                    new FileDeadLetterSink(deadLetterDirectory),
                    Duration.ofMillis(pollMilliseconds)
            );
            System.out.printf(
                    "Kafka materializer poll complete: polled=%d materialized=%d dead_lettered=%d committed_partitions=%d%n",
                    result.polled(), result.materialized(), result.deadLettered(), result.committedOffsets().size()
            );
        }
    }
}
