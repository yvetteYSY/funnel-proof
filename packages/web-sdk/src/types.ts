export const BUSINESS_EVENT_NAMES = [
  "click",
  "signup_completed",
  "activation_completed",
  "subscription_started"
] as const;

export type BusinessEventName = (typeof BUSINESS_EVENT_NAMES)[number];
export type EventName = "vpv" | BusinessEventName;

export type EventPropertiesByName = {
  vpv: { page_path: string };
  click: { ui_name: string; ui_type?: "button" | "link" | "menu_item"; ui_surface?: string };
  signup_completed: { signup_method?: "email" | "google" | "github" | "sso" };
  activation_completed: { activation_action: string };
  subscription_started: {
    plan: string;
    billing_interval: "monthly" | "yearly";
    acquisition_channel?: string;
  };
};

export interface EventContext {
  platform: "web";
  sdk_version: string;
}

export interface ClientEvent<TName extends EventName = EventName> {
  event_id: string;
  event_name: TName;
  occurred_at: string;
  anonymous_id: string;
  session_id: string;
  properties: EventPropertiesByName[TName];
  context: EventContext;
  schema_version: "1.0.0";
  tracking_plan_version: "1.0.0";
  funnel_definition_version: "1.0.0";
  consent: { analytics: true };
}

export interface AcceptedEvent<TName extends EventName = EventName> extends ClientEvent<TName> {
  received_at: string;
  event_time_quality: "client_verified" | "server_assigned" | "quarantined";
}

export interface CollectionRequest<TName extends EventName = EventName> {
  endpoint: string;
  workspaceKey: string;
  event: ClientEvent<TName>;
}

export interface EventTransport {
  send(request: CollectionRequest): Promise<void>;
}

export interface TrackerOptions {
  /** A customer-controlled first-party collection endpoint, for example /fp/collect. */
  endpoint: string;
  workspaceKey: string;
  anonymousId: string;
  sessionId?: string;
  sdkVersion?: string;
  transport?: EventTransport;
  now?: () => Date;
  eventIdFactory?: () => string;
}
