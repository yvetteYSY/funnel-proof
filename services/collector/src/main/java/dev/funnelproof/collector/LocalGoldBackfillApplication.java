package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/** Rebuilds a local Gold daily aggregate from the privacy-filtered local Silver event store. */
public final class LocalGoldBackfillApplication {
    private LocalGoldBackfillApplication() {
    }

    public static void main(String[] args) throws IOException {
        Path inputDirectory = Path.of(System.getenv().getOrDefault("FUNNEL_PROOF_GOLD_INPUT_DIR", ".funnel-proof/events"));
        Path outputFile = Path.of(System.getenv().getOrDefault("FUNNEL_PROOF_GOLD_OUTPUT", ".funnel-proof/gold/daily-funnel.v1.json"));
        String workspaceId = System.getenv().getOrDefault("FUNNEL_PROOF_GOLD_WORKSPACE_ID", "demo_workspace");

        ObjectNode snapshot = new GoldDailyFunnelSnapshot().build(
                new FileEventStore(inputDirectory).snapshot(workspaceId), Instant.now()
        );
        new GoldSnapshotWriter().replace(outputFile, snapshot);
        System.out.printf(
                "Local Gold rebuild complete: daily_buckets=%d output=%s%n",
                snapshot.path("daily_funnels").size(), outputFile.toAbsolutePath().normalize()
        );
    }
}
