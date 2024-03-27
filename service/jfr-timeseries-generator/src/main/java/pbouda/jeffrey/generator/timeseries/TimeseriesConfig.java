package pbouda.jeffrey.generator.timeseries;

import pbouda.jeffrey.common.EventType;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public record TimeseriesConfig(
        Type type,
        Path primaryRecording,
        Path secondaryRecording,
        EventType eventType,
        Instant primaryStart,
        Instant secondaryStart,
        Duration start,
        Duration duration,
        Duration interval,
        String searchPattern) {

    public enum Type {
        PRIMARY, DIFFERENTIAL
    }

    public TimeseriesConfig(
            Type type,
            Path primaryRecording,
            EventType eventType,
            Instant primaryStart,
            Duration start,
            Duration duration,
            Duration interval,
            String searchPattern) {

        this(type, primaryRecording, null, eventType, primaryStart, null, start, duration, interval, searchPattern);
    }

    public static TimeseriesConfigBuilder<?> primaryBuilder() {
        return new TimeseriesConfigBuilder<>();
    }

    public static DiffTimeseriesConfigBuilder differentialBuilder() {
        return new DiffTimeseriesConfigBuilder();
    }
}
