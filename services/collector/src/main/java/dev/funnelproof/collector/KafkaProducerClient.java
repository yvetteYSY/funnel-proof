package dev.funnelproof.collector;

import java.io.IOException;

/** Small seam so Kafka publication behavior can be tested without starting a broker. */
interface KafkaProducerClient extends AutoCloseable {
    void publish(String topic, String partitionKey, byte[] value) throws IOException;

    @Override
    void close();
}
