# Local collector

The starter collector is a dependency-light Java 21 HTTP service. It accepts only the V1 event contract at `POST /fp/collect`, validates privacy and timestamp rules before persistence, and writes accepted events to a local append-only NDJSON store.

Each event is scoped to the authenticated local workspace and deduplicated by `event_id`, so a browser retry is acknowledged without a second write. The store contains only events that passed the privacy filter; it never stores the workspace key itself. It is a local development stand-in for privacy-filtered Bronze storage, not a production database.

`GET /fp/insights/funnel` returns a deterministic, **local provisional** trial-to-paid funnel for the authenticated workspace. It recomputes the ordered anonymous journey (`vpv` → signup → activation → subscription) from the stored events and suppresses commentary if the five-minute collector-freshness objective is unhealthy. It does not call an AI service or expose raw event data.

This phase still does not create cloud resources, Kafka records, or logs containing payloads. Kafka remains the hosted scale-out ingestion sink; its producer should preserve the same stable `event_id` and workspace scope.

## Run locally

```bash
cd services/collector
mvn -Dmaven.repo.local=../../.m2 test
mvn -Dmaven.repo.local=../../.m2 compile exec:java -Dexec.mainClass=dev.funnelproof.collector.CollectorApplication
```

The collector listens only on `127.0.0.1:8080` by default. Set `FUNNEL_PROOF_PORT` to choose another local port.

By default, the demo is authorized with the local-only pair `fp_public_local_demo` → `demo_workspace`. To choose your own values without committing them, set `FUNNEL_PROOF_WORKSPACE_KEY` and `FUNNEL_PROOF_WORKSPACE_ID` in your shell before starting the collector. The event files default to `.funnel-proof/events/`; override this local path with `FUNNEL_PROOF_DATA_DIR` if needed. The `.funnel-proof/` directory is ignored by Git.

To view the synthetic demo's funnel after generating its events, use the same workspace key:

```bash
curl -H 'x-funnel-proof-workspace-key: fp_public_local_demo' \
  http://127.0.0.1:8080/fp/insights/funnel
```
