# Local SaaS demo

The demo is a synthetic walkthrough of the V1 trial-to-paid funnel. It sends no customer data and cannot emit an event until its local consent checkbox is selected. Its **Refresh local funnel** button displays the collector's workspace-scoped provisional funnel, freshness status, and deterministic commentary; it does not call an AI service.

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
