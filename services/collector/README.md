# Local collector

The starter collector is a dependency-light Java 21 HTTP service. It accepts only the V1 event contract at `POST /fp/collect`, validates privacy and timestamp rules, publishes an internal accepted-event record to a local durable event log, then materializes its local append-only NDJSON store.

Each event is scoped to the authenticated local workspace and deduplicated by `event_id`, so a browser retry is acknowledged without a second write. The local event log uses the Kafka-compatible topic `funnelproof.accepted-events.v1` and a stable partition key derived from `workspace_id + anonymous_id`; the raw workspace key is never stored. This gives one browser journey ordered records while allowing different browser journeys in a workspace to distribute across future Kafka partitions.

The local log is a free stand-in for Kafka, not a Kafka broker. It lets the collector exercise the important sequence—durably publish first, then materialize the funnel store—and lets a retry complete materialization after a local write failure. Its data directory defaults to `.funnel-proof/event-log/`; the local read-model directory remains `.funnel-proof/events/`. Both are ignored by Git.

`GET /fp/insights/funnel` returns a deterministic, **local provisional** trial-to-paid funnel for the authenticated workspace. It recomputes the ordered anonymous journey (`vpv` → signup → activation → subscription) from the stored events and suppresses commentary if the five-minute collector-freshness objective is unhealthy. It does not call an AI service or expose raw event data.

This phase does not create cloud resources or run a Kafka broker. The future Kafka producer will implement the same `EventLog` boundary with idempotent production, the same stable `event_id`, and the same partition key. The local log contains only privacy-filtered accepted events and never logs rejected request payloads.

## Run locally

```bash
cd services/collector
mvn -Dmaven.repo.local=../../.m2 test
mvn -Dmaven.repo.local=../../.m2 compile exec:java -Dexec.mainClass=dev.funnelproof.collector.CollectorApplication
```

The collector listens only on `127.0.0.1:8080` by default. Set `FUNNEL_PROOF_PORT` to choose another local port.

By default, the demo is authorized with the local-only pair `fp_public_local_demo` → `demo_workspace`. To choose your own values without committing them, set `FUNNEL_PROOF_WORKSPACE_KEY` and `FUNNEL_PROOF_WORKSPACE_ID` in your shell before starting the collector. Override the local read-model path with `FUNNEL_PROOF_DATA_DIR` or the local event-log path with `FUNNEL_PROOF_EVENT_LOG_DIR` if needed. The `.funnel-proof/` directory is ignored by Git.

To view the synthetic demo's funnel after generating its events, use the same workspace key:

```bash
curl -H 'x-funnel-proof-workspace-key: fp_public_local_demo' \
  http://127.0.0.1:8080/fp/insights/funnel
```
