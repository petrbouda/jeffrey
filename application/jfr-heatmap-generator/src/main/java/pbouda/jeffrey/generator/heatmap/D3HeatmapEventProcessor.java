package pbouda.jeffrey.generator.heatmap;

import jdk.jfr.consumer.RecordedEvent;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class D3HeatmapEventProcessor implements EventProcessor {

    private static final int MILLIS = 1000;
    private static final int MILLIS_BUCKET = 20;
    private static final int[] COLUMN = new int[MILLIS / MILLIS_BUCKET];

    static {
        // generate an array with values from 980 -> 0
        int curr = Integer.MAX_VALUE;
        for (int i = 0; curr > 0; i++) {
            curr = MILLIS - (MILLIS_BUCKET * (i + 1));
            COLUMN[i] = curr;
        }
    }

    private final String eventName;
    private final long vmStartTime;
    private final int[][] matrix = {};

    public D3HeatmapEventProcessor(String eventName, Instant vmStartTime) {
        this.eventName = eventName;
        this.vmStartTime = vmStartTime.toEpochMilli();
    }

    @Override
    public List<String> processableEvents() {
        return List.of(eventName);
    }

    @Override
    public void onStart() {
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        Instant relative = event.getStartTime().minusMillis(vmStartTime);
        long relativeSecond = relative.getEpochSecond();
        return null;
    }

    @Override
    public void onComplete() {

    }

    private static int[] newColumn() {
        return Arrays.copyOf(COLUMN, COLUMN.length);
    }
}
