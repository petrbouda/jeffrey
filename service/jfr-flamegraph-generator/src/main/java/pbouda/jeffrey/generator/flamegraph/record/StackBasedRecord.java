package pbouda.jeffrey.generator.flamegraph.record;

import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;

import java.time.Instant;

public sealed interface StackBasedRecord permits
        ExecutionSampleRecord, TlabAllocationRecord, BlockingRecord {

    /**
     * When the record was captured.
     *
     * @return instant time of the record.
     */
    Instant timestamp();

    /**
     * Defines the weight of the single sample.
     * It can be 1 in case of Execution Sample, but it can be more in case of Allocation Sample
     *
     * @return the value of the weight for this record.
     */
    long sampleWeight();

    /**
     * The stacktrace of the thread at the time of recording the sample.
     *
     * @return stack trace of the sample
     */
    RecordedStackTrace stackTrace();

    /**
     * The thread that recorded the sample.
     *
     * @return active thread at the time of recording.
     */
    RecordedThread thread();

}
