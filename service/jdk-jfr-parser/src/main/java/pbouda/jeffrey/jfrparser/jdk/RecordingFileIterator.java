package pbouda.jeffrey.jfrparser.jdk;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class RecordingFileIterator<R, T extends EventProcessor & Supplier<R>> {

    private final Path recording;
    private final T processor;

    public RecordingFileIterator(Path recording) {
        this(recording, null);
    }

    public RecordingFileIterator(Path recording, T processor) {
        this.recording = recording;
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
        if (!Files.exists(recording)) {
            throw new RuntimeException("File does not exists: " + recording);
        }

        try (RecordingFile rec = new RecordingFile(recording)) {
            eventProcessor.onStart();
            while (rec.hasMoreEvents()) {
                RecordedEvent event = rec.readEvent();
                if (eventProcessor.processableEvents().isProcessable(event.getEventType())) {
                    Result result = eventProcessor.onEvent(event);
                    if (result == Result.DONE) {
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
