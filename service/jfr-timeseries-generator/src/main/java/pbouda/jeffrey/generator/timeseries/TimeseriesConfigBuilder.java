package pbouda.jeffrey.generator.timeseries;

import pbouda.jeffrey.common.EventType;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@SuppressWarnings("unchecked")
public class TimeseriesConfigBuilder<T extends TimeseriesConfigBuilder<?>> {
    TimeseriesConfig.Type type;
    Path primaryRecording;
    EventType eventType;
    Instant primaryStart;
    Duration start = Duration.ZERO;
    Duration duration;
    Duration interval;

    public TimeseriesConfigBuilder() {
        this(TimeseriesConfig.Type.PRIMARY);
    }

    public TimeseriesConfigBuilder(TimeseriesConfig.Type type) {
        this.type = type;
    }

    public T withPrimaryRecording(Path recording) {
        this.primaryRecording = recording;
        return (T) this;
    }

    public T withEventType(EventType eventType) {
        this.eventType = eventType;
        return (T) this;
    }

    public T withPrimaryStart(Instant profilingStart) {
        this.primaryStart = profilingStart;
        return (T) this;
    }

    public T withStart(Duration start) {
        this.start = start;
        return (T) this;
    }

    public T withDuration(Duration duration) {
        this.duration = duration;
        return (T) this;
    }

    public T withInterval(Duration interval) {
        this.interval = interval;
        return (T) this;
    }

    public TimeseriesConfig build() {
        Objects.requireNonNull(primaryRecording, "JFR file as a source of data needs to be specified");
        Objects.requireNonNull(eventType, "Type of the event needs to be specified");
        Objects.requireNonNull(primaryStart, "Start time of the profile needs to be specified");
        return new TimeseriesConfig(type, primaryRecording, eventType, primaryStart, start, duration, interval);
    }
}
