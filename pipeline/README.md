# Distributed pipeline reference

This folder is the production-shaped, **local-first** data pipeline. It is intentionally separate from the starter collector: it can be exercised with synthetic events on one machine, but does not provision a cloud account, a managed cluster, or paid infrastructure.

## Compatibility baseline

- Apache Kafka 4.3.1 — accepted-event log
- Apache Flink 2.1.x — streaming validation, event-time handling, and bounded dedupe
- Apache Iceberg 1.11.x — Bronze/Silver/Gold table format on S3-compatible storage
- Apache Spark 4.1.x / Scala 2.13 — canonical hourly rebuilds and data-quality gates
- Apache Airflow 3.3.x — idempotent batch orchestration
- ClickHouse — tenant-scoped serving snapshots

Flink remains on the 2.1 line in this reference because Iceberg 1.11 publishes a matching runtime artifact. Version upgrades must be made as a tested engine-and-Iceberg compatibility change, never independently.

## Data contracts and ownership

| Layer | Owner | Contents | Correctness rule |
|---|---|---|---|
| Kafka accepted events | Collector / Flink | Privacy-filtered accepted event envelope | Partitioned by `workspace_id + anonymous_id`; stable `event_id` is preserved |
| Iceberg Bronze | Flink | Immutable accepted envelope plus Kafka lineage | Append-only; replayable |
| Iceberg Silver | Spark | Deduplicated, version-normalized event rows | Merge on `workspace_id + event_id` |
| Iceberg Gold | Spark | Canonical event-time daily funnel aggregates | Deterministic date-partition rewrite |
| ClickHouse serving | Spark / Flink | Aggregate snapshots only | Idempotent snapshot version per workspace/date |

The ClickHouse schema in [`clickhouse/`](clickhouse/) deliberately has no event IDs, anonymous IDs, URLs, or raw properties. The dashboard queries only this serving layer for ordinary reporting.

## Local execution policy

The runtime manifest is an optional distributed-learning profile. It is not part of `make verify`, the dashboard first run, or any paid deployment. It binds services to loopback addresses and must be run only with synthetic data.

Before running a future runtime command, make sure Colima (or another local container runtime) is active. Stop the profile after use to release local CPU, memory, and disk.

## Implemented streaming stage

The first distributed stage is now in [`flink/`](flink/): Kafka accepted envelopes flow into Iceberg Bronze through checkpointed, workspace-scoped event-ID deduplication. Its operational contract, build command, environment variables, and Flink submission command are documented in that folder. It is build-tested locally, but it is not presented as an already-running cluster; the next integration step is a synthetic local Kafka + MinIO + Flink run.
