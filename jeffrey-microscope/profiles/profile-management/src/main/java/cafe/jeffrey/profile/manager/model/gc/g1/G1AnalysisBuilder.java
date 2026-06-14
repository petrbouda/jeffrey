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

package cafe.jeffrey.profile.manager.model.gc.g1;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData.EvacuationEntry;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData.EvacuationFailure;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData.G1Header;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData.GcLockerEntry;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData.PausePhase;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData.RegionCell;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData.RegionSnapshot;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData.SystemGcEntry;
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
import java.util.TreeMap;
import java.util.function.ToLongFunction;

/**
 * Single-pass aggregator over the full G1 event set that produces {@link G1AnalysisData}. The
 * stream mixes many event types (see {@code GarbageCollectionManagerImpl.g1Analysis}); each is
 * routed by {@link GenericRecord#type()}.
 */
public class G1AnalysisBuilder implements RecordBuilder<GenericRecord, G1AnalysisData> {

    private static final String GC_ID_FIELD = "gcId";
    private static final String NAME_FIELD = "name";
    private static final String TYPE_FIELD = "type";
    private static final String SUM_OF_PAUSES_FIELD = "sumOfPauses";
    private static final String WHEN_FIELD = "when";
    private static final String EDEN_USED_FIELD = "edenUsedSize";
    private static final String SURVIVOR_USED_FIELD = "survivorUsedSize";
    private static final String OLD_USED_FIELD = "oldGenUsedSize";
    private static final String NUMBER_OF_REGIONS_FIELD = "numberOfRegions";
    private static final String INDEX_FIELD = "index";
    private static final String USED_FIELD = "used";
    private static final String CSET_REGIONS_FIELD = "cSetRegions";
    private static final String CSET_USED_BEFORE_FIELD = "cSetUsedBefore";
    private static final String CSET_USED_AFTER_FIELD = "cSetUsedAfter";
    private static final String ALLOCATION_REGIONS_FIELD = "allocationRegions";
    private static final String BYTES_COPIED_FIELD = "bytesCopied";
    private static final String REGIONS_FREED_FIELD = "regionsFreed";
    private static final String INVOKED_CONCURRENT_FIELD = "invokedConcurrent";
    private static final String LOCK_COUNT_FIELD = "lockCount";
    private static final String STALL_COUNT_FIELD = "stallCount";

    private static final String WHEN_AFTER_GC = "After GC";
    private static final String MIXED_TYPE = "Mixed";
    private static final String FULL_MARKER = "full";

    private static final String EDEN_SERIES = "Eden";
    private static final String SURVIVOR_SERIES = "Survivor";
    private static final String OLD_SERIES = "Old";
    private static final long CARRY_FORWARD_MARK = 0L;

    private static final int MAX_PAUSE_PHASES = 25;
    private static final int MAX_REGION_SNAPSHOTS = 8;
    private static final int MAX_REGIONS_PER_SNAPSHOT = 4096;
    private static final int MAX_EVACUATIONS = 200;
    private static final int MAX_SYSTEM_GCS = 200;
    private static final int MAX_GC_LOCKERS = 200;

    private static final class PhaseAcc {
        private final int level;
        private long count;
        private long total;
        private long max;

        private PhaseAcc(int level) {
            this.level = level;
        }

        private void add(long nanos) {
            count++;
            total += nanos;
            max = Math.max(max, nanos);
        }
    }

    private final Map<Long, String> gcNames = new HashMap<>();
    private final Map<Long, Long> gcPauseNanos = new HashMap<>();
    private final Map<Long, String> g1Types = new HashMap<>();
    private final Map<String, PhaseAcc> phases = new HashMap<>();
    private final LongLongHashMap edenSeries;
    private final LongLongHashMap survivorSeries;
    private final LongLongHashMap oldSeries;
    private final TreeMap<Long, List<RegionCell>> regionSnapshots = new TreeMap<>();
    private final List<EvacuationEntry> evacuations = new ArrayList<>();
    private final Map<Long, Long> evacuationFailures = new HashMap<>();
    private final List<SystemGcEntry> systemGcs = new ArrayList<>();
    private final List<GcLockerEntry> gcLockers = new ArrayList<>();
    private int regionCount;

