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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.span.SpanDetailRow;
import cafe.jeffrey.profile.manager.model.span.SpanEventRow;
import cafe.jeffrey.profile.manager.model.span.SpanHeatmap;
import cafe.jeffrey.profile.manager.model.span.SpanHeatmapCell;
import cafe.jeffrey.profile.manager.model.span.SpanHeatmapRow;
import cafe.jeffrey.profile.manager.model.span.SpanOverview;
import cafe.jeffrey.profile.manager.model.span.SpanSlowestRow;
import cafe.jeffrey.profile.manager.model.span.SpanTagStat;
import cafe.jeffrey.provider.profile.api.SpanRecord;
import cafe.jeffrey.provider.profile.api.SpanRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Derives the span views from the single {@link SpanRepository#listSpans()} primitive:
 * by-tag aggregation and a tag-by-time heatmap.
 */
public class SpanManagerImpl implements SpanManager {

    private static final double P95 = 0.95;
    private static final double P99 = 0.99;
    private static final int HEATMAP_BUCKET_COUNT = 60;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final String NO_TAG = "";

    private final SpanRepository repository;

    public SpanManagerImpl(SpanRepository repository) {
        this.repository = repository;
    }

    @Override
    public SpanOverview overview() {
        List<SpanRecord> spans = repository.listSpans();
        long[] durations = spans.stream()
                .mapToLong(SpanRecord::durationNanos)
                .sorted()
                .toArray();

        long count = durations.length;
        long total = 0;
        for (long duration : durations) {
            total += duration;
        }
        long avg = count == 0 ? 0 : total / count;
        long max = count == 0 ? 0 : durations[durations.length - 1];
        int distinctTags = (int) spans.stream()
                .map(span -> tagOrEmpty(span.tag()))
                .distinct()
                .count();

        return new SpanOverview(
                count, total, avg, percentile(durations, P95), percentile(durations, P99), max, distinctTags);
    }

    @Override
    public List<SpanTagStat> tagStatistics() {
        Map<String, List<SpanRecord>> byTag = repository.listSpans().stream()
                .collect(Collectors.groupingBy(span -> tagOrEmpty(span.tag())));

        List<SpanTagStat> stats = new ArrayList<>();
        for (Map.Entry<String, List<SpanRecord>> entry : byTag.entrySet()) {
            long[] durations = entry.getValue().stream()
                    .mapToLong(SpanRecord::durationNanos)
                    .sorted()
                    .toArray();

            long count = durations.length;
            long total = 0;
            for (long duration : durations) {
                total += duration;
            }
            long max = durations.length == 0 ? 0 : durations[durations.length - 1];
            long avg = count == 0 ? 0 : total / count;
            long p95 = percentile(durations, P95);
            long p99 = percentile(durations, P99);

            stats.add(new SpanTagStat(entry.getKey(), count, total, avg, p95, p99, max));
        }

        stats.sort(Comparator.comparingLong(SpanTagStat::totalNanos).reversed()
                .thenComparing(SpanTagStat::tag));
        return stats;
    }

    @Override
    public SpanHeatmap heatmap() {
        List<SpanRecord> spans = repository.listSpans();

        long maxEndMillis = 0;
        for (SpanRecord span : spans) {
            long end = span.startMillisFromBeginning() + span.durationNanos() / NANOS_PER_MILLI;
            if (end > maxEndMillis) {
                maxEndMillis = end;
            }
        }
        long bucketMillis = Math.max(1L, (long) Math.ceil((double) maxEndMillis / HEATMAP_BUCKET_COUNT));

        Map<String, List<SpanRecord>> byTag = new TreeMap<>();
        for (SpanRecord span : spans) {
            byTag.computeIfAbsent(tagOrEmpty(span.tag()), key -> new ArrayList<>()).add(span);
        }

        List<SpanHeatmapRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<SpanRecord>> entry : byTag.entrySet()) {
            Map<Integer, List<Long>> durationsByBucket = new HashMap<>();
            for (SpanRecord span : entry.getValue()) {
                int bucket = (int) Math.min(
                        HEATMAP_BUCKET_COUNT - 1L, span.startMillisFromBeginning() / bucketMillis);
                durationsByBucket.computeIfAbsent(bucket, key -> new ArrayList<>()).add(span.durationNanos());
            }

            List<SpanHeatmapCell> cells = new ArrayList<>();
            for (Map.Entry<Integer, List<Long>> bucketEntry : durationsByBucket.entrySet()) {
                long[] durations = bucketEntry.getValue().stream()
                        .mapToLong(Long::longValue)
                        .sorted()
                        .toArray();
                cells.add(new SpanHeatmapCell(bucketEntry.getKey(), durations.length, percentile(durations, P95)));
            }
            cells.sort(Comparator.comparingInt(SpanHeatmapCell::bucket));
            rows.add(new SpanHeatmapRow(entry.getKey(), cells));
        }

        return new SpanHeatmap(HEATMAP_BUCKET_COUNT, bucketMillis, rows);
    }

    @Override
    public List<SpanDetailRow> tagSpans(String tag) {
        String wanted = tagOrEmpty(tag);
        return repository.listSpans().stream()
                .filter(span -> tagOrEmpty(span.tag()).equals(wanted))
                .sorted(Comparator.comparingLong(SpanRecord::startEpochMillis))
                .map(span -> new SpanDetailRow(
                        span.startEpochMillis(), span.durationNanos(), span.osThreadId(), span.threadName()))
                .toList();
    }

    @Override
    public List<SpanSlowestRow> slowestSpans(int limit) {
        return repository.listSpans().stream()
                .sorted(Comparator.comparingLong(SpanRecord::durationNanos).reversed())
                .limit(limit)
                .map(span -> new SpanSlowestRow(
                        span.startEpochMillis(), span.durationNanos(),
                        span.osThreadId(), span.threadName(), tagOrEmpty(span.tag())))
                .toList();
    }

    @Override
    public List<SpanEventRow> spanEvents(long osThreadId, long fromEpochMillis, long toEpochMillis) {
        return repository.eventsForThread(osThreadId, fromEpochMillis, toEpochMillis).stream()
                .map(event -> new SpanEventRow(
                        event.eventType(), event.startEpochMillis(), event.durationNanos(), event.fields()))
                .toList();
    }

    private static long percentile(long[] sortedAsc, double quantile) {
        if (sortedAsc.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(quantile * sortedAsc.length) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sortedAsc.length) {
            index = sortedAsc.length - 1;
        }
        return sortedAsc[index];
    }

    private static String tagOrEmpty(String tag) {
        return tag == null ? NO_TAG : tag;
    }
}
