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
