# Local Kafka profile

This optional profile runs one Apache Kafka broker on your machine only. It does not create a cloud resource, account, or paid service. The broker is intentionally bound to `127.0.0.1:9092`, so it is not exposed to your local network.

It requires a free local container runtime that provides the `docker` command, such as Colima, Docker Desktop, or Podman with Docker Compose compatibility. FunnelProof does not install or start that runtime automatically.

## Start and stop

```bash
docker compose -f infra/kafka/compose.yml up -d
docker compose -f infra/kafka/compose.yml down
```

The image is pinned to Apache Kafka `4.3.1`; it is the same version as the collector's `kafka-clients` dependency. For a throwaway local learning environment, data is intentionally ephemeral: `down` removes the broker container and its events.

## What the code does

With `FUNNEL_PROOF_EVENT_LOG=kafka`, the collector publishes privacy-filtered accepted-event envelopes to `funnelproof.accepted-events.v1`. Records are keyed by `workspace_id + anonymous_id`, preserving a browser journey's order within one Kafka partition.

`KafkaEventMaterializer` is the consumer-side worker foundation. It writes to the durable event store before committing offsets. A structurally invalid Kafka record is recorded in a local safe-reason audit and then committed, preventing one poison message from blocking a partition. A storage failure is not committed and is replayed.

The consumer is covered by unit tests but is not started automatically. This keeps the default demo simple and local while we add the operational runner in the next slice.
