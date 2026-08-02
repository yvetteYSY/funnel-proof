package dev.funnelproof.collector;

import java.io.IOException;

/** Durable, replayable accepted-event boundary. Kafka is the production implementation. */
public interface EventLog {
    EventLogAppendResult publish(KafkaEventRecord record) throws IOException;
}
