package dev.funnelproof.collector;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs a short, bounded Kafka drain. The first Kafka poll can perform group assignment without
 * returning records, so the default allows a second poll before declaring an idle topic.
 */
public final class KafkaMaterializerApplication {
    private KafkaMaterializerApplication() {
    }

    public static void main(String[] args) throws Exception {
        String bootstrapServers = System.getenv().getOrDefault("FUNNEL_PROOF_KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:9092");
        String groupId = System.getenv().getOrDefault("FUNNEL_PROOF_KAFKA_CONSUMER_GROUP", "funnelproof-local-bronze-v1");
        int pollMilliseconds = Integer.parseInt(System.getenv().getOrDefault("FUNNEL_PROOF_KAFKA_POLL_MS", "1000"));
        int maxPolls = Math.max(2, Integer.parseInt(System.getenv().getOrDefault("FUNNEL_PROOF_KAFKA_MAX_POLLS", "2")));
        Path dataDirectory = Path.of(System.getenv().getOrDefault("FUNNEL_PROOF_DATA_DIR", ".funnel-proof/events"));
        Path deadLetterDirectory = Path.of(System.getenv().getOrDefault("FUNNEL_PROOF_DEAD_LETTER_DIR", ".funnel-proof/dead-letter"));

        try (KafkaConsumerClient consumer = new ApacheKafkaConsumerClient(
                bootstrapServers, groupId, KafkaEventRecordFactory.ACCEPTED_EVENTS_TOPIC
        )) {
            KafkaEventMaterializer materializer = new KafkaEventMaterializer();
            FileEventStore eventStore = new FileEventStore(dataDirectory);
            FileDeadLetterSink deadLetterSink = new FileDeadLetterSink(deadLetterDirectory);
            long polled = 0;
            long materialized = 0;
            long deadLettered = 0;
            Map<String, KafkaConsumerOffset> committedOffsets = new LinkedHashMap<>();
            boolean receivedRecords = false;

            for (int poll = 0; poll < maxPolls; poll++) {
                KafkaMaterializationResult result = materializer.materializeOnce(
                        consumer, eventStore, deadLetterSink, Duration.ofMillis(pollMilliseconds)
                );
                polled += result.polled();
                materialized += result.materialized();
                deadLettered += result.deadLettered();
                for (KafkaConsumerOffset offset : result.committedOffsets()) {
                    committedOffsets.put(offset.topic() + ":" + offset.partition(), offset);
                }
                if (result.polled() > 0) {
                    receivedRecords = true;
                } else if (receivedRecords) {
                    break;
                }
            }
            System.out.printf(
                    "Kafka materializer drain complete: polled=%d materialized=%d dead_lettered=%d committed_partitions=%d%n",
                    polled, materialized, deadLettered, committedOffsets.size()
            );
        }
    }
}
