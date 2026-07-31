package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Local-only sink. The returned snapshot is for development diagnostics and is never exposed
 * by the collector HTTP API.
 */
public final class InMemoryEventStore implements EventStore {
    private final Map<String, List<ObjectNode>> eventsByWorkspace = new HashMap<>();
    private final Map<String, Set<String>> eventIdsByWorkspace = new HashMap<>();

    @Override
    public synchronized StoreAppendResult append(String workspaceId, ObjectNode acceptedEvent) {
        Set<String> eventIds = eventIdsByWorkspace.computeIfAbsent(workspaceId, ignored -> new HashSet<>());
        String eventId = acceptedEvent.path("event_id").asText();
        if (!eventIds.add(eventId)) return StoreAppendResult.duplicate();

        eventsByWorkspace.computeIfAbsent(workspaceId, ignored -> new ArrayList<>()).add(acceptedEvent.deepCopy());
        return StoreAppendResult.written();
    }

    @Override
    public synchronized List<ObjectNode> snapshot(String workspaceId) {
        return eventsByWorkspace.getOrDefault(workspaceId, List.of()).stream().map(ObjectNode::deepCopy).toList();
    }
}
