package dev.funnelproof.collector;

/** Result of replaying accepted events into a local funnel read model. */
public record MaterializationResult(long checkpoint, long processed, long newlyWritten) {
}
