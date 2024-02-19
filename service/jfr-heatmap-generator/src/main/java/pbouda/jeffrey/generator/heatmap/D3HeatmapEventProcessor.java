package pbouda.jeffrey.generator.heatmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.jfrparser.jdk.SingleEventProcessor;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

public class D3HeatmapEventProcessor extends SingleEventProcessor {

    private static final int MILLIS = 1000;
    private static final int BUCKET_SIZE = 20;
    private static final int BUCKET_COUNT = MILLIS / BUCKET_SIZE;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final long startTimeMillis;
    private final OutputStream output;
    private final Instant endTime;
    private final List<Column> columns = new ArrayList<>();

    private int maxvalue = 0;

    public D3HeatmapEventProcessor(HeatmapConfig config, OutputStream output) {
        this(config.eventType(), config.profilingStartTime(), config.heatmapStart(), config.duration(), output);
    }

    public D3HeatmapEventProcessor(
            EventType eventType,
            Instant profilingStart,
            Duration heatmapStart,
            Duration duration,
            OutputStream output) {

        super(eventType);

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
            int[][] matrix = generateMatrix(columns);

            D3HeatmapModel model = new D3HeatmapModel(maxvalue, formatMatrix(matrix));
            MAPPER.writeValue(output, model);
        } catch (IOException e) {
            throw new RuntimeException("Cannot write the output of the Heatmap generator to an output stream", e);
        }
    }

    private static ArrayNode formatMatrix(int[][] matrix) {
        ArrayNode output = MAPPER.createArrayNode();

        for (int i = 0; i < matrix.length; i++) {
            ObjectNode row = MAPPER.createObjectNode();
            row.put("name", String.valueOf(i * BUCKET_SIZE));

            ArrayNode cells = MAPPER.createArrayNode();
            for (int j = 0; j < matrix[i].length; j++) {
                ObjectNode cell = MAPPER.createObjectNode();
                cell.put("x", String.valueOf(j + 1));
                cell.put("y", matrix[i][j]);
                cells.add(cell);
            }

            row.set("data", cells);
            output.add(row);
        }

        return output;
    }

    private static int[][] generateMatrix(List<Column> columns) {
        int[][] matrix = new int[BUCKET_COUNT][];
        for (int i = 0; i < BUCKET_COUNT; i++) {
            int[] row = new int[columns.size()];
            for (int j = 0; j < columns.size(); j++) {
                row[j] = columns.get(j).buckets[i];
            }
            matrix[i] = row;
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
            int bucket = i / BUCKET_SIZE;
            int newValue = ++buckets[bucket];
            buckets[bucket] = newValue;
            return newValue;
        }
    }
}
