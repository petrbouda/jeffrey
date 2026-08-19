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

package cafe.jeffrey.provider.profile.api;

import java.util.List;

/**
 * Reads the key/value payload of a profile's spans as queryable dimensions.
 * <p>
 * A span carries two of them — the open {@code attributes} map a developer attached, and the fields
 * the event type declares about itself — plus the shape columns every span already has.
 * {@link #derive()} flattens all three into {@code trace_span_attributes} once, after which
 * filtering, faceting and ranking are ordinary SQL over indexed columns rather than JSON extraction
 * repeated per row per query.
 * <p>
 * Nothing here knows about any particular event type, which is the point: an event type instrumented
 * tomorrow, with a {@code @Span} template Jeffrey has never seen, has its declared fields in the
 * catalog the first time its recording is opened, with no change on this side.
 */
public interface TraceAttributeRepository {

    /**
     * Flattens every span's attributes, declared fields and shape columns into the attribute index,
     * and summarises them into the catalog.
     * <p>
     * Runs once per profile, after {@link TraceRepository#derive()} — it reads {@code trace_spans},
     * so there is nothing to flatten until that has run. Safe on a profile with no traced events:
     * both tables simply stay empty.
     */
    void derive();

    /**
     * Every key the profile recorded, with its cardinality and coverage. This is the whole key list:
     * it is a scan of the catalog, not of the rows, so it stays cheap however wide the profile is.
     */
    List<TraceAttributeKeyRecord> keys();

    /**
     * The event types that produced spans, most spans first — the picker's first step.
     *
     * @param searchOnlyAbove the cardinality past which a key cannot be broken down. Passed in rather
     *                        than known here, because it is the caller's policy; it decides only how
     *                        the returned counts are split, never which types come back
     */
    List<TraceSpanTypeRecord> spanEventTypes(long searchOnlyAbove);

    /**
     * The keys spans of one event type carried, with that type's own counts.
     * <p>
     * Not a filter over {@link #keys()}: a key's counts here are scoped to the event type, because
     * {@code tenant} on HTTP spans is a different number of values from {@code tenant} across the
     * profile, and a picker showing the profile-wide figure under an event type would state a number
     * the page it opens cannot reproduce.
     *
     * @param eventType the event type to list; an unknown one yields no keys rather than an error,
     *                  since it can only reach here from a stale link
     */
    List<TraceAttributeKeyRecord> keysOf(String eventType);

    /**
     * The traces matching the query, with the spans that matched.
     * <p>
     * The hits are what keeps the answer in the list: without them a reader has to open a trace to
     * find out which span carried the value they searched for, and does it once per trace.
     */
    SearchPage search(TraceAttributeSearchQuery query);

    /**
     * How the matched traces are spread over the recording, against every trace as a backdrop.
     * <p>
     * Both counts come from one query, bucketed over the whole recording's bounds rather than the
     * matches' own, so the matched strip is comparable with the profile behind it instead of being
     * stretched to fill the same width.
     *
     * @param query   what to match; an unfiltered query makes matched and total agree everywhere
     * @param buckets how many slices to divide the recording into; at least 1
     */
    List<TimelineBucket> timeline(TraceAttributeSearchQuery query, int buckets);

    /**
     * One key's values, ranked, with the traces carrying each summarised.
     *
     * @return the ranked values, plus how many traces carry the key nowhere at all — stated rather
     *         than left out, because a key missing from a fifth of the profile is a fact about the
     *         instrumentation, and hiding it makes every share on the screen a lie
     */
    Values values(TraceAttributeValueQuery query);

    /**
     * The duration distribution of each of a key's values — the latency heatmap's cells.
     * <p>
     * Cells that hold nothing are left out rather than returned as zeroes; the caller draws a fixed
     * grid and fills what it was given.
     *
     */
    List<TraceAttributeLatencyRecord> latency(TraceAttributeLatencyQuery query);

    /**
     * A page of matched traces, the spans that matched them, and what the whole match set looks like.
     *
     * @param traces the page's traces, ordered as the query asked
     * @param hits   which spans matched, for the traces on this page only — resolving them for every
     *               match would be unbounded work for rows nobody is looking at
     * @param total  how many traces matched in total, so a capped page can say it is one
     * @param stats  the whole match set summarised, which the page cannot be summed into
     */
    record SearchPage(
            List<TraceSummaryRecord> traces,
            List<Hit> hits,
            long total,
            Stats stats) {

        public static final SearchPage EMPTY =
                new SearchPage(List.of(), List.of(), 0, Stats.EMPTY);
    }

    /**
     * One span that satisfied one condition, and what it held.
     *
     * @param traceId the trace the span belongs to
     * @param spanId  the span that matched
     * @param key     the key that matched
     * @param value   what that span recorded for it
     */
    record Hit(long traceId, long spanId, String key, String value) {
    }

    /**
     * The matched traces summarised. Aggregated in SQL rather than over the returned page, which is
     * capped: summing a page would report the slowest hundred as the whole match.
     */
    record Stats(
            long traces,
            long tracesWithErrors,
            long totalNanos,
            long p50Nanos,
            long p95Nanos,
            long maxNanos) {

        public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0);
    }

    /**
     * One slice of the recording: how many traces matched, and how many there were.
     *
     * @param fromMillisFromBeginning the slice's start, relative to the recording
     * @param matched                 traces in it that the query matched
     * @param total                   traces in it altogether
     */
    record TimelineBucket(long fromMillisFromBeginning, long matched, long total) {
    }

    /**
     * A key's values, ranked.
     *
     * @param values           the ranked values, at most as many as the query asked for
     * @param tracesWithoutKey traces carrying the key on no span at all
     */
    record Values(List<TraceAttributeValueRecord> values, long tracesWithoutKey) {

        public static final Values EMPTY = new Values(List.of(), 0);
    }
}
