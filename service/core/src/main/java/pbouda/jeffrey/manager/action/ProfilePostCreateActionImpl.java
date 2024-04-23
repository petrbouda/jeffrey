package pbouda.jeffrey.manager.action;

import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.graph.StackTraceBuilder;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;
import pbouda.jeffrey.jfrparser.jdk.StackBasedRecord;
import pbouda.jeffrey.jfrparser.jdk.StacktraceBasedEventProcessor;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.util.List;

public class ProfilePostCreateActionImpl implements ProfilePostCreateAction {

    private final WorkingDirs workingDirs;

    public ProfilePostCreateActionImpl(WorkingDirs workingDirs) {
        this.workingDirs = workingDirs;
    }
    
    @Override
    public void execute(ProfileInfo profileInfo) {
        List<StackBasedRecord> executionSamples = new RecordingFileIterator<>(
                workingDirs.profileRecording(profileInfo),
                new StacktraceBasedEventProcessor(EventType.EXECUTION_SAMPLE))
                .collect();

        StackTraceBuilder stackTraceBuilder = new StackTraceBuilder();
        for (StackBasedRecord record : executionSamples) {
            stackTraceBuilder.addStackTrace(record.stackTrace());
        }
    }
}
