package dev.funnelproof.collector;

import java.io.IOException;

/**
 * Local consumer/backfill worker. Offset checkpoints advance only after an idempotent event-store
 * write, so a crash replays at most an already-safe logical event.
 */
public final class LocalEventLogMaterializer {
    public MaterializationResult materialize(LocalEventLog eventLog, EventStore eventStore, OffsetStore offsetStore) throws IOException {
        long checkpoint = offsetStore.load();
        long processed = 0;
        long newlyWritten = 0;
        for (LoggedEvent loggedEvent : eventLog.readAfter(checkpoint)) {
            StoreAppendResult result = eventStore.append(loggedEvent.record().workspaceId(), loggedEvent.record().event());
            processed++;
            if (result.persisted()) newlyWritten++;
            offsetStore.save(loggedEvent.offset());
            checkpoint = loggedEvent.offset();
        }
        return new MaterializationResult(checkpoint, processed, newlyWritten);
    }
}
