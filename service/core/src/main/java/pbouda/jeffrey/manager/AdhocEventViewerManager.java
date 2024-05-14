package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.repository.model.ProfileInfo;
import pbouda.jeffrey.viewer.EventViewerGenerator;

public class AdhocEventViewerManager implements EventViewerManager {

    private final ProfileInfo profileInfo;
    private final WorkingDirs workingDirs;
    private final EventViewerGenerator generator;

    public AdhocEventViewerManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            EventViewerGenerator generator) {

        this.profileInfo = profileInfo;
        this.workingDirs = workingDirs;
        this.generator = generator;
    }

    @Override
    public JsonNode allEventTypes() {
        return generator.allEventTypes(workingDirs.profileRecording(profileInfo));
    }

    @Override
    public JsonNode events(Type eventType) {
        return generator.events(workingDirs.profileRecording(profileInfo), eventType);
    }

    @Override
    public JsonNode eventColumns(Type eventType) {
        return generator.eventColumns(workingDirs.profileRecording(profileInfo), eventType);
    }
}
