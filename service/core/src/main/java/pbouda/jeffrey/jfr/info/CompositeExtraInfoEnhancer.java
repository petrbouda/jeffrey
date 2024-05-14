package pbouda.jeffrey.jfr.info;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;
import pbouda.jeffrey.jfr.ProfileSettingsProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

public class CompositeExtraInfoEnhancer implements BiConsumer<EventType, ObjectNode> {

    private final Path recording;

    private List<ExtraInfoEnhancer> enhancers;

    public CompositeExtraInfoEnhancer(Path recording) {
        this.recording = recording;
    }

    public void initialize() {
        var settings = new RecordingFileIterator<>(recording, new ProfileSettingsProcessor())
                .collect();

        this.enhancers = List.of(
                new ExecutionSamplesExtraInfo(settings),
                new AllocationSamplesExtraInfo(settings)
        );
    }

    @Override
    public void accept(EventType eventType, ObjectNode jsonNodes) {
        for (ExtraInfoEnhancer enhancer : enhancers) {
            if (enhancer.isApplicable(eventType)) {
                enhancer.accept(jsonNodes);
            }
        }
    }
}
