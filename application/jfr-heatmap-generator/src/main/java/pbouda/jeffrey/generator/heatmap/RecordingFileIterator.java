package pbouda.jeffrey.generator.heatmap;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RecordingFileIterator {

    private final Path jfrFile;

    public RecordingFileIterator(Path jfrFile) {
        this.jfrFile = jfrFile;
    }

    public void iterate(EventProcessor eventProcessor) {
        if (!Files.exists(jfrFile)) {
            throw new RuntimeException(STR."File does not exists: \{jfrFile}");
        }

        List<String> strings = eventProcessor.processableEvents();

        try (RecordingFile rec = new RecordingFile(jfrFile)) {
            eventProcessor.onStart();
            while (rec.hasMoreEvents()) {
                RecordedEvent event = rec.readEvent();
                if (strings.contains(event.getEventType().getName())) {
                    EventProcessor.Result result = eventProcessor.onEvent(event);
                    if (result == EventProcessor.Result.DONE) {
                        break;
                    }
                }
            }
            eventProcessor.onComplete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
