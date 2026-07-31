# Local collector

The starter collector is a dependency-light Java 21 HTTP service. It accepts only the V1 event contract at `POST /fp/collect`, validates privacy and timestamp rules before persistence, and keeps accepted events in memory for local development.

It deliberately does not write customer data, logs, Kafka records, or cloud resources in this phase. A future sink will implement the same `EventStore` interface for PostgreSQL/ClickHouse locally and Kafka in a hosted deployment.

## Run locally

```bash
cd services/collector
mvn -Dmaven.repo.local=../../.m2 test
mvn -Dmaven.repo.local=../../.m2 compile exec:java -Dexec.mainClass=dev.funnelproof.collector.CollectorApplication
```

The collector listens only on `127.0.0.1:8080` by default. Set `FUNNEL_PROOF_PORT` to choose another local port.
