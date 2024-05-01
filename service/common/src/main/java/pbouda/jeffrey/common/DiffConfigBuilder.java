package pbouda.jeffrey.common;

import java.nio.file.Path;
import java.time.Duration;
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
                threadMode);
    }

    private AbsoluteTimeRange resolveAndShiftTimeRange(Instant start) {
        Duration timeShift = Duration.between(primaryStart, secondaryStart);
        return switch (timeRange) {
            case AbsoluteTimeRange tr -> shift(timeShift, tr);
            case RelativeTimeRange tr when start != null -> shift(timeShift, tr.toAbsoluteTimeRange(start));
            case RelativeTimeRange _ -> throw new IllegalArgumentException("`relativeTimeRange` needs start argument");
            case null -> AbsoluteTimeRange.UNLIMITED;
        };
    }

    private AbsoluteTimeRange shift(Duration timeShift, AbsoluteTimeRange tr) {
        if (timeShift.isPositive()) {
            return tr.shiftBack(timeShift);
        } else {
            return tr.shiftForward(timeShift);
        }
    }
}
