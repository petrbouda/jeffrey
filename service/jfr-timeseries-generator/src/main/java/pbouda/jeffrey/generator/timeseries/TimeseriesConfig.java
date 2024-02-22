package pbouda.jeffrey.generator.timeseries;

import pbouda.jeffrey.common.EventType;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public record TimeseriesConfig(
        Path recording,
        EventType eventType,
        Instant profilingStartTime,
        Duration start,
        Duration duration,
        Duration interval) {

    public static TimeseriesConfigBuilder builder() {
        return new TimeseriesConfigBuilder();
    }
}
