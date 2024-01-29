package pbouda.jeffrey.jfr;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.jfrparser.jdk.SingleEventProcessor;

import java.time.Instant;
import java.util.function.Supplier;

public class ProfilingStartTimeProcessor extends SingleEventProcessor implements Supplier<Instant> {

    private Instant profilingStartTime = null;

    public ProfilingStartTimeProcessor() {
        super(EventType.ACTIVE_RECORDING);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        this.profilingStartTime = event.getInstant("recordingStart");
        return Result.DONE;
    }

    @Override
    public void onComplete() {
        if (profilingStartTime == null) {
            throw new RuntimeException("Expected event was not found: " + eventType().code());
        }
    }

    @Override
    public Instant get() {
        return profilingStartTime;
    }
}
