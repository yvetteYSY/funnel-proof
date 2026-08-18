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
  anomaly?: {
    metric: string;
    status: "anomaly" | "normal" | "insufficient_history" | "insufficient_variation" | "suppressed_data_sla_unhealthy";
    current_value: number;
    baseline_points: number;
    baseline_median?: number;
    robust_z_score?: number;
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
  status: "paused" | "baseline_pending" | "normal" | "anomaly";
  title: string;
  detail: string;
}

export function anomalySummary(report: FunnelReport): AnomalySummary {
  const anomaly = report.anomaly;
  if (anomaly?.status === "suppressed_data_sla_unhealthy" || report.data_sla.status !== "healthy") {
    return {
      status: "paused",
      title: "Checks paused",
      detail: "Anomaly findings stay suppressed until the data freshness objective is healthy."
    };
  }
  if (!anomaly) {
    return {
      status: "baseline_pending",
      title: "Baseline pending",
      detail: "The privacy-safe detector needs 7 prior daily aggregate observations before it can evaluate a funnel change."
    };
  }
  if (anomaly.status === "anomaly") {
    return {
      status: "anomaly",
      title: "Change detected",
      detail: `Today's ${formatNumber(anomaly.current_value)} subscriptions differs from the ${formatNumber(anomaly.baseline_median ?? 0)}-subscription baseline. Review the funnel before acting.`
    };
  }
  if (anomaly.status === "normal") {
    return {
      status: "normal",
      title: "Within expected range",
      detail: `Today's ${formatNumber(anomaly.current_value)} subscriptions is within the recent aggregate baseline.`
    };
  }
  if (anomaly.status === "insufficient_variation") {
    return {
      status: "baseline_pending",
      title: "Baseline too uniform",
      detail: "The detector needs natural variation in daily aggregate counts before it can distinguish a meaningful change."
    };
  }
  return {
    status: "baseline_pending",
    title: "Baseline pending",
    detail: `The privacy-safe detector needs 7 prior daily aggregate observations; ${anomaly.baseline_points} are available.`
  };
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 1 }).format(value);
}
