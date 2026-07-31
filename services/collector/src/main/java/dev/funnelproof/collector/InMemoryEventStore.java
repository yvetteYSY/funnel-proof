package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-only sink. The returned snapshot is for development diagnostics and is never exposed
 * by the collector HTTP API.
 */
public final class InMemoryEventStore implements EventStore {
    private final List<ObjectNode> events = new ArrayList<>();

    @Override
    public synchronized void append(ObjectNode acceptedEvent) {
        events.add(acceptedEvent.deepCopy());
    }

    public synchronized List<ObjectNode> snapshot() {
        return events.stream().map(ObjectNode::deepCopy).toList();
    }
}
