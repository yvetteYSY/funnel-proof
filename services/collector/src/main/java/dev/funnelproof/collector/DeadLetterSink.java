package dev.funnelproof.collector;

import java.io.IOException;

/** Records safe failure metadata only; never writes an untrusted raw Kafka payload to disk. */
interface DeadLetterSink {
    void record(KafkaConsumerRecord record, String reason) throws IOException;
}
