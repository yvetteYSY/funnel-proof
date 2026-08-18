package dev.funnelproof.pipeline.flink;

import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.time.Duration;

/**
 * Checkpointed keyed dedupe. TTL bounds state, while canonical Spark reconciliation protects
 * against duplicates that arrive after the streaming state retention window.
 */
public final class BoundedEventDedupe extends KeyedProcessFunction<String, AcceptedEvent, AcceptedEvent> {
    private final int retentionDays;
    private transient ValueState<Boolean> seen;

    public BoundedEventDedupe(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    @Override
    public void open(org.apache.flink.api.common.functions.OpenContext context) {
        StateTtlConfig ttl = StateTtlConfig.newBuilder(Duration.ofDays(retentionDays))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .build();
        ValueStateDescriptor<Boolean> descriptor = new ValueStateDescriptor<>("seen-event-id", Boolean.class);
        descriptor.enableTimeToLive(ttl);
        seen = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void processElement(AcceptedEvent event, Context context, Collector<AcceptedEvent> out) throws Exception {
        if (seen.value() == null) {
            seen.update(true);
            out.collect(event);
        }
    }
}
