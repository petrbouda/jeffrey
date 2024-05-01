package pbouda.jeffrey.common;

import java.time.Duration;
import java.time.Instant;

public record AbsoluteTimeRange(Instant start, Instant end) implements TimeRange {

    public static final AbsoluteTimeRange UNLIMITED = new AbsoluteTimeRange();

    public AbsoluteTimeRange(long startInMillis, long endInMillis) {
        this(Instant.ofEpochMilli(startInMillis), Instant.ofEpochMilli(endInMillis));
    }

    public AbsoluteTimeRange(Instant start) {
        this(start, Instant.MAX);
    }

    public AbsoluteTimeRange() {
        this(Instant.MIN, Instant.MAX);
    }

    public AbsoluteTimeRange shiftForward(Duration shift) {
        return new AbsoluteTimeRange(start.plus(shift), end.plus(shift));
    }

    public AbsoluteTimeRange shiftBack(Duration shift) {
        return new AbsoluteTimeRange(start.minus(shift), end.minus(shift));
    }
}
