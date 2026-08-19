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

import cafe.jeffrey.profile.manager.model.trace.TraceAttributeKeyRow;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeLatency;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeSearchResult;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeTimelineBucket;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeValues;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanTypeRow;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyId;
import cafe.jeffrey.provider.profile.api.TraceAttributeLatencyQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyRecord;
import cafe.jeffrey.provider.profile.api.TraceAttributeRepository;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueQuery;
import cafe.jeffrey.provider.profile.api.TraceSpanTypeRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapes the attribute index for the attribute views.
 * <p>
 * Three things happen here rather than in SQL or in the browser. Ids become hex strings, because a
 * 64-bit id does not survive JSON as a number. The per-trace hits come back flat from the database
 * and are grouped onto the trace they belong to, capped, so one busy trace cannot bury the page.
 * And the cardinality guard is applied — the one policy in this feature that has to hold across
 * every screen, and so belongs in the one place all of them go through.
 */
public class TraceAttributesManagerImpl implements TraceAttributesManager {

    /**
     * How many matched spans one trace shows.
     * <p>
     * A trace where four hundred spans carry the value is answered by the first few; the rest are
     * the same fact repeated, and they would push the trace itself off the row.
     */
    private static final int MAX_HITS_PER_TRACE = 6;

    private final TraceAttributeRepository repository;

