/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.manager.model.gc.zgc;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.jfrparser.api.type.JfrThread;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData.StallSite;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData.StallType;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData.ZCycle;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData.ZgcHeader;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData.ZRelocationEntry;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData.ZUncommitEntry;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Single-pass aggregator over the ZGC event set producing {@link ZgcAnalysisData}.
 */
public class ZgcAnalysisBuilder implements RecordBuilder<GenericRecord, ZgcAnalysisData> {

    private static final String GC_ID_FIELD = "gcId";
    private static final String TYPE_FIELD = "type";
    private static final String SIZE_FIELD = "size";
    private static final String TENURING_THRESHOLD_FIELD = "tenuringThreshold";
    private static final String UNCOMMITTED_FIELD = "uncommitted";
    private static final String TOTAL_FIELD = "total";
    private static final String EMPTY_FIELD = "empty";
    private static final String RELOCATE_FIELD = "relocate";

    private static final String GENERATION_YOUNG = "Young";
    private static final String GENERATION_OLD = "Old";
    private static final String UNKNOWN_THREAD = "unknown";

    private static final String STALL_COUNT_SERIES = "Allocation Stalls";
    private static final String STALL_TIME_SERIES = "Stall Time";
    private static final String PAGE_ALLOCATION_SERIES = "Page Allocation";

    private static final int MAX_STALL_SITES = 50;
    private static final int MAX_CYCLES = 200;
    private static final int MAX_UNCOMMITS = 200;
    private static final int MAX_RELOCATIONS = 200;

    private static final class StallAcc {
        private long count;
        private long total;
        private long max;

        private void add(long nanos) {
            count++;
            total += nanos;
            max = Math.max(max, nanos);
        }
    }

    private final LongLongHashMap stallCountSeries;
    private final LongLongHashMap stallTimeSeries;
    private final LongLongHashMap pageAllocationSeries;
    private final Map<String, StallAcc> stallsByType = new HashMap<>();
    private final Map<String, StallAcc> stallsByThread = new HashMap<>();
    private final List<ZCycle> cycles = new ArrayList<>();
    private final List<ZUncommitEntry> uncommits = new ArrayList<>();
    private final List<ZRelocationEntry> relocations = new ArrayList<>();
    private long youngCycles;
    private long oldCycles;
    private long stallCount;
    private long totalStallNanos;
    private long maxStallNanos;
    private long pagesAllocatedBytes;
    private long uncommittedBytes;

    public ZgcAnalysisBuilder(RelativeTimeRange timeRange) {
        this.stallCountSeries = TimeseriesUtils.initWithZeros(timeRange);
        this.stallTimeSeries = TimeseriesUtils.initWithZeros(timeRange);
        this.pageAllocationSeries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        switch (record.type().code()) {
            case EventTypeName.Z_ALLOCATION_STALL -> onAllocationStall(fields, record);
            case EventTypeName.Z_YOUNG_GARBAGE_COLLECTION -> onYoungCollection(fields, record);
            case EventTypeName.Z_OLD_GARBAGE_COLLECTION -> onOldCollection(fields, record);
            case EventTypeName.Z_PAGE_ALLOCATION -> onPageAllocation(fields, record);
            case EventTypeName.Z_UNCOMMIT -> onUncommit(fields, record);
            case EventTypeName.Z_RELOCATION_SET -> onRelocationSet(fields, record);
            default -> {
                // Unrelated event in the combined stream; ignore.
            }
        }
    }

    private void onAllocationStall(ObjectNode fields, GenericRecord record) {
        long nanos = record.duration().toNanos();
        long seconds = record.timestampFromStart().toSeconds();
        stallCountSeries.addToValue(seconds, 1);
        stallTimeSeries.addToValue(seconds, nanos);

        stallCount++;
        totalStallNanos += nanos;
        maxStallNanos = Math.max(maxStallNanos, nanos);

        String type = Json.readString(fields, TYPE_FIELD);
        stallsByType.computeIfAbsent(type == null ? UNKNOWN_THREAD : type, key -> new StallAcc()).add(nanos);

        JfrThread thread = record.thread();
        String threadName = thread == null || thread.name() == null ? UNKNOWN_THREAD : thread.name();
        stallsByThread.computeIfAbsent(threadName, key -> new StallAcc()).add(nanos);
    }

