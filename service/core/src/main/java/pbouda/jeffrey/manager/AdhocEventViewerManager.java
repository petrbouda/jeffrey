package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.repository.model.ProfileInfo;
import pbouda.jeffrey.viewer.EventViewerGenerator;

public class AdhocEventViewerManager implements EventViewerManager {

    private final ProfileInfo profileInfo;
    private final EventViewerGenerator generator;

    public AdhocEventViewerManager(
            ProfileInfo profileInfo,
            EventViewerGenerator generator) {

        this.profileInfo = profileInfo;
        this.generator = generator;
    }

    @Override
    public JsonNode allEventTypes() {
        return generator.allEventTypes(profileInfo.recordingPath());
    }
}
