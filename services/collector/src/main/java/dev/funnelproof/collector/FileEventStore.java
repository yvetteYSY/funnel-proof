package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Local development Bronze sink. Each workspace receives an append-only NDJSON file; records
 * have already passed the privacy filter, and the raw workspace key is never written to disk.
 */
public final class FileEventStore implements EventStore {
    private static final Pattern WORKSPACE_ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path dataDirectory;
    private final Map<String, List<ObjectNode>> eventsByWorkspace = new HashMap<>();
    private final Map<String, Set<String>> eventIdsByWorkspace = new HashMap<>();

    public FileEventStore(Path dataDirectory) throws IOException {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        Files.createDirectories(this.dataDirectory);
        loadExistingEvents();
    }

    @Override
    public synchronized StoreAppendResult append(String workspaceId, ObjectNode acceptedEvent) throws IOException {
        validateWorkspaceId(workspaceId);
        String eventId = acceptedEvent.path("event_id").asText();
        if (eventId.isBlank()) throw new IOException("accepted event is missing event_id");

        Set<String> eventIds = eventIdsByWorkspace.computeIfAbsent(workspaceId, ignored -> new HashSet<>());
        if (eventIds.contains(eventId)) return StoreAppendResult.duplicate();

        byte[] line = (mapper.writeValueAsString(acceptedEvent) + "\n").getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(workspaceFile(workspaceId),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            channel.write(ByteBuffer.wrap(line));
            channel.force(true);
        }

        eventIds.add(eventId);
        eventsByWorkspace.computeIfAbsent(workspaceId, ignored -> new ArrayList<>()).add(acceptedEvent.deepCopy());
        return StoreAppendResult.written();
    }

    @Override
    public synchronized List<ObjectNode> snapshot(String workspaceId) {
        validateWorkspaceId(workspaceId);
        return eventsByWorkspace.getOrDefault(workspaceId, List.of()).stream().map(ObjectNode::deepCopy).toList();
    }

    private void loadExistingEvents() throws IOException {
        try (Stream<Path> paths = Files.list(dataDirectory)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString().endsWith(".ndjson")).toList()) {
                String filename = path.getFileName().toString();
                String workspaceId = filename.substring(0, filename.length() - ".ndjson".length());
                validateWorkspaceId(workspaceId);

                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (int index = 0; index < lines.size(); index++) {
                    if (lines.get(index).isBlank()) continue;
                    JsonNode parsed = mapper.readTree(lines.get(index));
                    if (!(parsed instanceof ObjectNode event) || event.path("event_id").asText().isBlank()) {
                        throw new IOException("invalid persisted event at " + filename + ":" + (index + 1));
                    }
                    String eventId = event.path("event_id").asText();
                    Set<String> eventIds = eventIdsByWorkspace.computeIfAbsent(workspaceId, ignored -> new HashSet<>());
                    if (!eventIds.add(eventId)) {
                        throw new IOException("duplicate persisted event_id at " + filename + ":" + (index + 1));
                    }
                    eventsByWorkspace.computeIfAbsent(workspaceId, ignored -> new ArrayList<>()).add(event.deepCopy());
                }
            }
        }
    }

    private Path workspaceFile(String workspaceId) {
        return dataDirectory.resolve(workspaceId + ".ndjson");
    }

    private static void validateWorkspaceId(String workspaceId) {
        if (!WORKSPACE_ID.matcher(workspaceId).matches()) {
            throw new IllegalArgumentException("workspace id must be a safe local identifier");
        }
    }
}
