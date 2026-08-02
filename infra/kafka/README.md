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

`KafkaEventMaterializer` is the consumer-side worker. It writes to the durable event store before committing offsets. A structurally invalid Kafka record is recorded in a local safe-reason audit and then committed, preventing one poison message from blocking a partition. A storage failure is not committed and is replayed.

After starting the local broker and sending events with `FUNNEL_PROOF_EVENT_LOG=kafka`, run one bounded materializer poll:

```bash
cd services/collector
mvn -Dmaven.repo.local=../../.m2 compile exec:java \
  -Dexec.mainClass=dev.funnelproof.collector.KafkaMaterializerApplication
```

The default group is `funnelproof-local-bronze-v1`. Override it with `FUNNEL_PROOF_KAFKA_CONSUMER_GROUP` to perform an isolated replay. The command prints counts and committed-partition totals only; raw event payloads are not printed.
