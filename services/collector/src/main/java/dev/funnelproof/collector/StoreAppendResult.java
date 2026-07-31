package dev.funnelproof.collector;

/** Outcome of an idempotent write; duplicate retries are accepted but not written twice. */
public record StoreAppendResult(boolean persisted) {
    public static StoreAppendResult written() {
        return new StoreAppendResult(true);
    }

    public static StoreAppendResult duplicate() {
        return new StoreAppendResult(false);
    }
}
