package pbouda.jeffrey.jfrparser.jdk;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class RecordingFileIterator<R, T extends EventProcessor & Supplier<R>> {

    private final Path jfrFile;
    private final T processor;

    public RecordingFileIterator(Path jfrFile) {
        this(jfrFile, null);
    }

    public RecordingFileIterator(Path jfrFile, T processor) {
        this.jfrFile = jfrFile;
        this.processor = processor;
    }

    public R collect() {
        Objects.requireNonNull(processor, "processor needs to be added to constructor to be able to collect result");

        _iterate(processor);
        return processor.get();
    }

    public void iterate(EventProcessor eventProcessor) {
        _iterate(eventProcessor);
    }

    private void _iterate(EventProcessor eventProcessor) {
        if (!Files.exists(jfrFile)) {
            throw new RuntimeException("File does not exists: " + jfrFile);
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