    private void onYoungCollection(ObjectNode fields, GenericRecord record) {
        long gcId = Json.readLong(fields, GC_ID_FIELD);
        if (gcId < 0) {
            return;
        }
        youngCycles++;
        cycles.add(new ZCycle(
                gcId,
                GENERATION_YOUNG,
                record.duration().toNanos(),
                Math.max(0, Json.readInt(fields, TENURING_THRESHOLD_FIELD))));
    }

    private void onOldCollection(ObjectNode fields, GenericRecord record) {
        long gcId = Json.readLong(fields, GC_ID_FIELD);
        if (gcId < 0) {
            return;
        }
        oldCycles++;
        cycles.add(new ZCycle(gcId, GENERATION_OLD, record.duration().toNanos(), 0));
    }

    private void onPageAllocation(ObjectNode fields, GenericRecord record) {
        long size = Math.max(0, Json.readLong(fields, SIZE_FIELD));
        pagesAllocatedBytes += size;
        pageAllocationSeries.addToValue(record.timestampFromStart().toSeconds(), size);
    }

    private void onUncommit(ObjectNode fields, GenericRecord record) {
        long bytes = Math.max(0, Json.readLong(fields, UNCOMMITTED_FIELD));
        uncommittedBytes += bytes;
        uncommits.add(new ZUncommitEntry(
                record.timestampFromStart().toMillis(), bytes, record.duration().toNanos()));
    }

    private void onRelocationSet(ObjectNode fields, GenericRecord record) {
        relocations.add(new ZRelocationEntry(
                record.timestampFromStart().toMillis(),
                Math.max(0, Json.readLong(fields, TOTAL_FIELD)),
                Math.max(0, Json.readLong(fields, EMPTY_FIELD)),
                Math.max(0, Json.readLong(fields, RELOCATE_FIELD))));
    }

    @Override
    public ZgcAnalysisData build() {
        ZgcHeader header = new ZgcHeader(
                youngCycles, oldCycles, stallCount, totalStallNanos, maxStallNanos,
                pagesAllocatedBytes, uncommittedBytes);

        SingleSerie countSerie = TimeseriesUtils.buildSerie(STALL_COUNT_SERIES, stallCountSeries);
        SingleSerie timeSerie = TimeseriesUtils.buildSerie(STALL_TIME_SERIES, stallTimeSeries);
        SingleSerie pageSerie = TimeseriesUtils.buildSerie(PAGE_ALLOCATION_SERIES, pageAllocationSeries);

        return new ZgcAnalysisData(
                header,
                new TimeseriesData(countSerie, timeSerie),
                buildStallTypes(),
                buildStallSites(),
                capByGcId(cycles, ZCycle::gcId, MAX_CYCLES),
                new TimeseriesData(pageSerie),
                capByOffset(uncommits, ZUncommitEntry::timeOffsetMillis, MAX_UNCOMMITS),
                capByOffset(relocations, ZRelocationEntry::timeOffsetMillis, MAX_RELOCATIONS));
    }

    private List<StallType> buildStallTypes() {
        return stallsByType.entrySet().stream()
                .map(entry -> new StallType(
                        entry.getKey(), entry.getValue().count, entry.getValue().total, entry.getValue().max))
                .sorted(Comparator.comparingLong(StallType::totalNanos).reversed())
                .toList();
    }

    private List<StallSite> buildStallSites() {
        return stallsByThread.entrySet().stream()
                .map(entry -> new StallSite(entry.getKey(), entry.getValue().count, entry.getValue().total))
                .sorted(Comparator.comparingLong(StallSite::totalNanos).reversed())
                .limit(MAX_STALL_SITES)
                .toList();
    }

    private static <T> List<T> capByGcId(List<T> entries, ToLongFunction<T> gcId, int max) {
        return entries.stream()
                .sorted(Comparator.comparingLong(gcId).reversed())
                .limit(max)
                .toList();
    }

    private static <T> List<T> capByOffset(List<T> entries, ToLongFunction<T> offset, int max) {
        return entries.stream()
                .sorted(Comparator.comparingLong(offset).reversed())
                .limit(max)
                .toList();
    }
}
