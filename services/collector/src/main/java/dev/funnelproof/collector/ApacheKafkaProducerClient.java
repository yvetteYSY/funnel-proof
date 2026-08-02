package dev.funnelproof.collector;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Apache Kafka Java-client adapter. It connects only when the Kafka profile is explicitly used. */
final class ApacheKafkaProducerClient implements KafkaProducerClient {
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(5);
    private final KafkaProducer<String, byte[]> producer;

    ApacheKafkaProducerClient(String bootstrapServers) {
        Properties properties = KafkaProducerSettings.forBootstrapServers(bootstrapServers);
        properties.put("key.serializer", StringSerializer.class.getName());
        properties.put("value.serializer", ByteArraySerializer.class.getName());
        this.producer = new KafkaProducer<>(properties);
    }

    @Override
    public void publish(String topic, String partitionKey, byte[] value) throws IOException {
        try {
            producer.send(new ProducerRecord<>(topic, partitionKey, value)).get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Kafka publish interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("Kafka did not durably acknowledge the event", exception);
        }
    }

    @Override
    public void close() {
        producer.close(ACK_TIMEOUT);
    }
}
