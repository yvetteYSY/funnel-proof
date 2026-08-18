package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes one complete Gold snapshot before replacing the previous version. */
public final class GoldSnapshotWriter {
    private final ObjectMapper mapper = new ObjectMapper();

    public void replace(Path target, ObjectNode snapshot) throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null) throw new IOException("gold output requires a parent directory");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, normalizedTarget.getFileName().toString(), ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), snapshot);
            try {
                Files.move(temporary, normalizedTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
