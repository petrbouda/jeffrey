package pbouda.jeffrey.generator.flamegraph.record;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import pbouda.jeffrey.common.Type;

import java.time.Instant;

public record AllocationRecord(
        Instant timestamp,
        RecordedStackTrace stackTrace,
        RecordedThread thread,
        RecordedClass allocatedClass,
        EventType eventType,
        long sampleWeight) implements StackBasedRecord {
}
