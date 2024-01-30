package pbouda.jeffrey.jfr.configuration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.Json;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class ProfileInformationProvider implements Supplier<ObjectNode> {

    private static final List<EventType> EVENT_TYPES = List.of(
            EventType.JVM_INFORMATION,
            EventType.CONTAINER_CONFIGURATION,
            EventType.CPU_INFORMATION,
            EventType.OS_INFORMATION,
            EventType.GC_CONFIGURATION,
            EventType.GC_HEAP_CONFIGURATION,
            EventType.GC_SURVIVOR_CONFIGURATION,
            EventType.GC_TLAB_CONFIGURATION,
            EventType.YOUNG_GENERATION_CONFIGURATION,
            EventType.COMPILER_CONFIGURATION,
            EventType.VIRTUALIZATION_INFORMATION
    );

    private final Path recording;

    public ProfileInformationProvider(Path recording) {
        this.recording = recording;
    }

    @Override
    public ObjectNode get() {
        ObjectNode result = Json.createObject();
        for (EventType eventType : EVENT_TYPES) {
            new RecordingFileIterator<>(recording, new JsonFieldEventProcessor(eventType))
                    .collect()
                    .ifPresent(json -> result.set(json.name(), json.content()));
        }
        return result;
    }
}