    public G1AnalysisBuilder(RelativeTimeRange timeRange) {
        this.edenSeries = TimeseriesUtils.initWithZeros(timeRange);
        this.survivorSeries = TimeseriesUtils.initWithZeros(timeRange);
        this.oldSeries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String type = record.type().code();
        switch (type) {
            case EventTypeName.GARBAGE_COLLECTION -> onGarbageCollection(fields);
            case EventTypeName.G1_GARBAGE_COLLECTION -> onG1GarbageCollection(fields);
            case EventTypeName.GC_PHASE_PAUSE -> addPhase(fields, record, 0);
            case EventTypeName.GC_PHASE_PAUSE_LEVEL_1 -> addPhase(fields, record, 1);
            case EventTypeName.GC_PHASE_PAUSE_LEVEL_2 -> addPhase(fields, record, 2);
            case EventTypeName.GC_PHASE_PAUSE_LEVEL_3 -> addPhase(fields, record, 3);
            case EventTypeName.GC_PHASE_PAUSE_LEVEL_4 -> addPhase(fields, record, 4);
            case EventTypeName.GC_PHASE_PARALLEL -> addPhase(fields, record, -1);
            case EventTypeName.G1_HEAP_SUMMARY -> onHeapSummary(fields, record);
            case EventTypeName.G1_HEAP_REGION_INFORMATION -> onRegionInformation(fields, record);
            case EventTypeName.EVACUATION_INFORMATION -> onEvacuationInformation(fields);
            case EventTypeName.EVACUATION_FAILED -> onEvacuationFailed(fields);
            case EventTypeName.SYSTEM_GC -> onSystemGc(fields, record);
            case EventTypeName.GC_LOCKER -> onGcLocker(fields, record);
            default -> {
                // Unrelated event in the combined stream; ignore.
            }
        }
    }

    private void onGarbageCollection(ObjectNode fields) {
        long gcId = Json.readLong(fields, GC_ID_FIELD);
        if (gcId < 0) {
            return;
        }
        gcNames.put(gcId, Json.readString(fields, NAME_FIELD));
        gcPauseNanos.put(gcId, Math.max(0, Json.readLong(fields, SUM_OF_PAUSES_FIELD)));
    }

    private void onG1GarbageCollection(ObjectNode fields) {
        long gcId = Json.readLong(fields, GC_ID_FIELD);
        if (gcId >= 0) {
            g1Types.put(gcId, Json.readString(fields, TYPE_FIELD));
        }
    }

    private void addPhase(ObjectNode fields, GenericRecord record, int level) {
        String name = Json.readString(fields, NAME_FIELD);
        if (name == null) {
            return;
        }
        phases.computeIfAbsent(name, key -> new PhaseAcc(level)).add(record.duration().toNanos());
    }

    private void onHeapSummary(ObjectNode fields, GenericRecord record) {
        int regions = Json.readInt(fields, NUMBER_OF_REGIONS_FIELD);
        if (regions > regionCount) {
            regionCount = regions;
        }
        if (!WHEN_AFTER_GC.equals(Json.readString(fields, WHEN_FIELD))) {
            return;
        }
        long seconds = record.timestampFromStart().toSeconds();
        accumulateMax(edenSeries, seconds, Json.readLong(fields, EDEN_USED_FIELD));
        accumulateMax(survivorSeries, seconds, Json.readLong(fields, SURVIVOR_USED_FIELD));
        accumulateMax(oldSeries, seconds, Json.readLong(fields, OLD_USED_FIELD));
    }

    private static void accumulateMax(LongLongHashMap series, long seconds, long value) {
        if (value >= 0) {
            series.updateValue(seconds, 0, existing -> Math.max(existing, value));
        }
    }

    private void onRegionInformation(ObjectNode fields, GenericRecord record) {
        long bucket = record.timestampFromStart().toMillis();
        int index = Json.readInt(fields, INDEX_FIELD);
        String type = Json.readString(fields, TYPE_FIELD);
        long used = Math.max(0, Json.readLong(fields, USED_FIELD));
        regionSnapshots.computeIfAbsent(bucket, key -> new ArrayList<>())
                .add(new RegionCell(index, type, used));
    }

    private void onEvacuationInformation(ObjectNode fields) {
        long gcId = Json.readLong(fields, GC_ID_FIELD);
        if (gcId < 0) {
            return;
        }
        evacuations.add(new EvacuationEntry(
                gcId,
                Math.max(0, Json.readInt(fields, CSET_REGIONS_FIELD)),
                Math.max(0, Json.readLong(fields, CSET_USED_BEFORE_FIELD)),
                Math.max(0, Json.readLong(fields, CSET_USED_AFTER_FIELD)),
                Math.max(0, Json.readInt(fields, ALLOCATION_REGIONS_FIELD)),
                Math.max(0, Json.readLong(fields, BYTES_COPIED_FIELD)),
                Math.max(0, Json.readInt(fields, REGIONS_FREED_FIELD))));
    }

    private void onEvacuationFailed(ObjectNode fields) {
        long gcId = Json.readLong(fields, GC_ID_FIELD);
        if (gcId >= 0) {
            evacuationFailures.merge(gcId, 1L, Long::sum);
        }
    }

    private void onSystemGc(ObjectNode fields, GenericRecord record) {
        systemGcs.add(new SystemGcEntry(
                record.timestampFromStart().toMillis(),
                record.duration().toNanos(),
                Json.readBoolean(fields, INVOKED_CONCURRENT_FIELD)));
    }

