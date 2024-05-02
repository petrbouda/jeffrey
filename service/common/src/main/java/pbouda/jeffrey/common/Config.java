package pbouda.jeffrey.common;

import java.nio.file.Path;
import java.time.Instant;

public record Config(
        Type type,
        Path primaryRecording,
        Path secondaryRecording,
        EventType eventType,
        Instant primaryStart,
        Instant secondaryStart,
        AbsoluteTimeRange primaryTimeRange,
        AbsoluteTimeRange secondaryTimeRange,
        String searchPattern,
        boolean threadMode,
        boolean collectWeight) {

    public enum Type {
        PRIMARY, DIFFERENTIAL
    }

    public Config(
            Type type,
            Path primaryRecording,
            EventType eventType,
            Instant primaryStart,
            AbsoluteTimeRange primaryTimeRange,
            String searchPattern,
            boolean threadMode,
            boolean collectWeight) {

        this(type, primaryRecording, null, eventType, primaryStart, null, primaryTimeRange, null, searchPattern, threadMode, collectWeight);
    }

    public static ConfigBuilder<?> primaryBuilder() {
        return new ConfigBuilder<>();
    }

    public static DiffConfigBuilder differentialBuilder() {
        return new DiffConfigBuilder();
    }
}
