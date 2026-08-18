import { describe, expect, it } from "vitest";
import { buildScenarioEvents, seedScenario, supportedScenarios } from "../../scripts/seed-demo-data.mjs";

describe("synthetic dashboard scenarios", () => {
  it("creates a deterministic healthy funnel using allowlisted event primitives", () => {
    const events = buildScenarioEvents("healthy", new Date("2026-01-01T12:00:00Z"));
    expect(events).toHaveLength(32);
    expect(events.filter(({ event }) => event.event_name === "vpv")).toHaveLength(12);
    expect(events.filter(({ event }) => event.event_name === "subscription_started")).toHaveLength(4);
    expect(events[0]?.event.properties).toEqual({ page_path: "/pricing" });
  });

  it("delivers a late-arrival scenario out of event-time order", () => {
    const events = buildScenarioEvents("late-arrivals", new Date("2026-01-01T12:00:00Z"));
    const firstJourney = events.filter(({ event }) => event.anonymous_id.endsWith("001"));
    expect(firstJourney[0]?.event.event_name).toBe("subscription_started");
    expect(firstJourney.at(-1)?.event.event_name).toBe("vpv");
    expect(firstJourney[0]?.event.occurred_at > firstJourney.at(-1)?.event.occurred_at).toBe(true);
  });

  it("resends a stable event ID in the retry scenario and reports collector dedupe", async () => {
    const events = buildScenarioEvents("retries", new Date("2026-01-01T12:00:00Z"));
    expect(events.at(-1)?.event.event_id).toBe(events[0]?.event.event_id);

    let requestCount = 0;
    const result = await seedScenario({
      scenario: "retries",
      endpoint: "http://collector.test/fp/collect",
      workspaceKey: "fp_public_local_demo",
      fetchImpl: async () => {
        requestCount += 1;
        return new Response(JSON.stringify({ accepted: true, duplicate: requestCount === events.length }), { status: 202 });
      }
    });
    expect(result).toMatchObject({ attempted: events.length, accepted: events.length - 1, duplicates: 1 });
  });

  it("lists the documented scenario names", () => {
    expect(supportedScenarios()).toEqual(["healthy", "signup-dropoff", "payment-dropoff", "late-arrivals", "retries"]);
  });
});
