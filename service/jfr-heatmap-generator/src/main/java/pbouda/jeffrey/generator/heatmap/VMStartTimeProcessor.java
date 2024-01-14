package pbouda.jeffrey.generator.heatmap;

import jdk.jfr.consumer.RecordedEvent;

import java.time.Instant;
import java.util.List;

public class VMStartTimeProcessor implements EventProcessor {

    private static final String VM_INFO_EVENT = "jdk.JVMInformation";

    private Instant jvmStartTime = null;

    @Override
    public List<String> processableEvents() {
        return List.of(VM_INFO_EVENT);
    }

    @Override
    public void onStart() {
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        this.jvmStartTime = event.getInstant("jvmStartTime");
        return Result.DONE;
    }

    @Override
    public void onComplete() {
        if (jvmStartTime == null) {
            throw new RuntimeException(STR."Expected event was not found: \{VM_INFO_EVENT}");
        }
    }

    public Instant startTime() {
        return jvmStartTime;
    }
}
