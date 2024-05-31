package pbouda.jeffrey.common;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

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
        return new Config(
                type,
                primaryRecording,
                secondaryRecording,
                eventType,
                primaryStart,
                secondaryStart,
                resolveTimeRange(primaryStart),
                resolveAndShiftTimeRange(secondaryStart),
                searchPattern,
                threadMode,
                collectWeight);
    }

    private AbsoluteTimeRange resolveAndShiftTimeRange(Instant start) {
        return switch (timeRange) {
            case AbsoluteTimeRange tr -> tr;
            case RelativeTimeRange tr when start != null -> tr.toAbsoluteTimeRange(start);
            case RelativeTimeRange _ -> throw new IllegalArgumentException("`relativeTimeRange` needs start argument");
            case null -> AbsoluteTimeRange.UNLIMITED;
        };
    }
}
