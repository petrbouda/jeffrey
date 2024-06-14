/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.generator.heatmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.jdk.SingleEventProcessor;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

public class HeatmapEventProcessor extends SingleEventProcessor {

    private static final int MILLIS = 1000;
    private static final int BUCKET_SIZE = 20;
    private static final int BUCKET_COUNT = MILLIS / BUCKET_SIZE;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final long startTimeMillis;
    private final OutputStream output;
    private final Instant endTime;
    private final List<Column> columns = new ArrayList<>();
    private final boolean collectWeight;

    private long maxvalue = 0;

    public HeatmapEventProcessor(HeatmapConfig config, OutputStream output) {
        this(config.eventType(), config.profilingStartTime(), config.heatmapStart(),
                config.duration(), config.collectWeight(), output);
    }

    public HeatmapEventProcessor(
            Type eventType,
            Instant profilingStart,
            Duration heatmapStart,
            Duration duration,
            boolean collectWeight,
            OutputStream output) {

        super(eventType);
        this.collectWeight = collectWeight;

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

        long value = 1;
        if (collectWeight) {
            value = eventType()
                    .weightExtractor()
                    .apply(event);
        }

        // Increment a value in the bucket and return a new value to track the
        // `maxvalue` from all buckets and columns.
        long newValue = columns.get(relativeSeconds).increment(millisInSecond, value);
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
            long[][] matrix = generateMatrix(columns);

            HeatmapModel model = new HeatmapModel(maxvalue, formatMatrix(matrix));
            MAPPER.writeValue(output, model);
        } catch (IOException e) {
            throw new RuntimeException("Cannot write the output of the Heatmap generator to an output stream", e);
        }
    }

    private static ArrayNode formatMatrix(long[][] matrix) {
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

    private static long[][] generateMatrix(List<Column> columns) {
        long[][] matrix = new long[BUCKET_COUNT][];
        for (int i = 0; i < BUCKET_COUNT; i++) {
            long[] row = new long[columns.size()];
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
        private final long[] buckets;

        private Column() {
            this.buckets = new long[BUCKET_COUNT];
        }

        private long increment(int i, long value) {
            int bucket = i / BUCKET_SIZE;
            long newValue = buckets[bucket] + value;
            buckets[bucket] = newValue;
            return newValue;
        }
    }
}
