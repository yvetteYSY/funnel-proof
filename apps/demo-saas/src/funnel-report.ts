export interface FunnelStage {
  event_name: string;
  users: number;
  conversion_from_previous: number | null;
}

export interface FunnelReport {
  accepted_event_count: number;
  data_sla: {
    status: "healthy" | "stale" | "no_data";
    age_seconds?: number;
    objective?: string;
  };
  stages: FunnelStage[];
  commentary: {
    status: string;
    summary: string;
  };
}

const stageLabels: Readonly<Record<string, string>> = {
  vpv: "First-party page view",
  signup_completed: "Signup completed",
  activation_completed: "Activation completed",
  subscription_started: "Subscription started"
};

export function labelForStage(eventName: string): string {
  return stageLabels[eventName] ?? eventName;
}

export function formatConversion(rate: number | null): string {
  return rate === null ? "—" : `${(rate * 100).toFixed(1)}%`;
}

export function dataHealthSummary(report: FunnelReport): string {
  if (report.data_sla.status === "healthy") {
    return `Data SLA healthy · ${report.accepted_event_count} accepted event${report.accepted_event_count === 1 ? "" : "s"}`;
  }
  if (report.data_sla.status === "no_data") return "No accepted events yet · funnel commentary is suppressed";
  return "Data SLA stale · funnel commentary is suppressed";
}

export interface DashboardStage {
  eventName: string;
  label: string;
  users: number;
  conversion: string;
  shareOfEntry: number;
}

export function dashboardStages(report: FunnelReport): DashboardStage[] {
  const entryUsers = report.stages[0]?.users ?? 0;
  return report.stages.map((stage) => ({
    eventName: stage.event_name,
    label: labelForStage(stage.event_name),
    users: stage.users,
    conversion: formatConversion(stage.conversion_from_previous),
    shareOfEntry: entryUsers === 0 ? 0 : Math.round((stage.users / entryUsers) * 100)
  }));
}

export function largestDropoff(report: FunnelReport): string {
  const stage = report.stages.slice(1).reduce<FunnelStage | undefined>((lowest, candidate) => {
    if (candidate.conversion_from_previous === null) return lowest;
    if (lowest?.conversion_from_previous === null || lowest === undefined) return candidate;
    return candidate.conversion_from_previous < lowest.conversion_from_previous ? candidate : lowest;
  }, undefined);

  if (!stage || stage.conversion_from_previous === null) return "No qualified stage transition yet.";
  return `${labelForStage(stage.event_name)} has the largest observed drop-off: ${formatConversion(stage.conversion_from_previous)} continue from the prior stage.`;
}

export interface AnomalySummary {
  status: "paused" | "baseline_pending";
  title: string;
  detail: string;
}

/**
 * The local report does not yet expose a time-series anomaly result. This presentation keeps that
 * limitation visible rather than fabricating a business finding from raw or insufficient data.
 */
export function anomalySummary(report: FunnelReport): AnomalySummary {
  if (report.data_sla.status !== "healthy") {
    return {
      status: "paused",
      title: "Checks paused",
      detail: "Anomaly findings stay suppressed until the data freshness objective is healthy."
    };
  }
  return {
    status: "baseline_pending",
    title: "Baseline pending",
    detail: "The privacy-safe detector needs at least 7 daily aggregate observations before it can evaluate a funnel change."
  };
}
