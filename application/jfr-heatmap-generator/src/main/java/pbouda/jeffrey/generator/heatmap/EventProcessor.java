package pbouda.jeffrey.generator.heatmap;

import jdk.jfr.consumer.RecordedEvent;

import java.util.List;

public interface EventProcessor {

    enum Result {
        CONTINUE, DONE
    }

    /**
     * A collection contains all name of events that can be processed by the implementation of this interface.
     * Other events will be skipped and method {@link #onEvent(RecordedEvent)} won't be invoked.
     *
     * @return all events eligible for processing.
     */
    List<String> processableEvents();

    /**
     * This method is called before any event is passed to the processor.
     */
    void onStart();

    /**
     * Processes incoming event. This method is invoked after calling {@link #onStart()}, before {@link #onComplete()}
     * and can be called multiple-times.
     * Methods returns the result of the processing, if we want to continue, or the processing has been finished.
     * If {@link Result#DONE} is returned, then the {@link #onComplete()} is supposed to be called anyway.
     *
     * @param event event to process.
     * @return result of the processing.
     */
    Result onEvent(RecordedEvent event);

    /**
     * Finalizes the event processing. No more events will deliver after calling this method.
     */
    void onComplete();
}
