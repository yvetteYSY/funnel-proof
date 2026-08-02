package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Local safe-reason audit for poison records. Original values remain only in Kafka retention. */
final class FileDeadLetterSink implements DeadLetterSink {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;

    FileDeadLetterSink(Path directory) throws IOException {
        Files.createDirectories(directory.toAbsolutePath().normalize());
        file = directory.toAbsolutePath().normalize().resolve("kafka-materializer.ndjson");
    }

    @Override
    public synchronized void record(KafkaConsumerRecord record, String reason) throws IOException {
        ObjectNode audit = mapper.createObjectNode();
        audit.put("recorded_at", Instant.now().toString());
        audit.put("topic", record.topic());
        audit.put("partition", record.partition());
        audit.put("offset", record.offset());
        audit.put("reason", reason);
        byte[] line = (mapper.writeValueAsString(audit) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            channel.write(ByteBuffer.wrap(line));
            channel.force(true);
        }
    }
}
