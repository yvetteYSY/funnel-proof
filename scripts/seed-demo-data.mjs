import { pathToFileURL } from "node:url";

const SCENARIOS = new Set(["healthy", "signup-dropoff", "payment-dropoff", "late-arrivals", "retries", "subscription-anomaly"]);
const STAGES = ["vpv", "signup_completed", "activation_completed", "subscription_started"];

export function supportedScenarios() {
  return [...SCENARIOS];
}

export function buildScenarioEvents(scenario, now = new Date()) {
  if (!SCENARIOS.has(scenario)) throw new Error(`Unknown scenario: ${scenario}. Choose one of: ${supportedScenarios().join(", ")}`);
  if (scenario === "subscription-anomaly") return subscriptionAnomalyEvents(now);

  const baseTime = new Date(now.getTime() - 30 * 60 * 1000);
  const plan = scenarioPlan(scenario);
  const events = [];
  for (let user = 1; user <= plan.pageViews; user += 1) {
    const journey = ["vpv"];
    if (user <= plan.signups) journey.push("signup_completed");
    if (user <= plan.activations) journey.push("activation_completed");
    if (user <= plan.subscriptions) journey.push("subscription_started");

    for (const eventName of journey) {
      events.push(seedEvent(scenario, user, eventName, baseTime));
    }
  }

  if (scenario === "late-arrivals") {
    // Send later funnel steps before earlier event-time records. The collector report must still
    // order each anonymous journey by occurred_at, not arrival order.
    return events.sort((left, right) => right.deliveryRank - left.deliveryRank || left.event.event_id.localeCompare(right.event.event_id));
  }
  if (scenario === "retries") {
    // A browser retry resends exactly the same stable event_id; it must not create a second user.
    return [...events, events[0]];
  }
  return events;
}

function subscriptionAnomalyEvents(now) {
  // Seven historical observed days vary narrowly around 6–8 subscriptions. The latest event-time
  // day has one subscription, which should cross the robust MAD threshold without any raw data.
  const subscriptionsByDay = [6, 7, 8, 7, 8, 6, 8, 1];
  const firstDay = new Date(now.getTime() - 8 * 24 * 60 * 60 * 1000);
  const events = [];
  for (const [day, subscriptions] of subscriptionsByDay.entries()) {
    const dayStart = new Date(firstDay.getTime() + day * 24 * 60 * 60 * 1000);
    for (let user = 1; user <= subscriptions; user += 1) {
      const identity = `day${String(day + 1).padStart(2, "0")}_user${String(user).padStart(3, "0")}`;
      for (const eventName of STAGES) events.push(seedEvent("subscription-anomaly", identity, eventName, dayStart));
    }
  }
  return events;
}

export async function seedScenario({ scenario, endpoint, workspaceKey, now = new Date(), fetchImpl = fetch }) {
  const records = buildScenarioEvents(scenario, now);
  let accepted = 0;
  let duplicates = 0;

  for (const record of records) {
    const response = await fetchImpl(endpoint, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-funnel-proof-workspace-key": workspaceKey
      },
      body: JSON.stringify(record.event)
    });
    if (!response.ok) throw new Error(`Collector rejected the ${scenario} seed with HTTP ${response.status}.`);
    const result = await response.json();
    if (!result.accepted) throw new Error(`Collector rejected the ${scenario} seed.`);
    if (result.duplicate) duplicates += 1;
    else accepted += 1;
  }
  return { scenario, attempted: records.length, accepted, duplicates };
}

function scenarioPlan(scenario) {
  return {
    healthy: { pageViews: 12, signups: 9, activations: 7, subscriptions: 4 },
    "signup-dropoff": { pageViews: 12, signups: 3, activations: 2, subscriptions: 1 },
    "payment-dropoff": { pageViews: 12, signups: 10, activations: 8, subscriptions: 2 },
    "late-arrivals": { pageViews: 4, signups: 4, activations: 4, subscriptions: 2 },
    retries: { pageViews: 2, signups: 1, activations: 1, subscriptions: 1 }
  }[scenario];
}

function seedEvent(scenario, userNumber, eventName, baseTime) {
  const stage = STAGES.indexOf(eventName);
  const occurredAt = new Date(baseTime.getTime() + stage * 60_000).toISOString();
  const suffix = String(userNumber).padStart(3, "0");
  const eventSuffix = `${eventName.replace("_completed", "").replace("_started", "")}_${suffix}`;
  return {
    deliveryRank: stage,
    event: {
      event_id: `seed_${scenario}_${eventSuffix}`,
      event_name: eventName,
      occurred_at: occurredAt,
      anonymous_id: `seed_${scenario}_anonymous_${suffix}`,
      session_id: `seed_${scenario}_session_${suffix}`,
      properties: propertiesFor(eventName),
      context: { platform: "web", sdk_version: "0.1.0" },
      schema_version: "1.0.0",
      tracking_plan_version: "1.0.0",
      funnel_definition_version: "1.0.0",
      consent: { analytics: true }
    }
  };
}

function propertiesFor(eventName) {
  return {
    vpv: { page_path: "/pricing" },
    signup_completed: { signup_method: "github" },
    activation_completed: { activation_action: "first_project_created" },
    subscription_started: { plan: "pro", billing_interval: "monthly" }
  }[eventName];
}

async function main() {
  const scenario = process.argv[2] ?? "healthy";
  const result = await seedScenario({
    scenario,
    endpoint: process.env.FUNNEL_PROOF_COLLECTOR_URL ?? "http://127.0.0.1:8080/fp/collect",
    workspaceKey: process.env.FUNNEL_PROOF_WORKSPACE_KEY ?? "fp_public_local_demo"
  });
  console.log(`Seeded ${result.scenario}: attempted=${result.attempted} accepted=${result.accepted} duplicates=${result.duplicates}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : "Unable to seed synthetic demo data.");
    process.exitCode = 1;
  });
}
