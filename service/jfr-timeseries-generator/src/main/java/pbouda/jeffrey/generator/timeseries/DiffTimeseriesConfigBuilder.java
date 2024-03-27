package pbouda.jeffrey.generator.timeseries;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public final class DiffTimeseriesConfigBuilder extends TimeseriesConfigBuilder<DiffTimeseriesConfigBuilder> {
    Path secondaryRecording;
    Instant secondaryStart;

    public DiffTimeseriesConfigBuilder() {
        super(TimeseriesConfig.Type.DIFFERENTIAL);
    }

    public DiffTimeseriesConfigBuilder withSecondaryRecording(Path recording) {
        this.secondaryRecording = recording;
        return this;
    }

    public DiffTimeseriesConfigBuilder withSecondaryStart(Instant profilingStart) {
        this.secondaryStart = profilingStart;
        return this;
    }

    @Override
    public TimeseriesConfig build() {
        Objects.requireNonNull(secondaryRecording, "Secondary JFR file as a source of data needs to be specified");
        Objects.requireNonNull(secondaryStart, "Start time of the profile needs to be specified");
        return new TimeseriesConfig(type, primaryRecording, secondaryRecording, eventType, primaryStart, secondaryStart, start, duration, interval, searchPattern);
    }
}
