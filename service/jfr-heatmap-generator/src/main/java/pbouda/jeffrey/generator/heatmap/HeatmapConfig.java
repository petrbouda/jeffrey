package pbouda.jeffrey.generator.heatmap;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public record HeatmapConfig(
        Path jfrFile,
        String eventName,
        Instant profilingStartTime,
        Duration heatmapStart,
        Duration duration) {

    public static HeatmapConfigBuilder builder() {
        return new HeatmapConfigBuilder();
    }
}
