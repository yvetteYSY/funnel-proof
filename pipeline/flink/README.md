# Kafka-to-Iceberg Bronze writer

`BronzeStreamingJob` is the production-shaped stream processor for the collector's accepted-event Kafka envelope. It stores every valid accepted event in the Iceberg Bronze table.

## Correctness behavior

- It reads `funnelproof.accepted-events.v1` with a stable consumer group and begins at the earliest available offset for a new group.
- It assigns event time from `event.occurred_at` and permits two hours of out-of-order arrival by default. The watermark is deliberately **not** an ingestion cutoff: all accepted events still reach Bronze for later historical correction.
- It keys state by `workspace_id + event_id`; the first occurrence inside the configured seven-day state-retention window is emitted. The keyed state and consumer offsets are committed through Flink exactly-once checkpoints.
- Iceberg's Flink sink commits files on successful checkpoints. Spark remains the canonical reconciler: it deduplicates the full Bronze history and rewrites impacted Gold date partitions, including records that arrive after the stream dedupe window.

This is an end-to-end *effectively-once* design, not a blanket exactly-once claim. `event_id` plus the deterministic downstream reconciliation make replays and backfills safe.

## Build

```bash
make flink-test
make flink-package
```

The second command produces `pipeline/flink/target/flink-bronze-writer-0.1.0-SNAPSHOT.jar`. The jar bundles the Kafka connector, Iceberg runtime, and JSON parser; the Flink distribution supplies Flink itself. A deployment must also install the filesystem connector matching its table location (for example, the S3 filesystem plugin for `s3a://`).

## Required environment

```bash
export FUNNEL_PROOF_KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092
export FUNNEL_PROOF_KAFKA_TOPIC=funnelproof.accepted-events.v1
export FUNNEL_PROOF_FLINK_CONSUMER_GROUP=funnelproof-bronze-v1
export FUNNEL_PROOF_ICEBERG_BRONZE_LOCATION=s3a://funnelproof/warehouse/funnelproof/bronze_events_v1
export FUNNEL_PROOF_FLINK_CHECKPOINT_STORAGE=s3a://funnelproof/checkpoints/bronze-v1
```

Optional tuning variables are `FUNNEL_PROOF_ALLOWED_LATENESS_HOURS` (default `2`), `FUNNEL_PROOF_DEDUPE_RETENTION_DAYS` (default `7`), and `FUNNEL_PROOF_CHECKPOINT_INTERVAL_SECONDS` (default `30`). There are no credentials in this module: provide S3-compatible credentials through the runtime's secret mechanism, never committed environment files.

Create the Iceberg table using [`../iceberg/bronze_events.sql`](../iceberg/bronze_events.sql), then submit the jar to a Flink 2.1.x cluster:

```bash
flink run -c dev.funnelproof.pipeline.flink.BronzeStreamingJob \
  pipeline/flink/target/flink-bronze-writer-0.1.0-SNAPSHOT.jar
```

For local learning, use only the synthetic demo workspace and a local S3-compatible endpoint. This repository does not create a cloud account or provision any paid resource.
