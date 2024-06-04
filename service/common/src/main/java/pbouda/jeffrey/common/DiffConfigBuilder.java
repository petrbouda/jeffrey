package pbouda.jeffrey.common;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class DiffConfigBuilder extends ConfigBuilder<DiffConfigBuilder> {
    Path secondaryRecording;
    Instant secondaryStart;

    public DiffConfigBuilder() {
        super(Config.Type.DIFFERENTIAL);
    }

    public DiffConfigBuilder withSecondaryRecording(Path recording) {
        this.secondaryRecording = recording;
        return this;
    }

    public DiffConfigBuilder withSecondaryStart(Instant profilingStart) {
        this.secondaryStart = profilingStart;
        return this;
    }

    @Override
    public Config build() {
        Objects.requireNonNull(secondaryRecording, "Secondary JFR file as a source of data needs to be specified");
        Objects.requireNonNull(secondaryStart, "Start time of the profile needs to be specified");

        AbsoluteTimeRange primaryRange = resolveTimeRange(primaryStart);
        AbsoluteTimeRange secondaryRange;
        if (timeRange == null) {
            secondaryRange = AbsoluteTimeRange.UNLIMITED;
        } else {
            long timeShift = ChronoUnit.MILLIS.between(primaryStart, primaryRange.start());
            Duration duration = timeRange.duration();
            Instant start = secondaryStart.plusMillis(timeShift);
            Instant end = start.plus(duration);
            secondaryRange = new AbsoluteTimeRange(start, end);
        }

        return new Config(
                type,
                primaryRecording,
                secondaryRecording,
                eventType,
                primaryStart,
                secondaryStart,
                primaryRange,
                secondaryRange,
                searchPattern,
                threadMode,
                collectWeight);
    }
}
