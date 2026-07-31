import type { CollectionRequest, EventTransport } from "./types.js";

export class FetchTransport implements EventTransport {
  async send(request: CollectionRequest): Promise<void> {
    const response = await fetch(request.endpoint, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-funnel-proof-workspace-key": request.workspaceKey
      },
      body: JSON.stringify(request.event),
      keepalive: true,
      credentials: "same-origin"
    });

    if (!response.ok) {
      throw new Error(`FunnelProof collector rejected event with HTTP ${response.status}`);
    }
  }
}
