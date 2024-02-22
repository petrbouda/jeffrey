package pbouda.jeffrey.generator.timeseries;

import pbouda.jeffrey.common.EventType;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class TimeseriesConfigBuilder {
    private Path recording;
    private EventType eventType;
    private Instant profilingStart;
    private Duration start = Duration.ZERO;
    private Duration duration;
    private Duration interval;

    public TimeseriesConfigBuilder withRecording(Path recording) {
        this.recording = recording;
        return this;
    }

    public TimeseriesConfigBuilder withEventType(EventType eventType) {
        this.eventType = eventType;
        return this;
    }

    public TimeseriesConfigBuilder withProfilingStart(Instant profilingStart) {
        this.profilingStart = profilingStart;
        return this;
    }

    public TimeseriesConfigBuilder withStart(Duration start) {
        this.start = start;
        return this;
    }

    public TimeseriesConfigBuilder withDuration(Duration duration) {
        this.duration = duration;
        return this;
    }

    public TimeseriesConfigBuilder withInterval(Duration interval) {
        this.interval = interval;
        return this;
    }

    public TimeseriesConfig build() {
        Objects.requireNonNull(recording, "JFR file as a source of data needs to be specified");
        Objects.requireNonNull(eventType, "Type of the event needs to be specified");
        Objects.requireNonNull(profilingStart, "Start time of the profile needs to be specified");
        return new TimeseriesConfig(recording, eventType, profilingStart, start, duration, interval);
    }
}