    private void onGcLocker(ObjectNode fields, GenericRecord record) {
        gcLockers.add(new GcLockerEntry(
                record.timestampFromStart().toMillis(),
                record.duration().toNanos(),
                Math.max(0, Json.readInt(fields, LOCK_COUNT_FIELD)),
                Math.max(0, Json.readInt(fields, STALL_COUNT_FIELD))));
    }

    @Override
    public G1AnalysisData build() {
        return new G1AnalysisData(
                buildHeader(),
                buildPhases(),
                buildComposition(),
                buildSnapshots(),
                capByGcId(evacuations, EvacuationEntry::gcId, MAX_EVACUATIONS),
                buildEvacuationFailures(),
                TimeseriesData.empty(),
                List.of(),
                capByOffset(systemGcs, SystemGcEntry::timeOffsetMillis, MAX_SYSTEM_GCS),
                capByOffset(gcLockers, GcLockerEntry::timeOffsetMillis, MAX_GC_LOCKERS));
    }

    private G1Header buildHeader() {
        long young = 0;
        long mixed = 0;
        long full = 0;
        List<Long> pauses = new ArrayList<>(gcPauseNanos.size());
        for (Map.Entry<Long, Long> entry : gcPauseNanos.entrySet()) {
            long gcId = entry.getKey();
            pauses.add(entry.getValue());
            String name = gcNames.get(gcId);
            if (name != null && name.toLowerCase().contains(FULL_MARKER)) {
                full++;
            } else if (MIXED_TYPE.equalsIgnoreCase(g1Types.get(gcId))) {
                mixed++;
            } else {
                young++;
            }
        }
        pauses.sort(Comparator.naturalOrder());
        long total = pauses.stream().mapToLong(Long::longValue).sum();
        long max = pauses.isEmpty() ? 0 : pauses.getLast();
        long avg = pauses.isEmpty() ? 0 : total / pauses.size();
        long p99 = percentile(pauses, 99);
        long failures = evacuationFailures.values().stream().mapToLong(Long::longValue).sum();
        return new G1Header(young, mixed, full, total, avg, max, p99, failures, regionCount);
    }

    private static long percentile(List<Long> sortedAscending, int percentile) {
        if (sortedAscending.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedAscending.size()) - 1;
        return sortedAscending.get(Math.max(0, Math.min(index, sortedAscending.size() - 1)));
    }

    private List<PausePhase> buildPhases() {
        return phases.entrySet().stream()
                .map(entry -> {
                    PhaseAcc acc = entry.getValue();
                    long avg = acc.count == 0 ? 0 : acc.total / acc.count;
                    return new PausePhase(entry.getKey(), acc.level, acc.count, acc.total, acc.max, avg);
                })
                .sorted(Comparator.comparingLong(PausePhase::totalNanos).reversed())
                .limit(MAX_PAUSE_PHASES)
                .toList();
    }

    private TimeseriesData buildComposition() {
        SingleSerie eden = TimeseriesUtils.buildSerie(EDEN_SERIES, edenSeries);
        SingleSerie survivor = TimeseriesUtils.buildSerie(SURVIVOR_SERIES, survivorSeries);
        SingleSerie old = TimeseriesUtils.buildSerie(OLD_SERIES, oldSeries);
        TimeseriesUtils.remapTimeseriesBySteps(eden, CARRY_FORWARD_MARK);
        TimeseriesUtils.remapTimeseriesBySteps(survivor, CARRY_FORWARD_MARK);
        TimeseriesUtils.remapTimeseriesBySteps(old, CARRY_FORWARD_MARK);
        return new TimeseriesData(eden, survivor, old);
    }

    private List<RegionSnapshot> buildSnapshots() {
        List<RegionSnapshot> result = new ArrayList<>();
        for (Map.Entry<Long, List<RegionCell>> entry : regionSnapshots.descendingMap().entrySet()) {
            if (result.size() >= MAX_REGION_SNAPSHOTS) {
                break;
            }
            List<RegionCell> cells = entry.getValue();
            cells.sort(Comparator.comparingInt(RegionCell::index));
            if (cells.size() > MAX_REGIONS_PER_SNAPSHOT) {
                cells = cells.subList(0, MAX_REGIONS_PER_SNAPSHOT);
            }
            result.add(new RegionSnapshot(entry.getKey(), List.copyOf(cells)));
        }
        result.sort(Comparator.comparingLong(RegionSnapshot::timeOffsetMillis));
        return result;
    }

    private List<EvacuationFailure> buildEvacuationFailures() {
        return evacuationFailures.entrySet().stream()
                .map(entry -> new EvacuationFailure(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(EvacuationFailure::gcId).reversed())
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
