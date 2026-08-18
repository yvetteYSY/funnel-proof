package dev.funnelproof.pipeline.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ExternalizedCheckpointRetention;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.data.TimestampData;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.flink.sink.FlinkSink;

import java.time.Duration;

/** Kafka accepted events to Iceberg Bronze, with checkpointed keyed dedupe and event-time watermarks. */
public final class BronzeStreamingJob {
    private BronzeStreamingJob() {
    }

    public static void main(String[] args) throws Exception {
        PipelineSettings settings = PipelineSettings.fromEnvironment();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        configureCheckpoints(env, settings);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(settings.kafkaBootstrapServers())
                .setTopics(settings.kafkaTopic())
                .setGroupId(settings.kafkaConsumerGroup())
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<AcceptedEvent> accepted = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "accepted-events-kafka")
                .map(AcceptedEvent::parse, TypeInformation.of(AcceptedEvent.class))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<AcceptedEvent>forBoundedOutOfOrderness(Duration.ofHours(settings.allowedLatenessHours()))
                                .withTimestampAssigner((event, ignored) -> event.occurredAt().toEpochMilli())
                )
                .keyBy(AcceptedEvent::dedupeKey)
                .process(new BoundedEventDedupe(settings.dedupeRetentionDays()));

        DataStream<RowData> bronzeRows = accepted.map(BronzeStreamingJob::toBronzeRow, TypeInformation.of(RowData.class));
        FlinkSink.forRowData(bronzeRows)
                .tableLoader(TableLoader.fromHadoopTable(settings.icebergBronzeLocation(), new org.apache.hadoop.conf.Configuration()))
                .append();

        env.execute("funnelproof-kafka-to-iceberg-bronze");
    }

    private static void configureCheckpoints(StreamExecutionEnvironment env, PipelineSettings settings) {
        Configuration configuration = new Configuration();
        configuration.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, settings.checkpointStorage());
        env.configure(configuration);
        env.enableCheckpointing(Duration.ofSeconds(settings.checkpointIntervalSeconds()).toMillis(), CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setCheckpointTimeout(Duration.ofMinutes(2).toMillis());
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(Duration.ofSeconds(10).toMillis());
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.getCheckpointConfig().setExternalizedCheckpointRetention(ExternalizedCheckpointRetention.RETAIN_ON_CANCELLATION);
    }

    private static RowData toBronzeRow(AcceptedEvent event) {
        GenericRowData row = new GenericRowData(10);
        row.setField(0, StringData.fromString(event.workspaceId()));
        row.setField(1, StringData.fromString(event.eventId()));
        row.setField(2, StringData.fromString(event.anonymousId()));
        row.setField(3, StringData.fromString(event.eventName()));
        row.setField(4, TimestampData.fromInstant(event.occurredAt()));
        row.setField(5, TimestampData.fromInstant(event.receivedAt()));
        row.setField(6, StringData.fromString(event.schemaVersion()));
        row.setField(7, StringData.fromString(event.trackingPlanVersion()));
        row.setField(8, StringData.fromString(event.funnelDefinitionVersion()));
        row.setField(9, StringData.fromString(event.acceptedEventJson()));
        return row;
    }
}
