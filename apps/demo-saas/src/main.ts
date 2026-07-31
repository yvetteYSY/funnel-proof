import { createTracker } from "../../../packages/web-sdk/src/index.js";

const status = requiredElement<HTMLOutputElement>("status");
const consent = requiredElement<HTMLInputElement>("analytics-consent");
const tracker = createTracker({
  endpoint: "/fp/collect",
  workspaceKey: "fp_public_local_demo",
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

async function send(successMessage: string, action: () => Promise<unknown>): Promise<void> {
  try {
    await action();
    status.value = successMessage;
  } catch (error) {
    status.value = error instanceof Error ? error.message : "Event could not be recorded.";
  }
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
