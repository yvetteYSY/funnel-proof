# Tests

Tests evolve with each implementation slice. They cover event contracts, SDK privacy behavior, collector validation and HTTP behavior, local durability/idempotency, ordered funnel results, data-SLA commentary suppression, partition-key routing, and replay/checkpoint recovery.

Tests live at the repository level so contract, SDK, collector, and end-to-end behavior can evolve together.

- `contracts/` contains valid and invalid wire fixtures.
- `web-sdk/` contains privacy and event-construction unit tests.
- `collector/` contains Java collector unit tests.
- `integration/` is reserved for SDK-to-collector checks once local runtimes are installed.

Every code change must add or update the closest relevant test. `npm run verify` validates JSON fixtures, TypeScript types, and SDK tests. `make collector-test` runs the Java suite.
