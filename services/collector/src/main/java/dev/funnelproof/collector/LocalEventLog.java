package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Free local stand-in for Kafka. It writes the same internal record shape to an fsync'd append-only
 * file, so local development exercises publish-before-materialize and duplicate handling.
 */
public final class LocalEventLog implements EventLog {
    private static final String FILENAME = "accepted-events.ndjson";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path logFile;
    private final Set<String> eventIdentities = new HashSet<>();

    public LocalEventLog(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory.toAbsolutePath().normalize());
        this.logFile = dataDirectory.toAbsolutePath().normalize().resolve(FILENAME);
        loadExistingEventIdentities();
    }

    @Override
    public synchronized EventLogAppendResult publish(KafkaEventRecord record) throws IOException {
        String identity = identity(record.workspaceId(), record.eventId());
        if (eventIdentities.contains(identity)) return EventLogAppendResult.duplicate();

        byte[] line = (new String(record.serializedValue(mapper), StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(logFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            channel.write(ByteBuffer.wrap(line));
            channel.force(true);
        }
        eventIdentities.add(identity);
        return EventLogAppendResult.written();
    }

    /** Reads records strictly after a committed consumer checkpoint. Local offsets start at one. */
    public synchronized List<LoggedEvent> readAfter(long offset) throws IOException {
        if (offset < 0) throw new IOException("event log offset cannot be negative");
        if (!Files.exists(logFile)) return List.of();

        List<LoggedEvent> records = new ArrayList<>();
        long currentOffset = 0;
        int lineNumber = 0;
        for (String line : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            lineNumber++;
            if (line.isBlank()) continue;
            currentOffset++;
            if (currentOffset <= offset) continue;
            records.add(new LoggedEvent(currentOffset, parseRecord(line, lineNumber)));
        }
        return List.copyOf(records);
    }

    private void loadExistingEventIdentities() throws IOException {
        if (!Files.exists(logFile)) return;
        int lineNumber = 0;
        for (String line : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            lineNumber++;
            if (line.isBlank()) continue;
            KafkaEventRecord record = parseRecord(line, lineNumber);
            String workspaceId = record.workspaceId();
            String eventId = record.eventId();
            if (workspaceId.isBlank() || eventId.isBlank() || !eventIdentities.add(identity(workspaceId, eventId))) {
                throw new IOException("invalid persisted event log record at " + FILENAME + ":" + lineNumber);
            }
        }
    }

    private static String identity(String workspaceId, String eventId) {
        return workspaceId.length() + ":" + workspaceId + eventId;
    }

    private KafkaEventRecord parseRecord(String line, int lineNumber) throws IOException {
        JsonNode parsed = mapper.readTree(line);
        String workspaceId = parsed.path("workspace_id").asText();
        if (!(parsed.path("event") instanceof com.fasterxml.jackson.databind.node.ObjectNode event)) {
            throw new IOException("invalid persisted event log record at " + FILENAME + ":" + lineNumber);
        }
        try {
            return new KafkaEventRecordFactory().create(workspaceId, event);
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid persisted event log record at " + FILENAME + ":" + lineNumber, exception);
        }
    }
}
