import { describe, expect, it } from "vitest";
import { summarizeDemoReport } from "../../scripts/demo-check.mjs";

const report = {
  metric_status: "local_provisional",
  accepted_event_count: 12,
  data_sla: { status: "healthy" },
  stages: [
    { event_name: "vpv", users: 6 },
    { event_name: "signup_completed", users: 3 }
  ],
  anomaly: { status: "insufficient_history" }
};

describe("local demo check", () => {
  it("summarizes aggregate data without raw event fields", () => {
    expect(summarizeDemoReport(report)).toBe(
      "Demo check passed: accepted=12; vpv=6 → signup_completed=3; anomaly=insufficient_history"
    );
  });

  it("fails closed when the local data SLA is not healthy", () => {
    expect(() => summarizeDemoReport({ ...report, data_sla: { status: "stale" } })).toThrow("Data SLA is not healthy");
  });
});
