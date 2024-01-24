package pbouda.jeffrey.jfr;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.manager.EventType;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

public class ProfilingStartTimeProcessor implements EventProcessor, Supplier<Instant> {

    private static final EventType EVENT_TYPE = EventType.ACTIVE_RECORDING;

    private Instant profilingStartTime = null;

    @Override
    public List<String> processableEvents() {
        return List.of(EVENT_TYPE.code());
    }

    @Override
    public void onStart() {
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        this.profilingStartTime = event.getInstant("recordingStart");
        return Result.DONE;
    }

    @Override
    public void onComplete() {
        if (profilingStartTime == null) {
            throw new RuntimeException("Expected event was not found: " + EVENT_TYPE.code());
        }
    }

    @Override
    public Instant get() {
        return profilingStartTime;
    }
}
