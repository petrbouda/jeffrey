package pbouda.jeffrey.generator.heatmap;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class HeatmapConfigBuilder {
    private Path jfrFile;
    private String eventName;
    private Instant profilingStart;
    private Duration heatmapStart = Duration.ZERO;
    private Duration duration;

    public HeatmapConfigBuilder withJfrFile(Path jfrFile) {
        this.jfrFile = jfrFile;
        return this;
    }

    public HeatmapConfigBuilder withEventName(String eventName) {
        this.eventName = eventName;
        return this;
    }

    public HeatmapConfigBuilder withProfilingStart(Instant profilingStart) {
        this.profilingStart = profilingStart;
        return this;
    }

    public HeatmapConfigBuilder withHeatmapStart(Duration heatmapStart) {
        this.heatmapStart = heatmapStart;
        return this;
    }

    public HeatmapConfigBuilder withDuration(Duration duration) {
        this.duration = duration;
        return this;
    }

    public HeatmapConfig build() {
        Objects.requireNonNull(jfrFile, "JFR file as a source of data needs to be specified");
        Objects.requireNonNull(eventName, "Name of the event needs to be specified");
        Objects.requireNonNull(profilingStart, "Start time of the profile needs to be specified");
        return new HeatmapConfig(jfrFile, eventName, profilingStart, heatmapStart, duration);
    }
}
