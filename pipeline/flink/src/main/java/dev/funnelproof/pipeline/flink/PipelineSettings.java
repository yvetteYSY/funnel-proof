package dev.funnelproof.pipeline.flink;

/** Environment-only configuration keeps credentials out of source and makes local/production deployment explicit. */
public record PipelineSettings(
        String kafkaBootstrapServers,
        String kafkaTopic,
        String kafkaConsumerGroup,
        String icebergBronzeLocation,
        String checkpointStorage,
        int allowedLatenessHours,
        int dedupeRetentionDays,
        int checkpointIntervalSeconds
) {
    public static PipelineSettings fromEnvironment() {
        return new PipelineSettings(
                value("FUNNEL_PROOF_KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:9092"),
                value("FUNNEL_PROOF_KAFKA_TOPIC", "funnelproof.accepted-events.v1"),
                value("FUNNEL_PROOF_FLINK_CONSUMER_GROUP", "funnelproof-bronze-v1"),
                required("FUNNEL_PROOF_ICEBERG_BRONZE_LOCATION"),
                required("FUNNEL_PROOF_FLINK_CHECKPOINT_STORAGE"),
                positive("FUNNEL_PROOF_ALLOWED_LATENESS_HOURS", 2),
                positive("FUNNEL_PROOF_DEDUPE_RETENTION_DAYS", 7),
                positive("FUNNEL_PROOF_CHECKPOINT_INTERVAL_SECONDS", 30)
        );
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String value(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int positive(String name, int defaultValue) {
        int value = Integer.parseInt(value(name, String.valueOf(defaultValue)));
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
