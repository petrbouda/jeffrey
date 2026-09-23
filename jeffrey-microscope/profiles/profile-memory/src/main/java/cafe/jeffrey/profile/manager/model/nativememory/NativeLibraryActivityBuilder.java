/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.manager.model.nativememory;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryActivityData.Header;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryActivityData.LibraryOperation;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryActivityData.Operation;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregates {@code jdk.NativeLibraryLoad} / {@code jdk.NativeLibraryUnload} into headline counters, a
 * per-operation list (slowest first) and per-second load/unload count timelines.
 */
public class NativeLibraryActivityBuilder
        implements RecordBuilder<GenericRecord, NativeLibraryActivityData> {

    private static final String NAME_FIELD = "name";
    private static final String SUCCESS_FIELD = "success";
    private static final String ERROR_MESSAGE_FIELD = "errorMessage";
    private static final String UNKNOWN_LIBRARY = "unknown";
    private static final String LOADS_SERIES = "Loads";
    private static final String UNLOADS_SERIES = "Unloads";

    private final int maxOps;
    private final LongLongHashMap loadsSeries;
    private final LongLongHashMap unloadsSeries;
    private final List<LibraryOperation> operations = new ArrayList<>();
    private long totalLoads;
    private long failedLoads;
    private long totalUnloads;
    private long slowestLoadNanos;
    private long totalLoadNanos;
    private String slowestLibrary;

    public NativeLibraryActivityBuilder(RelativeTimeRange timeRange, int maxOps) {
        if (maxOps <= 0) {
            throw new IllegalArgumentException("maxOps must be positive: " + maxOps);
        }
        this.maxOps = maxOps;
        this.loadsSeries = TimeseriesUtils.initWithZeros(timeRange);
        this.unloadsSeries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        Operation operation = Type.NATIVE_LIBRARY_UNLOAD.sameAs(record.type()) ? Operation.UNLOAD : Operation.LOAD;

        String name = Json.readString(fields, NAME_FIELD);
        boolean success = Json.readBoolean(fields, SUCCESS_FIELD);
        String errorMessage = Json.readString(fields, ERROR_MESSAGE_FIELD);
        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : Math.max(0, duration.toNanos());
        long seconds = record.timestampFromStart().toSeconds();

        operations.add(new LibraryOperation(
                operation,
                name == null ? UNKNOWN_LIBRARY : name,
                record.timestampFromStart().toMillis(),
                durationNanos,
                success,
                errorMessage == null || errorMessage.isBlank() ? null : errorMessage));

        if (operation == Operation.LOAD) {
            totalLoads++;
            totalLoadNanos += durationNanos;
            if (!success) {
                failedLoads++;
            }
            if (durationNanos > slowestLoadNanos) {
                slowestLoadNanos = durationNanos;
                slowestLibrary = name;
            }
            loadsSeries.addToValue(seconds, 1);
        } else {
            totalUnloads++;
            unloadsSeries.addToValue(seconds, 1);
        }
    }

    @Override
    public NativeLibraryActivityData build() {
        List<LibraryOperation> sorted = operations.stream()
                .sorted(Comparator.comparingLong(LibraryOperation::durationNanos).reversed())
                .limit(maxOps)
                .toList();

        SingleSerie loadsSerie = TimeseriesUtils.buildSerie(LOADS_SERIES, loadsSeries);
        SingleSerie unloadsSerie = TimeseriesUtils.buildSerie(UNLOADS_SERIES, unloadsSeries);

        Header header = new Header(
                totalLoads, failedLoads, totalUnloads, slowestLoadNanos, totalLoadNanos, slowestLibrary);

        return new NativeLibraryActivityData(header, sorted, new TimeseriesData(loadsSerie, unloadsSerie));
    }
}
