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

import java.util.List;
import java.util.Objects;

/**
 * Assertions about one span: where it sits in its trace, and what it says about the operation.
 *
 * @see SpansAssert#hasSpan(String)
 */
public final class SpanAssert {

    private static final String ROOT_STATUS_UNSET = "UNSET";

    private final RecordedSpan span;
    private final SpansAssert recording;

    SpanAssert(RecordedSpan span, SpansAssert recording) {
        this.span = span;
        this.recording = recording;
    }

    /**
     * Asserts the span starts its trace — what an inbound request's event should be.
     */
    public SpanAssert isRoot() {
        if (!span.isRoot()) {
            throw new AssertionError("expected '" + span.name() + "' to be a trace root, but it names parent "
                    + span.parentSpanId() + describeParent());
        }
        return this;
    }

    /**
     * Asserts the span hangs directly off the span with this name — one level, not merely somewhere
     * in the same trace.
     */
    public SpanAssert nestedUnder(String parentName) {
        Objects.requireNonNull(parentName, "parentName must not be null");

        if (span.isRoot()) {
            throw new AssertionError("expected '" + span.name() + "' to nest under '" + parentName
                    + "', but it is a trace root - the work it describes ran outside that span, or it was "
                    + "committed after the enclosing binding was gone");
        }

        List<RecordedSpan> parents = recording.spansWithId(span.parentSpanId());
        if (parents.isEmpty()) {
            throw new AssertionError("expected '" + span.name() + "' to nest under '" + parentName
                    + "', but its parent (spanId=" + span.parentSpanId() + ") is not in the recording");
        }

        RecordedSpan parent = parents.getFirst();
        if (!parentName.equals(parent.name())) {
            throw new AssertionError("expected '" + span.name() + "' to nest under '" + parentName
                    + "', but its parent is '" + parent.name() + "'");
        }
        if (parent.traceId() != span.traceId()) {
            throw new AssertionError("'" + span.name() + "' and its parent '" + parent.name()
                    + "' are in different traces (" + span.traceId() + " vs " + parent.traceId() + ")");
        }
        return this;
    }

    /**
     * Asserts the span shares a trace with the named span, at any depth — the weaker check for work
     * whose exact nesting is not the point.
     */
    public SpanAssert inSameTraceAs(String otherName) {
        Objects.requireNonNull(otherName, "otherName must not be null");

        RecordedSpan other = recording.spans().stream()
                .filter(candidate -> otherName.equals(candidate.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no span named '" + otherName + "' was recorded; recorded spans: " + recording.describeAll()));

        if (other.traceId() != span.traceId()) {
            throw new AssertionError("expected '" + span.name() + "' and '" + otherName
                    + "' to share a trace, but their trace ids are " + span.traceId() + " and " + other.traceId());
        }
        return this;
    }

    /**
     * Asserts the span carries trace identity at all.
     */
    public SpanAssert isTraced() {
        if (!span.isTraced()) {
            throw new AssertionError("'" + span.name() + "' carries no trace identity (traceId=" + span.traceId()
                    + ", spanId=" + span.spanId() + "), so it is part of no trace - committed with commit() "
                    + "instead of commitSpan()?");
        }
        return this;
    }

    /**
     * Asserts what role the span plays: {@code INTERNAL}, {@code SERVER} or {@code CLIENT}.
     */
    public SpanAssert hasKind(String kind) {
        if (!Objects.equals(kind, span.kind())) {
            throw new AssertionError(
                    "expected '" + span.name() + "' to have kind " + kind + " but was " + span.kind());
        }
        return this;
    }

    /**
     * Asserts how the span finished: {@code UNSET}, {@code OK} or {@code ERROR}.
     */
    public SpanAssert hasStatus(String status) {
        if (!Objects.equals(status, span.status())) {
            throw new AssertionError(
                    "expected '" + span.name() + "' to have status " + status + " but was " + span.status());
        }
        return this;
    }

    /**
     * Asserts the span failed with this exception type — the check that an error path is actually
     * visible in the trace rather than silently swallowed.
     */
    public SpanAssert hasErrorType(Class<? extends Throwable> errorType) {
        Objects.requireNonNull(errorType, "errorType must not be null");
        if (!errorType.getName().equals(span.errorType())) {
            throw new AssertionError("expected '" + span.name() + "' to have failed with " + errorType.getName()
                    + " but its error type was " + span.errorType());
        }
        return this;
    }

    /**
     * Asserts the span recorded no failure.
     */
    public SpanAssert hasNoError() {
        if (span.errorType() != null || !ROOT_STATUS_UNSET.equals(span.status())) {
            throw new AssertionError("expected '" + span.name() + "' to have recorded no failure, but status was "
                    + span.status() + " and error type " + span.errorType());
        }
        return this;
    }

    /**
     * Asserts which event type carried the span, e.g. {@code jeffrey.JdbcQuery}.
     */
    public SpanAssert hasEventType(String eventType) {
        if (!Objects.equals(eventType, span.eventType())) {
            throw new AssertionError("expected '" + span.name() + "' to be carried by " + eventType
                    + " but it was " + span.eventType());
        }
        return this;
    }

    /**
     * @return the span itself, for a check the fluent API does not express
     */
    public RecordedSpan span() {
        return span;
    }

    /**
     * Returns to the recording, to continue with another span.
     */
    public SpansAssert and() {
        return recording;
    }

    private String describeParent() {
        List<RecordedSpan> parents = recording.spansWithId(span.parentSpanId());
        if (parents.isEmpty()) {
            return "";
        }
        return " ('" + parents.getFirst().name() + "')";
    }
}
