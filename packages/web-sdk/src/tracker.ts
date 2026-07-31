import { isBusinessEventName, normalizePagePath, PrivacyValidationError, validateProperties } from "./privacy.js";
import { FetchTransport } from "./transport.js";
import type {
  BusinessEventName,
  ClientEvent,
  CollectionRequest,
  EventName,
  EventPropertiesByName,
  EventTransport,
  TrackerOptions
} from "./types.js";

export class ConsentRequiredError extends Error {
  constructor() {
    super("Analytics consent is required before FunnelProof can emit an event");
    this.name = "ConsentRequiredError";
  }
}

export class FunnelProofTracker {
  private analyticsConsent = false;
  private readonly transport: EventTransport;
  private readonly now: () => Date;
  private readonly eventIdFactory: () => string;
  private readonly sessionId: string;
  private readonly sdkVersion: string;

  constructor(private readonly options: TrackerOptions) {
    if (!options.endpoint.startsWith("/") && !options.endpoint.startsWith("https://")) {
      throw new PrivacyValidationError("endpoint must be first-party relative or HTTPS");
    }
    if (options.workspaceKey.length < 8) {
      throw new PrivacyValidationError("workspaceKey is required");
    }
    if (options.anonymousId.length < 16) {
      throw new PrivacyValidationError("anonymousId must be a pseudonymous identifier of at least 16 characters");
    }

    this.transport = options.transport ?? new FetchTransport();
    this.now = options.now ?? (() => new Date());
    this.eventIdFactory = options.eventIdFactory ?? createId;
    this.sessionId = options.sessionId ?? createId();
    this.sdkVersion = options.sdkVersion ?? "0.1.0";
  }

  grantAnalyticsConsent(): void {
    this.analyticsConsent = true;
  }

  withdrawAnalyticsConsent(): void {
    this.analyticsConsent = false;
  }

  async captureVirtualPageView(rawUrl: string): Promise<ClientEvent<"vpv">> {
    const pagePath = normalizePagePath(rawUrl);
    return this.emit("vpv", { page_path: pagePath });
  }

  async track<TName extends BusinessEventName>(
    eventName: TName,
    properties: EventPropertiesByName[TName]
  ): Promise<ClientEvent<TName>> {
    if (!isBusinessEventName(eventName)) {
      throw new PrivacyValidationError("vpv must be emitted through captureVirtualPageView");
    }
    return this.emit(eventName, properties);
  }

  private async emit<TName extends EventName>(
    eventName: TName,
    properties: EventPropertiesByName[TName]
  ): Promise<ClientEvent<TName>> {
    if (!this.analyticsConsent) throw new ConsentRequiredError();
    validateProperties(eventName, properties);

    const occurredAt = this.now();
    if (Number.isNaN(occurredAt.getTime())) {
      throw new PrivacyValidationError("clock returned an invalid event timestamp");
    }

    const event: ClientEvent<TName> = {
      event_id: this.eventIdFactory(),
      event_name: eventName,
      occurred_at: occurredAt.toISOString(),
      anonymous_id: this.options.anonymousId,
      session_id: this.sessionId,
      properties,
      context: { platform: "web", sdk_version: this.sdkVersion },
      schema_version: "1.0.0",
      tracking_plan_version: "1.0.0",
      funnel_definition_version: "1.0.0",
      consent: { analytics: true }
    };

    const request: CollectionRequest<TName> = {
      endpoint: this.options.endpoint,
      workspaceKey: this.options.workspaceKey,
      event
    };
    await this.transport.send(request);
    return event;
  }
}

export function createTracker(options: TrackerOptions): FunnelProofTracker {
  return new FunnelProofTracker(options);
}

function createId(): string {
  if (typeof globalThis.crypto?.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }

  return `fp_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 18)}`;
}
