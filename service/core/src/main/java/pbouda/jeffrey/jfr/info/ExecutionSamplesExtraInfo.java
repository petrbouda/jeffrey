package pbouda.jeffrey.jfr.info;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.jfr.ProfileSettingsProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

public class ExecutionSamplesExtraInfo implements Consumer<ObjectNode> {

    private final Path recording;

    public ExecutionSamplesExtraInfo(Path recording) {
        this.recording = recording;
    }

    @Override
    public void accept(ObjectNode json) {
        Map<String, String> settings = new RecordingFileIterator<>(recording, new ProfileSettingsProcessor())
                .collect();

        ObjectNode extras = Json.createObject()
                .put("source", settings.get("source"))
                .put("cpu_event", settings.get("cpu_event"));

        json.set("extras", extras);
    }
}
