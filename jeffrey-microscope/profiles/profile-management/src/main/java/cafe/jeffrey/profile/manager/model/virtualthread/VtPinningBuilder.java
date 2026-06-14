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

package cafe.jeffrey.profile.manager.model.virtualthread;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData.DurationBucket;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData.PinnedThreadStat;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData.PinningReasonStat;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates {@code jdk.VirtualThreadPinned} events into per-second timelines, a duration
 * distribution, and per-thread totals.
 */
public class VtPinningBuilder implements RecordBuilder<GenericRecord, VtPinningBuilder.Result> {

    public record Result(
            long count,
            long totalNanos,
            long maxNanos,
            TimeseriesData timeline,
            List<DurationBucket> distribution,
            List<PinnedThreadStat> topThreads,
            List<PinningReasonStat> reasons) {
    }

    private static final String EVENT_THREAD_FIELD = "eventThread";
    private static final String PINNED_REASON_FIELD = "pinnedReason";
    private static final String UNKNOWN_THREAD = "unknown";
    private static final String UNKNOWN_REASON = "unknown";
    private static final String COUNT_SERIES = "Pinning Events";
    private static final String TIME_SERIES = "Pinned Time";

    // Bucket upper bounds (exclusive) in nanos, paired with labels; the final bucket catches the rest.
    private static final long[] BUCKET_BOUNDS_NANOS = {
            50_000_000L, 100_000_000L, 500_000_000L, 1_000_000_000L};
    private static final String[] BUCKET_LABELS = {
            "< 50 ms", "50–100 ms", "100–500 ms", "500 ms–1 s", "≥ 1 s"};

    private static final class ThreadAcc {
        private long count;
        private long total;
        private long max;

        private void add(long nanos) {
            count++;
            total += nanos;
            max = Math.max(max, nanos);
        }
    }

    private final int maxThreads;
    private final LongLongHashMap countSeries;
    private final LongLongHashMap timeSeries;
    private final long[] bucketCounts = new long[BUCKET_LABELS.length];
    private final Map<String, ThreadAcc> byThread = new HashMap<>();
    private final Map<String, ThreadAcc> byReason = new HashMap<>();
    private long count;
    private long totalNanos;
    private long maxNanos;

    public VtPinningBuilder(RelativeTimeRange timeRange, int maxThreads) {
        if (maxThreads <= 0) {
            throw new IllegalArgumentException("maxThreads must be positive: " + maxThreads);
        }
        this.maxThreads = maxThreads;
        this.countSeries = TimeseriesUtils.initWithZeros(timeRange);
        this.timeSeries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        Duration duration = record.duration();
        long nanos = duration == null ? 0 : Math.max(0, duration.toNanos());
        long seconds = record.timestampFromStart().toSeconds();

        countSeries.addToValue(seconds, 1);
        timeSeries.addToValue(seconds, nanos);

        count++;
        totalNanos += nanos;
        maxNanos = Math.max(maxNanos, nanos);
        bucketCounts[bucketIndex(nanos)]++;

        String thread = Json.readString(record.jsonFields(), EVENT_THREAD_FIELD);
        byThread.computeIfAbsent(thread == null ? UNKNOWN_THREAD : thread, key -> new ThreadAcc()).add(nanos);

        String reason = Json.readString(record.jsonFields(), PINNED_REASON_FIELD);
        byReason.computeIfAbsent(reason == null || reason.isBlank() ? UNKNOWN_REASON : reason,
                key -> new ThreadAcc()).add(nanos);
    }

    private static int bucketIndex(long nanos) {
        for (int i = 0; i < BUCKET_BOUNDS_NANOS.length; i++) {
            if (nanos < BUCKET_BOUNDS_NANOS[i]) {
                return i;
            }
        }
        return BUCKET_LABELS.length - 1;
    }

    @Override
    public Result build() {
        SingleSerie countSerie = TimeseriesUtils.buildSerie(COUNT_SERIES, countSeries);
        SingleSerie timeSerie = TimeseriesUtils.buildSerie(TIME_SERIES, timeSeries);

        List<DurationBucket> distribution = new ArrayList<>(BUCKET_LABELS.length);
        for (int i = 0; i < BUCKET_LABELS.length; i++) {
            distribution.add(new DurationBucket(BUCKET_LABELS[i], bucketCounts[i]));
        }

        List<PinnedThreadStat> topThreads = byThread.entrySet().stream()
                .map(entry -> new PinnedThreadStat(
                        entry.getKey(), entry.getValue().count, entry.getValue().total, entry.getValue().max))
                .sorted(Comparator.comparingLong(PinnedThreadStat::totalNanos).reversed())
                .limit(maxThreads)
                .toList();

        List<PinningReasonStat> reasons = byReason.entrySet().stream()
                .map(entry -> new PinningReasonStat(
                        entry.getKey(), entry.getValue().count, entry.getValue().total, entry.getValue().max))
                .sorted(Comparator.comparingLong(PinningReasonStat::totalNanos).reversed())
                .toList();

        return new Result(count, totalNanos, maxNanos, new TimeseriesData(countSerie, timeSerie),
                distribution, topThreads, reasons);
    }
}
