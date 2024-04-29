package pbouda.jeffrey.manager.action;

import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.repository.model.ProfileInfo;

public class ProfilePostCreateActionImpl implements ProfilePostCreateAction {

    private final WorkingDirs workingDirs;

    public ProfilePostCreateActionImpl(WorkingDirs workingDirs) {
        this.workingDirs = workingDirs;
    }
    
    @Override
    public void execute(ProfileInfo profileInfo) {
//        List<StackBasedRecord> executionSamples = new RecordingFileIterator<>(
//                workingDirs.profileRecording(profileInfo),
//                new StacktraceBasedEventProcessor(EventType.EXECUTION_SAMPLE))
//                .collect();
//
//        StackTraceBuilder stackTraceBuilder = new StackTraceBuilder();
//        for (StackBasedRecord record : executionSamples) {
//            stackTraceBuilder.addStackTrace(record.stackTrace());
//        }
    }
}
