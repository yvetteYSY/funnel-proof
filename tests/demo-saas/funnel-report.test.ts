import { describe, expect, it } from "vitest";
import { dataHealthSummary, formatConversion, labelForStage, type FunnelReport } from "../../apps/demo-saas/src/funnel-report.js";

const healthyReport: FunnelReport = {
  accepted_event_count: 4,
  data_sla: { status: "healthy", age_seconds: 3 },
  stages: [],
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
});
