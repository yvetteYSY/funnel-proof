import { describe, expect, it } from "vitest";
import {
  ConsentRequiredError,
  createTracker,
  PrivacyValidationError,
  type CollectionRequest,
  type EventTransport
} from "../../packages/web-sdk/src/index.js";

class RecordingTransport implements EventTransport {
  readonly requests: CollectionRequest[] = [];

  async send(request: CollectionRequest): Promise<void> {
    this.requests.push(request);
  }
}

function trackerForTest(transport = new RecordingTransport()) {
  return {
    transport,
    tracker: createTracker({
      endpoint: "/fp/collect",
      workspaceKey: "fp_public_test_key",
      anonymousId: "anonymous-browser-id-1234",
      sessionId: "session-id-1234567890",
      eventIdFactory: () => "018f4f70-8ab1-7cc4-a67d-fae57c0c0f99",
      now: () => new Date("2026-07-30T18:45:00.123Z"),
      transport
    })
  };
}

describe("FunnelProofTracker", () => {
  it("does not emit analytics before consent", async () => {
    const { tracker, transport } = trackerForTest();

    await expect(tracker.captureVirtualPageView("https://app.example.com/pricing?email=person@example.com#faq"))
      .rejects.toBeInstanceOf(ConsentRequiredError);
    expect(transport.requests).toHaveLength(0);
  });

  it("emits a minimal vpv through the first-party collector after consent", async () => {
    const { tracker, transport } = trackerForTest();
    tracker.grantAnalyticsConsent();

    const event = await tracker.captureVirtualPageView("https://app.example.com/pricing?utm_source=search#faq");

    expect(event.properties).toEqual({ page_path: "/pricing" });
    expect(event.occurred_at).toBe("2026-07-30T18:45:00.123Z");
    expect(transport.requests).toHaveLength(1);
    expect(transport.requests[0]?.endpoint).toBe("/fp/collect");
    expect(transport.requests[0]?.event).not.toHaveProperty("tenant_id");
  });

  it("uses a reusable click primitive and rejects sensitive or unregistered properties", async () => {
    const { tracker, transport } = trackerForTest();
    tracker.grantAnalyticsConsent();

    await tracker.track("click", { ui_name: "pricing_cta", ui_type: "button", ui_surface: "pricing" });
    expect(transport.requests[0]?.event.properties).toEqual({
      ui_name: "pricing_cta",
      ui_type: "button",
      ui_surface: "pricing"
    });

    await expect(
      tracker.track("activation_completed", { activation_action: "first_project_created", email: "person@example.com" } as never)
    ).rejects.toBeInstanceOf(PrivacyValidationError);
  });
});
