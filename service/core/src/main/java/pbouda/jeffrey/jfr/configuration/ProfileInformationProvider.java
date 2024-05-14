package pbouda.jeffrey.jfr.configuration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class ProfileInformationProvider implements Supplier<ObjectNode> {

    private static final List<Type> EVENT_TYPES = List.of(
            Type.JVM_INFORMATION,
            Type.CONTAINER_CONFIGURATION,
            Type.CPU_INFORMATION,
            Type.OS_INFORMATION,
            Type.GC_CONFIGURATION,
            Type.GC_HEAP_CONFIGURATION,
            Type.GC_SURVIVOR_CONFIGURATION,
            Type.GC_TLAB_CONFIGURATION,
            Type.YOUNG_GENERATION_CONFIGURATION,
            Type.COMPILER_CONFIGURATION,
            Type.VIRTUALIZATION_INFORMATION
    );

    private final Path recording;

    public ProfileInformationProvider(Path recording) {
        this.recording = recording;
    }

    @Override
    public ObjectNode get() {
        ObjectNode result = Json.createObject();
        for (Type eventType : EVENT_TYPES) {
            new RecordingFileIterator<>(recording, new JsonFieldEventProcessor(eventType))
                    .collect()
                    .ifPresent(json -> result.set(json.name(), json.content()));
        }
        return result;
    }
}
