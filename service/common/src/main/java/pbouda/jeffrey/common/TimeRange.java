package pbouda.jeffrey.common;

public sealed interface TimeRange permits AbsoluteTimeRange, RelativeTimeRange {

    static TimeRange create(long startInMillis, long endInMillis, boolean absolute) {
        if (absolute) {
            return new AbsoluteTimeRange(startInMillis, endInMillis);
        } else {
            return new RelativeTimeRange(startInMillis, endInMillis);
        }
    }
}
