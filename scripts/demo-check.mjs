import { pathToFileURL } from "node:url";

export function summarizeDemoReport(report) {
  if (report.metric_status !== "local_provisional") throw new Error("Unexpected funnel report type.");
  if (report.data_sla?.status !== "healthy") throw new Error("Data SLA is not healthy. Seed a scenario, then retry.");
  if (!Number.isInteger(report.accepted_event_count) || report.accepted_event_count < 1) {
    throw new Error("No accepted synthetic events found. Run make demo-data first.");
  }
  const funnel = (report.stages ?? []).map((stage) => `${stage.event_name}=${stage.users}`).join(" → ");
  const anomaly = report.anomaly?.status ?? "not_available";
  return `Demo check passed: accepted=${report.accepted_event_count}; ${funnel}; anomaly=${anomaly}`;
}

async function main() {
  const endpoint = process.env.FUNNEL_PROOF_INSIGHTS_URL ?? "http://127.0.0.1:8080/fp/insights/funnel";
  const workspaceKey = process.env.FUNNEL_PROOF_WORKSPACE_KEY ?? "fp_public_local_demo";
  const response = await fetch(endpoint, { headers: { "x-funnel-proof-workspace-key": workspaceKey } });
  if (!response.ok) throw new Error(`Collector insights request failed with HTTP ${response.status}. Run make demo-start first.`);
  console.log(summarizeDemoReport(await response.json()));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : "Unable to verify the local demo.");
    process.exitCode = 1;
  });
}
