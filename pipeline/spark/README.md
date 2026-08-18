# Canonical Spark rebuild

`CanonicalRebuildJob` is the batch source of truth for FunnelProof reporting. It reads an explicit event-date range from Iceberg Bronze, writes a typed, deduplicated Silver replacement for that range, and writes a deterministic Gold funnel snapshot.

## Correctness and scale

- Silver chooses the earliest accepted record for each `(workspace_id, event_id)` in the requested range. Retrying the same job replaces, rather than appends, the affected Iceberg partitions.
- Gold requires ordered first occurrences of `vpv → signup_completed → activation_completed → subscription_started` for an anonymous identity. The stage event date determines its reporting date.
- Gold reads a bounded historical lookback (30 days by default) before the target range, so a stage completed just after midnight can still be matched to an earlier prerequisite. A backfill with a longer business conversion window must set `--lookback-days` accordingly.
- The job enables Adaptive Query Execution and performs dedupe/window/group operations on executors. It does not cache frames or collect event rows to the driver.

The pure Scala rules in `FunnelRules` are unit-tested separately from Spark. They make the business rule auditable and prevent an engine implementation detail from silently changing the funnel definition.

## Build and submit

```bash
make spark-test
mvn -f pipeline/spark/pom.xml -Dmaven.repo.local=.m2 package

spark-submit \
  --class dev.funnelproof.pipeline.spark.CanonicalRebuildJob \
  --packages org.apache.iceberg:iceberg-spark-runtime-4.1_2.13:1.11.0 \
  pipeline/spark/target/spark-canonical-rebuild-0.1.0-SNAPSHOT.jar \
  --bronze-table lake.funnelproof.bronze_events_v1 \
  --silver-table lake.funnelproof.silver_events_v1 \
  --gold-table lake.funnelproof.gold_funnel_daily_v1 \
  --start-date 2026-08-18 \
  --end-date 2026-08-18 \
  --lookback-days 30
```

The Spark cluster must define the `lake` Iceberg catalog and its S3-compatible credentials through its deployment configuration. Neither credentials nor endpoints are stored here. Create the tables first with [`../iceberg/silver_events.sql`](../iceberg/silver_events.sql) and [`../iceberg/gold_funnel_daily.sql`](../iceberg/gold_funnel_daily.sql).

This job is build-tested. A local Kafka + MinIO + Spark integration run is a later, synthetic-data-only step; it remains local and does not create a paid account or cloud resource.
