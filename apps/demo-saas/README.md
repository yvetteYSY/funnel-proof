# Local SaaS demo

The demo is a synthetic walkthrough of the V1 trial-to-paid funnel and local business-owner dashboard. It sends no customer data and cannot emit an event until its local consent checkbox is selected. **Refresh dashboard** displays aggregate funnel conversion, the collector's freshness/SLA status, deterministic commentary, and an honest anomaly guardrail state. It does not call an AI service or render identifiers, URLs with query strings, or raw event payloads.

With Node.js, Java, and Maven installed:

```bash
# Terminal 1
cd services/collector
mvn -Dmaven.repo.local=../../.m2 test
mvn -Dmaven.repo.local=../../.m2 compile exec:java -Dexec.mainClass=dev.funnelproof.collector.CollectorApplication

# Terminal 2
npm install
npm run demo
```

Open the local Vite URL, consent, then click the three synthetic funnel actions. The Vite proxy forwards `/fp/collect` and `/fp/insights/funnel` to the loopback-only collector.

## Repeatable synthetic scenarios

With the collector running, seed a scenario without clicking through the UI:

```bash
make demo-data                         # healthy: 12 views → 9 signups → 7 activations → 4 subscriptions
make demo-data SCENARIO=signup-dropoff
make demo-data SCENARIO=payment-dropoff
make demo-data SCENARIO=late-arrivals  # delivered out of order; funnel still uses occurred_at
make demo-data SCENARIO=retries        # resends one stable event_id to demonstrate dedupe
```

The supported scenarios emit only allowlisted, synthetic properties. Each scenario uses stable synthetic event IDs, so rerunning the same scenario is idempotent: the collector reports duplicates rather than inflating the funnel. Use a new local collector data directory when you want an empty dashboard.
