package pbouda.jeffrey.generator.flamegraph.record;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;

import java.time.Instant;

public record TlabAllocationRecord(
        Instant timestamp,
        RecordedStackTrace stackTrace,
        RecordedThread thread,
        RecordedClass allocatedClass,
        long sampleWeight) implements StackBasedRecord {
}
