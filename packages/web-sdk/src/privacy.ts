import type { BusinessEventName, EventName, EventPropertiesByName } from "./types.js";

const IDENTIFIER_PATTERN = /^[a-z][a-z0-9_]{1,63}$/;
const SENSITIVE_KEY_PATTERN = /(email|password|token|secret|card|address|content|query|referrer|title|html|text)/i;

const allowedKeys: Record<EventName, readonly string[]> = {
  vpv: ["page_path"],
  click: ["ui_name", "ui_type", "ui_surface"],
  signup_completed: ["signup_method"],
  activation_completed: ["activation_action"],
  subscription_started: ["plan", "billing_interval", "acquisition_channel"]
};

const requiredKeys: Record<EventName, readonly string[]> = {
  vpv: ["page_path"],
  click: ["ui_name"],
  signup_completed: [],
  activation_completed: ["activation_action"],
  subscription_started: ["plan", "billing_interval"]
};

export class PrivacyValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "PrivacyValidationError";
  }
}

export function normalizePagePath(rawUrl: string): string {
  const url = new URL(rawUrl, "https://funnelproof.invalid");
  const path = url.pathname || "/";

  if (path.includes("?") || path.includes("#") || path.length > 512) {
    throw new PrivacyValidationError("vpv page_path must be a normalized route without query or fragment");
  }

  return path;
}

export function validateProperties<TName extends EventName>(
  eventName: TName,
  properties: EventPropertiesByName[TName]
): void {
  const supplied = Object.keys(properties);
  for (const key of supplied) {
    if (SENSITIVE_KEY_PATTERN.test(key)) {
      throw new PrivacyValidationError(`Property '${key}' is prohibited by the privacy policy`);
    }
    if (!allowedKeys[eventName].includes(key)) {
      throw new PrivacyValidationError(`Property '${key}' is not allowed for event '${eventName}'`);
    }
  }

  for (const key of requiredKeys[eventName]) {
    if (!(key in properties)) {
      throw new PrivacyValidationError(`Property '${key}' is required for event '${eventName}'`);
    }
  }

  validateValueShape(eventName, properties);
}

function validateValueShape<TName extends EventName>(
  eventName: TName,
  properties: EventPropertiesByName[TName]
): void {
  const values = properties as Record<string, string | undefined>;

  if (eventName === "vpv") {
    normalizePagePath(values.page_path ?? "");
  }

  if (eventName === "click") {
    validateIdentifier(values.ui_name ?? "", "ui_name");
    if (values.ui_surface) validateIdentifier(values.ui_surface, "ui_surface");
  }

  if (eventName === "activation_completed") {
    validateIdentifier(values.activation_action ?? "", "activation_action");
  }

  if (eventName === "subscription_started") {
    validateIdentifier(values.plan ?? "", "plan");
    if (values.acquisition_channel) validateIdentifier(values.acquisition_channel, "acquisition_channel");
  }
}

function validateIdentifier(value: string, field: string): void {
  if (!IDENTIFIER_PATTERN.test(value)) {
    throw new PrivacyValidationError(`${field} must be a registered lowercase snake_case identifier`);
  }
}

export function isBusinessEventName(eventName: string): eventName is BusinessEventName {
  return eventName !== "vpv" && eventName in allowedKeys;
}
