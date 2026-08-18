package dev.funnelproof.collector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Explainable median/MAD anomaly detector for aggregate metrics only. */
public final class RobustAnomalyDetector {
    private static final int MINIMUM_BASELINE_POINTS = 7;
    private static final double ROBUST_Z_THRESHOLD = 3.5;

    public AnomalyResult detect(String metric, List<Double> baseline, double currentValue, boolean dataHealthy) {
        if (!dataHealthy) return new AnomalyResult(metric, "suppressed_data_sla_unhealthy", null, null, null);
        if (baseline.size() < MINIMUM_BASELINE_POINTS) {
            return new AnomalyResult(metric, "insufficient_history", null, null, null);
        }
        double median = median(baseline);
        List<Double> deviations = baseline.stream().map(value -> Math.abs(value - median)).toList();
        double mad = median(deviations);
        if (mad == 0) return new AnomalyResult(metric, "insufficient_variation", median, mad, null);

        double robustZ = 0.6745 * (currentValue - median) / mad;
        String status = Math.abs(robustZ) >= ROBUST_Z_THRESHOLD ? "anomaly" : "normal";
        return new AnomalyResult(metric, status, median, mad, round(robustZ));
    }

    private static double median(List<Double> values) {
        List<Double> ordered = new ArrayList<>(values);
        ordered.sort(Comparator.naturalOrder());
        int middle = ordered.size() / 2;
        return ordered.size() % 2 == 0 ? (ordered.get(middle - 1) + ordered.get(middle)) / 2 : ordered.get(middle);
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    public record AnomalyResult(String metric, String status, Double baselineMedian, Double baselineMad, Double robustZScore) {
    }
}
