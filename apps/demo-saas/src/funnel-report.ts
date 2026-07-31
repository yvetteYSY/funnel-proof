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
