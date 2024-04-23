package pbouda.jeffrey.jfrparser.jdk;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.EventType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public class StacktraceBasedEventProcessor extends SingleEventProcessor implements Supplier<List<StackBasedRecord>> {

    private final List<StackBasedRecord> records = new ArrayList<>();

    public StacktraceBasedEventProcessor(EventType eventType) {
        super(eventType);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        StackBasedRecord record = new StackBasedRecord(
                event.getStartTime(),
                event.getStackTrace(),
                event.getThread("sampledThread"));

        records.add(record);
        return Result.CONTINUE;
    }

    @Override
    public List<StackBasedRecord> get() {
        records.sort(Comparator.comparing(StackBasedRecord::timestamp));
        return records;
    }
}
