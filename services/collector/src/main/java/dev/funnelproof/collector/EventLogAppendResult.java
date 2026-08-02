package dev.funnelproof.collector;

/** Duplicate retries are acknowledged without writing another logical event to the durable log. */
public record EventLogAppendResult(boolean persisted) {
    public static EventLogAppendResult written() {
        return new EventLogAppendResult(true);
    }

    public static EventLogAppendResult duplicate() {
        return new EventLogAppendResult(false);
    }
}
