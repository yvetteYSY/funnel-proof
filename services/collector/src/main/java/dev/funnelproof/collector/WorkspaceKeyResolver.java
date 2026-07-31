package dev.funnelproof.collector;

import java.util.Map;
import java.util.Optional;

/** Resolves an API key at the edge without storing that key in event data. */
public interface WorkspaceKeyResolver {
    Optional<String> resolve(String workspaceKey);

    static WorkspaceKeyResolver localFromEnvironment() {
        String key = System.getenv().getOrDefault("FUNNEL_PROOF_WORKSPACE_KEY", "fp_public_local_demo");
        String workspaceId = System.getenv().getOrDefault("FUNNEL_PROOF_WORKSPACE_ID", "demo_workspace");
        return fixed(Map.of(key, workspaceId));
    }

    static WorkspaceKeyResolver fixed(Map<String, String> workspaceKeys) {
        Map<String, String> permitted = Map.copyOf(workspaceKeys);
        return workspaceKey -> Optional.ofNullable(permitted.get(workspaceKey));
    }
}
