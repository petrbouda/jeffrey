package pbouda.jeffrey.generator.heatmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class D3HeatmapEventProcessor implements EventProcessor {

    private static final int MILLIS = 1000;
    private static final int BUCKET_SIZE = 20;
    private static final int BUCKET_COUNT = MILLIS / BUCKET_SIZE;
    private static final int[] ROWS_VALUES = new int[BUCKET_COUNT];
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // generate an array with values from 980 -> 0
        int curr = Integer.MAX_VALUE;
        for (int i = 0; curr > 0; i++) {
            curr = MILLIS - (BUCKET_SIZE * (i + 1));
            ROWS_VALUES[i] = curr;
        }
    }

    private final String eventName;
    private final long startTimeMillis;
    private final OutputStream output;
    private final Instant endTime;
    private final List<Column> columns = new ArrayList<>();

    private int maxvalue = 0;

    public D3HeatmapEventProcessor(HeatmapConfig config, OutputStream output) {
        this(config.eventName(), config.profilingStartTime(), config.heatmapStart(), config.duration(), output);
    }

    public D3HeatmapEventProcessor(
            String eventName,
            Instant profilingStart,
            Duration heatmapStart,
            Duration duration,
            OutputStream output) {

        this.eventName = eventName;
        Instant startTime = profilingStart.plus(heatmapStart);
        this.startTimeMillis = startTime.toEpochMilli();
        this.output = output;

        if (duration != null && !duration.isZero()) {
            this.endTime = startTime.plus(duration);
        } else {
            this.endTime = null;
        }
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
        Instant eventTime = event.getStartTime();

        // This event is after the end of the processing, skip it.
        // We cannot finish the whole processing, the events are not sorted by time.
        // TODO: More sophisticated parsing using chunks? Skip when the chunk was created after the end-time?
        if (endTime != null && eventTime.isAfter(endTime)) {
            return Result.CONTINUE;
        }

        Instant relative = eventTime.minusMillis(startTimeMillis);
        int relativeSeconds = (int) relative.getEpochSecond();
        int millisInSecond = relative.get(ChronoField.MILLI_OF_SECOND);

        // Value for the new second/column arrived, then create a new column for it.
        int expectedColumns = relativeSeconds + 1;
        if (expectedColumns > columns.size()) {
            appendMoreColumns(expectedColumns);
        }

        // Increment a value in the bucket and return a new value to track the
        // `maxvalue` from all buckets and columns.
        int newValue = columns.get(relativeSeconds).increment(millisInSecond);
        if (newValue > maxvalue) {
            maxvalue = newValue;
        }

        return Result.CONTINUE;
    }

    @Override
    public void onComplete() {
        if (columns.isEmpty()) {
            return;
        }

        try {
            int[] columnsArray = IntStream.range(0, columns.size()).toArray();
            D3HeatmapModel model = new D3HeatmapModel(columnsArray, ROWS_VALUES, maxvalue, generateMatrix(columns));
            MAPPER.writeValue(output, model);
        } catch (IOException e) {
            throw new RuntimeException("Cannot write the output of the Heatmap generator to an output stream", e);
        }
    }

    private static int[][] generateMatrix(List<Column> columns) {
        int[][] matrix = new int[columns.size()][];
        for (int i = 0; i < columns.size(); i++) {
            matrix[i] = columns.get(i).buckets;
        }
        return matrix;
    }

    private void appendMoreColumns(long newSize) {
        long columnsToAdd = newSize - columns.size();
        for (int i = 0; i < columnsToAdd; i++) {
            columns.add(new Column());
        }
    }

    private static final class Column {
        private final int[] buckets;

        private Column() {
            this.buckets = new int[BUCKET_COUNT];
        }

        private int increment(int i) {
            int bucket = invertBucket(i / BUCKET_SIZE);
            int newValue = ++buckets[bucket];
            buckets[bucket] = newValue;
            return newValue;
        }

        /**
         * In D3 Heatmap the buckets are inverted.
         * Example: 1st index of the array is 980-1000, the last one is 0-20.
         *
         * @param bucket the regular index of the bucket.
         * @return inverted bucket to aligned with 3D Heatmap specification.
         */
        private static int invertBucket(int bucket) {
            // value of the last bucket - the regular index of the bucket
            return (BUCKET_COUNT - 1) - bucket;
        }
    }
}
