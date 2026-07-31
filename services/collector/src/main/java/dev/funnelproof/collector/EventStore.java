package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface EventStore {
    void append(ObjectNode acceptedEvent);
}
