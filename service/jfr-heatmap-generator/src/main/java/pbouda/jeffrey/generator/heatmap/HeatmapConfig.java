package pbouda.jeffrey.generator.heatmap;

import pbouda.jeffrey.common.EventType;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public record HeatmapConfig(
        Path recording,
        EventType eventType,
        Instant profilingStartTime,
        Duration heatmapStart,
        Duration duration) {

    public static HeatmapConfigBuilder builder() {
        return new HeatmapConfigBuilder();
    }
}
