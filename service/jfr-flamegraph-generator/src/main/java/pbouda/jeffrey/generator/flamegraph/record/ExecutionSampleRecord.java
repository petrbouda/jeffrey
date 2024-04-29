package pbouda.jeffrey.generator.flamegraph.record;

import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;

import java.time.Instant;

public record ExecutionSampleRecord(
        Instant timestamp,
        long sampleWeight,
        RecordedStackTrace stackTrace,
        RecordedThread thread) implements StackBasedRecord {

    public ExecutionSampleRecord(
            Instant timestamp,
            RecordedStackTrace stackTrace,
            RecordedThread thread) {
        this(timestamp, 1, stackTrace, thread);
    }
}
