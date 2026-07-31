# FunnelProof

An open, privacy-first funnel-confidence service for independent B2B SaaS teams. FunnelProof helps a founder verify and understand the path from first visit to signup, activation, and paid subscription. The initial release targets SaaS websites and web apps; iOS follows using the same event contract.

## Design

Read the [business-first product and architecture design](docs/product-and-architecture-design.md).

## Starter implementation

The initial local-only vertical slice contains:

- `contracts/` — versioned event schema and the B2B SaaS tracking plan.
- `packages/web-sdk/` — privacy-first TypeScript SDK; it emits a minimal first-party `vpv` and explicit funnel events only after consent.
- `services/collector/` — Java 21 loopback collector that rejects unsafe events before its local in-memory sink.
- `apps/demo-saas/` — synthetic trial-to-paid walkthrough; it never sends real customer data.
- `tests/` — contract fixtures plus SDK and collector tests.

## Local verification

Install the free runtimes described in the design's zero-cost execution policy, then run:

```bash
make doctor
npm install
make verify
```

No cloud account, paid API, container runtime, or real customer data is required.

## Development cost

Development is local and zero-cost by default: no cloud resources, paid APIs, credit-card trials, or real customer data. See the design document's **Zero-cost execution policy** before running any project setup.
