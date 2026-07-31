# Contracts

Contracts are the source of truth shared by the SDK, collector, processing jobs, and tests.

- `events/v1/event-envelope.schema.json` defines an accepted, privacy-filtered event.
- `tracking-plans/b2b-saas.v1.json` defines the V1 primitive vocabulary and canonical trial-to-paid funnel.

Rules:

1. Never repurpose an existing field or event name with a new meaning.
2. Add optional fields only when the schema and tracking plan explicitly allow them.
3. Add a fixture under `tests/contracts/` for every accepted schema change.
4. Breaking changes create a new versioned file; backfills retain their source version.
