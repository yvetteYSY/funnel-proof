import { createTracker } from "../../../packages/web-sdk/src/index.js";
import { dataHealthSummary, formatConversion, labelForStage, type FunnelReport } from "./funnel-report.js";

const status = requiredElement<HTMLOutputElement>("status");
const consent = requiredElement<HTMLInputElement>("analytics-consent");
const reportStatus = requiredElement<HTMLOutputElement>("report-status");
const reportStages = requiredElement<HTMLOListElement>("report-stages");
const reportCommentary = requiredElement<HTMLElement>("report-commentary");
const workspaceKey = "fp_public_local_demo";
const tracker = createTracker({
  endpoint: "/fp/collect",
  workspaceKey,
  anonymousId: browserLocalId("fp_demo_anonymous_id"),
  sdkVersion: "0.1.0"
});

consent.addEventListener("change", async () => {
  if (!consent.checked) {
    tracker.withdrawAnalyticsConsent();
    status.value = "Consent withdrawn. No further events will be sent.";
    return;
  }

  tracker.grantAnalyticsConsent();
  await send("Viewed local demo", () => tracker.captureVirtualPageView(window.location.href));
});

requiredElement<HTMLButtonElement>("signup").addEventListener("click", () =>
  send("Synthetic signup recorded", () => tracker.track("signup_completed", { signup_method: "github" }))
);
requiredElement<HTMLButtonElement>("activation").addEventListener("click", () =>
  send("Synthetic activation recorded", () =>
    tracker.track("activation_completed", { activation_action: "first_project_created" })
  )
);
requiredElement<HTMLButtonElement>("subscription").addEventListener("click", () =>
  send("Synthetic subscription recorded", () =>
    tracker.track("subscription_started", { plan: "pro", billing_interval: "monthly" })
  )
);

requiredElement<HTMLButtonElement>("refresh-report").addEventListener("click", () => void refreshReport());

async function send(successMessage: string, action: () => Promise<unknown>): Promise<void> {
  try {
    await action();
    status.value = successMessage;
    await refreshReport();
  } catch (error) {
    status.value = error instanceof Error ? error.message : "Event could not be recorded.";
  }
}

async function refreshReport(): Promise<void> {
  reportStatus.value = "Loading local funnel report…";
  try {
    const response = await fetch("/fp/insights/funnel", {
      headers: { "x-funnel-proof-workspace-key": workspaceKey }
    });
    if (!response.ok) throw new Error("Collector is unavailable. Start it locally, then refresh this report.");
    renderReport((await response.json()) as FunnelReport);
  } catch (error) {
    reportStatus.value = error instanceof Error ? error.message : "Funnel report could not be loaded.";
    reportStages.replaceChildren();
    reportCommentary.textContent = "";
  }
}

function renderReport(report: FunnelReport): void {
  reportStatus.value = dataHealthSummary(report);
  reportStages.replaceChildren(
    ...report.stages.map((stage) => {
      const item = document.createElement("li");
      item.textContent = `${labelForStage(stage.event_name)}: ${stage.users} users · ${formatConversion(stage.conversion_from_previous)} from prior stage`;
      return item;
    })
  );
  reportCommentary.textContent = report.commentary.summary;
}

function browserLocalId(key: string): string {
  const existing = window.localStorage.getItem(key);
  if (existing) return existing;
  const created = window.crypto.randomUUID();
  window.localStorage.setItem(key, created);
  return created;
}

function requiredElement<TElement extends HTMLElement>(id: string): TElement {
  const element = document.getElementById(id);
  if (!element) throw new Error(`Missing required demo element: ${id}`);
  return element as TElement;
}