    public TraceAttributesManagerImpl(TraceAttributeRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<TraceAttributeKeyRow> keys() {
        return repository.keys().stream()
                .map(TraceAttributesManagerImpl::toRow)
                .toList();
    }

    @Override
    public List<TraceSpanTypeRow> spanEventTypes() {
        return repository.spanEventTypes(SEARCH_ONLY_ABOVE).stream()
                .map(TraceAttributesManagerImpl::toRow)
                .toList();
    }

    @Override
    public List<TraceAttributeKeyRow> keysOf(String eventType) {
        return repository.keysOf(eventType).stream()
                .map(TraceAttributesManagerImpl::toRow)
                .toList();
    }

    @Override
    public TraceAttributeSearchResult search(TraceAttributeSearchQuery query) {
        TraceAttributeRepository.SearchPage page = repository.search(query);

        Map<Long, List<TraceAttributeSearchResult.Hit>> hits = groupHits(page.hits());

        List<TraceAttributeSearchResult.Match> matches = page.traces().stream()
                .map(trace -> new TraceAttributeSearchResult.Match(
                        toRow(trace),
                        hits.getOrDefault(trace.traceId(), List.of())))
                .toList();

        TraceAttributeRepository.Stats stats = page.stats();
        return new TraceAttributeSearchResult(
                matches,
                page.total(),
                new TraceAttributeSearchResult.Stats(
                        stats.traces(),
                        stats.tracesWithErrors(),
                        stats.totalNanos(),
                        stats.p50Nanos(),
                        stats.p95Nanos(),
                        stats.maxNanos()));
    }

    /**
     * The flat hits, gathered onto the trace they belong to and capped per trace.
     * <p>
     * Capped here rather than in SQL because the cap is per trace rather than per result: a
     * {@code LIMIT} would spend the whole budget on the first trace of the page and leave the rest
     * of the rows with nothing to show.
     */
    private static Map<Long, List<TraceAttributeSearchResult.Hit>> groupHits(
            List<TraceAttributeRepository.Hit> hits) {

        Map<Long, List<TraceAttributeSearchResult.Hit>> grouped = new LinkedHashMap<>();
        for (TraceAttributeRepository.Hit hit : hits) {
            List<TraceAttributeSearchResult.Hit> traceHits =
                    grouped.computeIfAbsent(hit.traceId(), _ -> new ArrayList<>());
            if (traceHits.size() < MAX_HITS_PER_TRACE) {
                traceHits.add(new TraceAttributeSearchResult.Hit(
                        TraceIds.hex(hit.spanId()), hit.key(), hit.value()));
            }
        }
        return grouped;
    }

    @Override
    public List<TraceAttributeTimelineBucket> timeline(TraceAttributeSearchQuery query, int buckets) {
        return repository.timeline(query, buckets).stream()
                .map(bucket -> new TraceAttributeTimelineBucket(
                        bucket.fromMillisFromBeginning(), bucket.matched(), bucket.total()))
                .toList();
    }

    @Override
    public TraceAttributeValues values(TraceAttributeValueQuery query) {
        int limit = Math.min(query.limit(), MAX_VALUES);
        TraceAttributeRepository.Values values = repository.values(
                new TraceAttributeValueQuery(
                        query.key(), query.sort(), query.descending(), limit, query.eventType()));

        List<TraceAttributeValues.Row> rows = values.values().stream()
                .map(value -> new TraceAttributeValues.Row(
                        value.value(),
                        value.traceCount(),
                        value.totalNanos(),
                        value.p50Nanos(),
                        value.p95Nanos(),
                        value.maxNanos(),
                        value.errorTraces()))
                .toList();

        // From the catalog rather than counted here: the returned rows only say how many values fit
        // in the page, and a list truncated at the cap has to say what it was truncated from. Read
        // under the same scope as the rows — a scoped breakdown whose cardinality came from the
        // profile-wide catalog would say "12 values in all" above a list that is all of the four the
        // event type has, and call a complete list truncated.
        long distinctValues = distinctValuesOf(query.key(), query.eventType());

        return new TraceAttributeValues(
                rows, values.tracesWithoutKey(), distinctValues, distinctValues > rows.size());
    }

    @Override
    public TraceAttributeLatency latency(TraceAttributeLatencyQuery query) {
        List<TraceAttributeLatency.Cell> cells = repository.latency(query).stream()
                .map(cell -> new TraceAttributeLatency.Cell(
                        cell.value(), cell.bucket(), cell.traceCount()))
                .toList();

        // The grid's bounds come from the cells rather than from a constant shared with the
        // repository: the caller draws the axis it was given, so the two cannot drift apart.
        int min = cells.stream().mapToInt(TraceAttributeLatency.Cell::bucket).min().orElse(0);
        int max = cells.stream().mapToInt(TraceAttributeLatency.Cell::bucket).max().orElse(0);

        return new TraceAttributeLatency(cells, min, max);
    }

    /**
     * The key's true cardinality, read from whichever catalog matches the breakdown's scope.
     * <p>
     * A scan of one small table — either catalog holds a row per key, not per value — which is why
     * this is a second read rather than a column smuggled onto every value row.
     *
     * @param eventType the event type the values were read under, or null for the whole profile
     */
    private long distinctValuesOf(TraceAttributeKeyId key, String eventType) {
        List<TraceAttributeKeyRecord> catalog =
                eventType == null ? repository.keys() : repository.keysOf(eventType);

        return catalog.stream()
                .filter(candidate -> candidate.id().equals(key))
                .mapToLong(TraceAttributeKeyRecord::distinctValues)
                .findFirst()
                .orElse(0L);
    }

    private static TraceSpanTypeRow toRow(TraceSpanTypeRecord type) {
        return new TraceSpanTypeRow(
                type.eventType(),
                type.spanCount(),
                type.traceCount(),
                type.errorSpans(),
                type.attributeCount(),
                type.breakableCount());
    }

    private static TraceAttributeKeyRow toRow(TraceAttributeKeyRecord key) {
        return new TraceAttributeKeyRow(
                key.id().source().name(),
                key.id().owner(),
                key.id().key(),
                key.valueKind().name(),
                key.distinctValues(),
                key.spanCount(),
                key.traceCount(),
                key.distinctValues() > SEARCH_ONLY_ABOVE);
    }

    private static TraceRow toRow(TraceSummaryRecord trace) {
        return new TraceRow(
                TraceIds.hex(trace.traceId()),
                trace.rootName(),
                trace.rootKind(),
                trace.rootEventType(),
                trace.startMillisFromBeginning(),
                trace.startEpochMillis(),
                trace.durationNanos(),
                trace.spanCount(),
                trace.errorCount(),
                trace.hasPlatformSpan());
    }
}
