package pbouda.jeffrey.jfr;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ProfileSettingsProcessor implements EventProcessor, Supplier<Map<String, String>> {

    private static final BitSet COMPLETE_BITSET = BitSet.valueOf(new byte[]{1, 1});
    private static final int SOURCE_BIT_INDEX = 0;
    private static final int CPU_EVENT_BIT_INDEX = 1;
    private static final int ALLOC_EVENT_BIT_INDEX = 2;

    private final BitSet isComplete = new BitSet(2);

    private final Map<String, String> mappedValues = new HashMap<>();

    @Override
    public ProcessableEvents processableEvents() {
        return new ProcessableEvents(List.of(Type.ACTIVE_SETTING, Type.ACTIVE_RECORDING));
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        if (Type.ACTIVE_RECORDING.sameAs(event)) {
            String name = event.getString("name");

            if (name.startsWith("async-profiler")) {
                mappedValues.put("source", EventSource.ASYNC_PROFILER.name());
            } else {
                mappedValues.put("source", EventSource.JDK.name());
            }
            isComplete.set(SOURCE_BIT_INDEX);
        } else {
            String nameValue = event.getString("name");
            if ("event".equals(nameValue)) {
                mappedValues.put("cpu_event", event.getString("value"));
                isComplete.set(CPU_EVENT_BIT_INDEX);
            } else if ("alloc".equals(nameValue)) {
                mappedValues.put("alloc_event", event.getString("value"));
                isComplete.set(ALLOC_EVENT_BIT_INDEX);
            }
        }

        if (COMPLETE_BITSET.equals(isComplete)) {
            return Result.DONE;
        } else {
            return Result.CONTINUE;
        }
    }

    @Override
    public Map<String, String> get() {
        return mappedValues;
    }
}
