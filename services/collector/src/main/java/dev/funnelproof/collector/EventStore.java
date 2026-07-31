package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.List;

public interface EventStore {
    /**
     * Persists an already validated event within its authenticated workspace.
     * Implementations must treat {@code workspaceId + event_id} as idempotent.
     */
    StoreAppendResult append(String workspaceId, ObjectNode acceptedEvent) throws IOException;

    /** Returns privacy-filtered event copies for one authenticated workspace only. */
    List<ObjectNode> snapshot(String workspaceId) throws IOException;
}
