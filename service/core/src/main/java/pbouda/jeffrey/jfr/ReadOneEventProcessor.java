package pbouda.jeffrey.jfr;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

import java.util.function.Supplier;

public class ReadOneEventProcessor implements EventProcessor, Supplier<Boolean> {

    private boolean eventArrived = false;

    @Override
    public ProcessableEvents processableEvents() {
        return ProcessableEvents.all();
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        eventArrived = true;
        return Result.DONE;
    }

    @Override
    public Boolean get() {
        return eventArrived;
    }
}
