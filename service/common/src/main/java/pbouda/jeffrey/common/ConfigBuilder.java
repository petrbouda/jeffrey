package pbouda.jeffrey.common;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

@SuppressWarnings("unchecked")
public class ConfigBuilder<T extends ConfigBuilder<?>> {
    Config.Type type;
    Path primaryRecording;
    EventType eventType;
    Instant primaryStart;
    TimeRange timeRange;
    String searchPattern;
    boolean threadMode;
    boolean collectWeight;

    public ConfigBuilder() {
        this(Config.Type.PRIMARY);
    }

    public ConfigBuilder(Config.Type type) {
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

    public T withTimeRange(TimeRange timeRange) {
        this.timeRange = timeRange;
        return (T) this;
    }

    public T withSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
        return (T) this;
    }

    public T withThreadMode(boolean threadMode) {
        this.threadMode = threadMode;
        return (T) this;
    }

    public T withCollectWeight(boolean collectWeight) {
        this.collectWeight = collectWeight;
        return (T) this;
    }

    protected AbsoluteTimeRange resolveTimeRange(Instant start) {
        return switch (timeRange) {
            case AbsoluteTimeRange tr -> tr;
            case RelativeTimeRange tr when start != null -> tr.toAbsoluteTimeRange(start);
            case RelativeTimeRange _ -> throw new IllegalArgumentException("`relativeTimeRange` only with `primaryStart`");
            case null -> AbsoluteTimeRange.UNLIMITED;
        };
    }

    public Config build() {
        Objects.requireNonNull(primaryRecording, "JFR file as a source of data needs to be specified");
        Objects.requireNonNull(eventType, "Type of the event needs to be specified");
        return new Config(
                type,
                primaryRecording,
                eventType,
                primaryStart,
                resolveTimeRange(primaryStart),
                searchPattern,
                threadMode,
                collectWeight);
    }
}
