package pbouda.jeffrey.common;

import java.time.Duration;
import java.time.Instant;

/**
 * @param start start from the beginning of the recording.
 * @param end end from the beginning of the recording.
 */
public record RelativeTimeRange(Duration start, Duration end) implements TimeRange {

    public RelativeTimeRange(long startInMillis, long endInMillis) {
        this(Duration.ofMillis(startInMillis), Duration.ofMillis(endInMillis));
    }

    public AbsoluteTimeRange toAbsoluteTimeRange(Instant recordingStart) {
        return new AbsoluteTimeRange(recordingStart.plus(start), recordingStart.plus(end));
    }

    @Override
    public Duration duration() {
        return end.minus(start);
    }
}
