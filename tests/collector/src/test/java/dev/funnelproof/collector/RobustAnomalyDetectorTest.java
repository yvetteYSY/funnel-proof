package dev.funnelproof.collector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RobustAnomalyDetectorTest {
    private final RobustAnomalyDetector detector = new RobustAnomalyDetector();
    private final List<Double> baseline = List.of(96.0, 99.0, 100.0, 100.0, 101.0, 102.0, 104.0);

    @Test
    void identifiesAHighConfidenceBusinessMetricAnomaly() {
        var result = detector.detect("activation_conversion", baseline, 80.0, true);

        assertEquals("anomaly", result.status());
        assertEquals(100.0, result.baselineMedian());
    }

    @Test
    void suppressesFindingsWhenTheDataSlaIsUnhealthy() {
        var result = detector.detect("activation_conversion", baseline, 80.0, false);

        assertEquals("suppressed_data_sla_unhealthy", result.status());
        assertEquals(null, result.robustZScore());
    }

    @Test
    void requiresEnoughHistoryBeforeMakingAClaim() {
        var result = detector.detect("signup_volume", List.of(10.0, 11.0, 12.0), 1.0, true);

        assertEquals("insufficient_history", result.status());
    }
}
