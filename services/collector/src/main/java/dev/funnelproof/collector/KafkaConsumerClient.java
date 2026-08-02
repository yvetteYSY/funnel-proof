package dev.funnelproof.collector;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Test seam around the Apache client; production commits only after materialization succeeds. */
interface KafkaConsumerClient extends AutoCloseable {
    List<KafkaConsumerRecord> poll(Duration timeout) throws IOException;

    void commitSync(List<KafkaConsumerOffset> offsets) throws IOException;

    @Override
    void close();
}
