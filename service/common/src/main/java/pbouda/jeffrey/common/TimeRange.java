package pbouda.jeffrey.common;

import java.time.Duration;

public sealed interface TimeRange permits AbsoluteTimeRange, RelativeTimeRange {

    Duration duration();

    static TimeRange create(long startInMillis, long endInMillis, boolean absolute) {
        if (absolute) {
            return new AbsoluteTimeRange(startInMillis, endInMillis);
        } else {
            return new RelativeTimeRange(startInMillis, endInMillis);
        }
    }
}
