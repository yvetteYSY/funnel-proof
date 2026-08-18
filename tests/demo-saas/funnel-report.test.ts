import { describe, expect, it } from "vitest";
import {
  anomalySummary,
  dashboardStages,
  dataHealthSummary,
  formatConversion,
  labelForStage,
  largestDropoff,
  type FunnelReport
} from "../../apps/demo-saas/src/funnel-report.js";

const healthyReport: FunnelReport = {
  accepted_event_count: 4,
  data_sla: { status: "healthy", age_seconds: 3 },
  stages: [
    { event_name: "vpv", users: 10, conversion_from_previous: null },
    { event_name: "signup_completed", users: 5, conversion_from_previous: 0.5 },
    { event_name: "activation_completed", users: 4, conversion_from_previous: 0.8 },
    { event_name: "subscription_started", users: 1, conversion_from_previous: 0.25 }
  ],
  commentary: { status: "informational", summary: "Example" }
};

describe("local funnel report presentation", () => {
  it("uses business-readable stage and conversion labels", () => {
    expect(labelForStage("activation_completed")).toBe("Activation completed");
    expect(formatConversion(0.625)).toBe("62.5%");
    expect(formatConversion(null)).toBe("—");
  });

  it("makes data health visible before showing an insight", () => {
    expect(dataHealthSummary(healthyReport)).toBe("Data SLA healthy · 4 accepted events");
    expect(dataHealthSummary({ ...healthyReport, data_sla: { status: "stale" } })).toContain("commentary is suppressed");
  });

  it("creates aggregate dashboard stages and identifies the largest drop-off", () => {
    expect(dashboardStages(healthyReport)).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: "First-party page view", users: 10, shareOfEntry: 100 }),
      expect.objectContaining({ label: "Signup completed", users: 5, shareOfEntry: 50 })
    ]));
    expect(largestDropoff(healthyReport)).toContain("Subscription started");
    expect(largestDropoff(healthyReport)).toContain("25.0%");
  });

  it("does not present an anomaly finding before a healthy aggregate baseline exists", () => {
    expect(anomalySummary(healthyReport)).toMatchObject({ status: "baseline_pending", title: "Baseline pending" });
    expect(anomalySummary({ ...healthyReport, data_sla: { status: "stale" } })).toMatchObject({ status: "paused", title: "Checks paused" });
  });

  it("renders a detector result without exposing raw event data", () => {
    const report: FunnelReport = {
      ...healthyReport,
      anomaly: {
        metric: "daily_subscription_started_users",
        status: "anomaly",
        current_value: 1,
        baseline_points: 7,
        baseline_median: 5.5,
        robust_z_score: -6.07
      }
    };

    expect(anomalySummary(report)).toMatchObject({ status: "anomaly", title: "Change detected" });
    expect(anomalySummary(report).detail).toContain("1 subscriptions");
  });
});
