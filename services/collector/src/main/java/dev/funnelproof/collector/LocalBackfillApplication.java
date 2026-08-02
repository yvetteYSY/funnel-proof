package dev.funnelproof.collector;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Replays privacy-filtered accepted events into a separately chosen local read-model directory.
 * It never deletes or overwrites a source log; a caller chooses an empty target for a fresh rebuild.
 */
public final class LocalBackfillApplication {
    private LocalBackfillApplication() {
    }

    public static void main(String[] args) throws IOException {
        Path eventLogDirectory = Path.of(System.getenv().getOrDefault("FUNNEL_PROOF_EVENT_LOG_DIR", ".funnel-proof/event-log"));
        Path targetDirectory = Path.of(System.getenv().getOrDefault("FUNNEL_PROOF_BACKFILL_DATA_DIR", ".funnel-proof/backfill/events"));
        Path checkpointFile = Path.of(System.getenv().getOrDefault(
                "FUNNEL_PROOF_BACKFILL_CHECKPOINT",
                ".funnel-proof/backfill/checkpoints/bronze.offset"
        ));

        MaterializationResult result = new LocalEventLogMaterializer().materialize(
                new LocalEventLog(eventLogDirectory),
                new FileEventStore(targetDirectory),
                new FileOffsetStore(checkpointFile)
        );
        System.out.printf(
                "Local backfill complete: processed=%d newly_written=%d checkpoint=%d%n",
                result.processed(), result.newlyWritten(), result.checkpoint()
        );
    }
}
