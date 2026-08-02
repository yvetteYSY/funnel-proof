package dev.funnelproof.collector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Small local checkpoint file, atomically replaced where the file system supports it. */
public final class FileOffsetStore implements OffsetStore {
    private final Path checkpointFile;

    public FileOffsetStore(Path checkpointFile) throws IOException {
        this.checkpointFile = checkpointFile.toAbsolutePath().normalize();
        Path parent = this.checkpointFile.getParent();
        if (parent == null) throw new IOException("checkpoint path requires a parent directory");
        Files.createDirectories(parent);
    }

    @Override
    public synchronized long load() throws IOException {
        if (!Files.exists(checkpointFile)) return 0;
        try {
            long offset = Long.parseLong(Files.readString(checkpointFile, StandardCharsets.UTF_8).trim());
            if (offset < 0) throw new IOException("checkpoint offset cannot be negative");
            return offset;
        } catch (NumberFormatException exception) {
            throw new IOException("invalid checkpoint offset", exception);
        }
    }

    @Override
    public synchronized void save(long offset) throws IOException {
        if (offset < 0) throw new IOException("checkpoint offset cannot be negative");
        Path temporary = checkpointFile.resolveSibling(checkpointFile.getFileName() + ".tmp");
        Files.writeString(temporary, Long.toString(offset) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(temporary, checkpointFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, checkpointFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
