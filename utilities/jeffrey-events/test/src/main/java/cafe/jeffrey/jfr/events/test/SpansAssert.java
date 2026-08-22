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

package cafe.jeffrey.jfr.events.test;

import jdk.jfr.consumer.RecordedEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assertions over the spans in a recording — the executable form of the checklist instrumentation
 * is otherwise verified against by eye with {@code jfr print}.
 * <p>
 * Every failure names what was actually recorded, because the useful question when instrumentation
 * is wrong is never "did it fail" but "what did it emit instead".
 *
 * <pre>{@code
 * SpansAssert.assertThat(events)
 *         .hasNoUntracedSpans()
 *         .hasNoOrphanedSpans()
 *         .hasSpan("GET /api/users/{id}").isRoot()
 *         .and()
 *         .hasSpan("UserMapper.selectById").nestedUnder("GET /api/users/{id}");
 * }</pre>
 *
 * Throws plain {@link AssertionError}, so the toolkit stays dependency-free and works under JUnit,
 * TestNG or none of them.
 */
public final class SpansAssert {

    private final List<RecordedSpan> spans;

    private SpansAssert(List<RecordedSpan> spans) {
        this.spans = spans;
    }

    /**
     * Opens assertions over the spans among {@code events}; non-span events are ignored.
     */
    public static SpansAssert assertThat(List<RecordedEvent> events) {
        Objects.requireNonNull(events, "events must not be null");
        return new SpansAssert(RecordedSpan.from(events));
    }

    /**
     * Opens assertions over spans already parsed.
     */
    public static SpansAssert assertThatSpans(List<RecordedSpan> spans) {
        Objects.requireNonNull(spans, "spans must not be null");
        return new SpansAssert(List.copyOf(spans));
    }

    /**
     * Selects the one span with this operation name and continues the assertion on it.
     *
     * @throws AssertionError when no span, or more than one, carries the name
     */
    public SpanAssert hasSpan(String name) {
        Objects.requireNonNull(name, "name must not be null");

        List<RecordedSpan> matching = spans.stream()
                .filter(span -> name.equals(span.name()))
                .toList();

        if (matching.isEmpty()) {
            throw new AssertionError("no span named '" + name + "' was recorded; recorded spans: " + describeAll());
        }
        if (matching.size() > 1) {
            throw new AssertionError(
                    "expected exactly one span named '" + name + "', but " + matching.size() + " were recorded");
        }
        return new SpanAssert(matching.getFirst(), this);
    }

    /**
     * Asserts that no span carries this operation name — the shape of a test pinning that some work
     * is deliberately <em>not</em> instrumented.
     */
    public SpansAssert hasNoSpan(String name) {
        Objects.requireNonNull(name, "name must not be null");
        boolean present = spans.stream().anyMatch(span -> name.equals(span.name()));
        if (present) {
            throw new AssertionError("expected no span named '" + name + "', but one was recorded");
        }
        return this;
    }

    public SpansAssert hasSpanCount(int expected) {
        if (spans.size() != expected) {
            throw new AssertionError(
                    "expected " + expected + " spans but recorded " + spans.size() + ": " + describeAll());
        }
        return this;
    }

    /**
     * Asserts every span carries trace identity.
     * <p>
     * A span with ids of {@code 0} is recorded and still feeds its dashboard, but is part of no
     * trace — the signature of a bare {@code commit()} where {@code commitSpan()} belonged, or of
     * work that crossed an executor without {@code Tracer.fork}/{@code continueIn}.
     */
    public SpansAssert hasNoUntracedSpans() {
        List<RecordedSpan> untraced = spans.stream()
                .filter(span -> !span.isTraced())
                .toList();

        if (!untraced.isEmpty()) {
            throw new AssertionError("these spans carry no trace identity (ids are 0), so they are in no trace: "
                    + describe(untraced)
                    + " - committed with commit() instead of commitSpan(), or emitted across an executor boundary");
        }
        return this;
    }

    /**
     * Asserts that every span's parent is present in the recording.
     * <p>
     * An orphan is promoted to a root by Jeffrey, so nothing is lost but the nesting is — usually a
     * parent that fell under a duration threshold, or a middle layer nobody instrumented.
     */
    public SpansAssert hasNoOrphanedSpans() {
        Set<Long> present = spans.stream()
                .map(RecordedSpan::spanId)
                .collect(Collectors.toSet());

        List<RecordedSpan> orphans = spans.stream()
                .filter(span -> !span.isRoot())
                .filter(span -> !present.contains(span.parentSpanId()))
                .toList();

        if (!orphans.isEmpty()) {
            throw new AssertionError("these spans name a parent that is not in the recording, so they will be "
                    + "promoted to roots and lose their nesting: " + describe(orphans));
        }
        return this;
    }

    /**
     * Asserts the recording holds no more than {@code max} distinct span names.
     * <p>
     * Guards the low-cardinality contract: every distinct name enters the JFR per-chunk constant
     * pool, so a name built from a request id or an entity id inflates the recording and produces
     * one "operation" per instance in Jeffrey. A test that exercises N endpoints should see N
     * names, not N times the number of requests.
     */
    public SpansAssert hasSpanNameCardinalityAtMost(int max) {
        Map<String, Long> names = spanNameCardinality();
        if (names.size() > max) {
            throw new AssertionError("expected at most " + max + " distinct span names but recorded "
                    + names.size() + ": " + names
                    + " - a name built from a request or entity id is the usual cause");
        }
        return this;
    }

    /**
     * @return how many spans carry each operation name, most frequent first
     */
    public Map<String, Long> spanNameCardinality() {
        Map<String, Long> counts = spans.stream()
                .collect(Collectors.groupingBy(
                        span -> String.valueOf(span.name()), Collectors.counting()));

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    /**
     * @return the spans this assertion covers, for a check the fluent API does not express
     */
    public List<RecordedSpan> spans() {
        return spans;
    }

    List<RecordedSpan> spansWithId(long spanId) {
        return spans.stream()
                .filter(span -> span.spanId() == spanId)
                .toList();
    }

    String describeAll() {
        return describe(spans);
    }

    private static String describe(List<RecordedSpan> spans) {
        return spans.stream()
                .map(span -> span.name() + " (" + span.eventType() + ", traceId=" + span.traceId()
                        + ", spanId=" + span.spanId() + ", parentSpanId=" + span.parentSpanId() + ")")
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
