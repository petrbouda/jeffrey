package pbouda.jeffrey.jfrparser.jdk;

import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;

import java.time.Instant;

public record StackBasedRecord(Instant timestamp, RecordedStackTrace stackTrace, RecordedThread thread) {
}
