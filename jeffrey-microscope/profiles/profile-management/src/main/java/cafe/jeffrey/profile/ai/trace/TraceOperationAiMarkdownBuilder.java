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

package cafe.jeffrey.profile.ai.trace;

import cafe.jeffrey.profile.manager.model.trace.TraceNotificationGroupRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSpanRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSummary;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationThreads;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;

import java.util.Comparator;
import java.util.List;

/**
 * Renders one operation — every trace of a given type — as Markdown for a model to read.
 * <p>
 * A sibling of {@link TraceAiMarkdownBuilder} rather than a mode of it: the two documents answer
 * different questions and warn about different things. A single trace is one sample and the reader
 * must be told not to generalise from it; an operation is a population, where the reader must
 * instead be told that its span totals are two different accountings that cannot be mixed.
 */
public final class TraceOperationAiMarkdownBuilder {

    private static final String AI_PREAMBLE = """
            # How to read this operation

            This document describes **one operation** — every trace of a single type in a
            recording, aggregated — exported by Jeffrey (a JVM performance analyst) for
            AI-assisted interpretation. Unlike a single-trace export, this is a
            **population**: it says what is typical, not what happened once.

            ## Sections

            1. **Header (YAML-ish)** — the operation's identity, how often it ran, and its
               latency percentiles.
            2. **Span breakdown** — where the operation's time goes, by span name.
            3. **Notifications** — what the application itself reported while traces of
               this operation ran, grouped by kind.
            4. **Threads** — what kind of threads carried it.
            5. **Slowest traces** — individual exemplars worth drilling into.

            ## The two time columns — do not mix them

            Each span name is reported with **two** totals, and they answer different
            questions:

            - **`inclusive`** is the span's whole duration, containing everything it called.
              Because parents contain their children, inclusive figures **sum to more than
              the operation's own total time** — the same instant is counted once per level
              of the tree. Ranking by inclusive tells you *which part of the request tree*
              the time is inside.
            - **`self`** is the time spans of that name spent on their **own** work, with
              their children's time removed. Self figures **do sum to** the operation's
              time, so they can legitimately be compared against each other. Ranking by self
              tells you *which code to go and look at*.

            These two rankings routinely disagree, and the disagreement is informative: a
            span that merely wraps three slow queries tops the inclusive ranking and barely
            registers on the self one. If you rank by the wrong column you will recommend
            optimising a method that does nothing but call other methods.

            `self` is computed by merging child intervals, not by subtracting child
            durations: concurrent children are subtracted once, and children that ran on a
            different thread are not subtracted at all.

            ## Percentiles

            `p50`/`p95`/`p99` are computed across every trace of this operation, so they all
            describe the same population and can be compared with each other. A wide gap
            between `p50` and `p99` means the operation is *sometimes* slow — which is a
            different problem from one that is uniformly slow, and usually a more
            interesting one. `max` is a single observation and should not be reasoned about
            as a typical figure.

            ## Notifications — what the application said about itself

            A notification is the application's own report of something going wrong while a
            request ran — a pool with no idle connections, a fallback taken — emitted by its
            own code as a `jeffrey.Notification` event. Here they are aggregated across every
            trace of the operation, one line per kind:

                - <TYPE> ×<count> [<SEVERITY>] in <traces> of <total> traces — <category> from <source>

            followed by the message every occurrence carries and a few trace ids that raised
            it, the slowest first, as candidates for a single-trace export. `CRITICAL` and
            `HIGH` are findings in their own right; `MEDIUM` and `LOW` are context. A kind
            raised in most traces of the operation is a property of the operation; one raised
            in a few is a property of those requests, and the exemplars are where to look.

            ## What this document cannot tell you

            It has no per-trace JVM context: the pauses, lock waits and I/O that explain
            *why* a particular trace was slow live in the single-trace export instead. Use
            the slowest traces listed at the end to pick one worth exporting on its own.

            """;

    private static final String HEADING_ANALYSIS = "## How to analyze this operation";
    private static final String HEADING_SPANS = "## Span breakdown";
    private static final String HEADING_NOTIFICATIONS = "## Notifications";
    private static final String HEADING_THREADS = "## Threads";
    private static final String HEADING_SLOWEST = "## Slowest traces";

    private static final String ANALYSIS_INSTRUCTION = """
            Start with the spread, not the average. If `p99` is far above `p50`, the useful
            question is what makes the slow tail slow, and the answer is usually visible only
            in individual traces — not in these aggregates.

            Then read the span breakdown twice: once ranked by `self` to find where the work
            actually is, and once by `inclusive` to see which part of the tree contains it.
            Where the two disagree, say so and explain which one supports your conclusion.

            Be careful about `count`. A span name that appears many times per trace may cost
            more in aggregate than a slower one that appears once, and an N+1 query pattern
            shows up here as a high occurrence count against a modest median.

            Read the notifications before concluding anything from the timing. A `CRITICAL`
            or `HIGH` kind raised across most traces of this operation is the operation's
            own diagnosis, and usually names the cause the span breakdown only shows the cost
            of; lead with it. Compare the trace count of a kind with the operation's trace
            count to tell a systematic problem from an occasional one.

            Finally, name the traces you would look at next and why, rather than concluding
            from aggregates alone what any single request did.
            """;

    private static final String BULLET_PREFIX = "- ";
    private static final String DASH_SEPARATOR = " — ";
    private static final String NO_SPANS_NOTE = "(no spans recorded for this operation)";
    private static final String NO_TRACES_NOTE = "(no traces recorded for this operation)";
    private static final String NO_NOTIFICATIONS_NOTE =
            "(the application raised no notifications inside traces of this operation)";
    private static final String INDENT_UNIT = "  ";

    /** How much of a notification message survives. Enough to identify it, not enough to be a page. */
    private static final int MAX_MESSAGE_CHARS = 200;

    private final TraceOperationRow operation;
    private final TraceOperationSummary summary;
    private final List<TraceNotificationGroupRow> notifications;
    private final List<TraceRow> slowestTraces;

    /**
     * @param notifications what the application said inside traces of this operation, grouped by
     *                      kind and led by severity, already capped by the caller
     */
    public TraceOperationAiMarkdownBuilder(
            TraceOperationRow operation,
            TraceOperationSummary summary,
            List<TraceNotificationGroupRow> notifications,
            List<TraceRow> slowestTraces) {

        this.operation = operation;
        this.summary = summary;
        this.notifications = notifications;
        this.slowestTraces = slowestTraces;
    }

    public String build() {
        StringBuilder out = new StringBuilder(8192);
        out.append(AI_PREAMBLE);
        renderHeader(out);
        out.append('\n');
        renderAnalysisInstruction(out);
        renderSpans(out);
        out.append('\n');
        renderNotifications(out);
        out.append('\n');
        renderThreads(out);
        out.append('\n');
        renderSlowestTraces(out);
        return out.toString();
    }

    private void renderHeader(StringBuilder out) {
        out.append("operation: ").append(operation.name()).append('\n');
        out.append("kind: ").append(operation.kind()).append('\n');
        out.append("event_type: ").append(operation.eventType()).append('\n');
        out.append("trace_count: ").append(operation.count()).append('\n');
        out.append("error_count: ").append(operation.errorCount()).append('\n');
        out.append("notification_count: ").append(operation.notificationCount()).append('\n');
        out.append("urgent_notification_count: ").append(operation.urgentNotificationCount()).append('\n');
        out.append("span_count: ").append(operation.spanCount()).append('\n');
        out.append("total_time: ").append(TraceAiFormat.duration(operation.totalNanos())).append('\n');
        out.append("p50: ").append(TraceAiFormat.duration(operation.p50Nanos())).append('\n');
        out.append("p95: ").append(TraceAiFormat.duration(operation.p95Nanos())).append('\n');
        out.append("p99: ").append(TraceAiFormat.duration(operation.p99Nanos())).append('\n');
        out.append("max: ").append(TraceAiFormat.duration(operation.maxNanos())).append('\n');
    }

    private void renderAnalysisInstruction(StringBuilder out) {
        out.append(HEADING_ANALYSIS).append('\n').append('\n');
        out.append(ANALYSIS_INSTRUCTION).append('\n');
    }

    /**
     * Ranked by self time, because that is the ranking whose rows can be compared with each other —
     * and the inclusive figure is carried on the same line so the disagreement between the two is
     * visible without re-sorting the document.
     */
    private void renderSpans(StringBuilder out) {
        out.append(HEADING_SPANS).append('\n').append('\n');

        List<TraceOperationSpanRow> spans = summary.spans();
        if (spans.isEmpty()) {
            out.append(NO_SPANS_NOTE).append('\n');
            return;
        }

        out.append("Ranked by self time. Each line reads:").append('\n').append('\n');
        out.append("    - <name> — self <total> (median <p50>), inclusive <total> (median <p50>), ")
                .append("<count> occurrences in <traces> traces").append('\n').append('\n');

        List<TraceOperationSpanRow> ranked = spans.stream()
                .sorted(Comparator.comparingLong(TraceOperationSpanRow::selfNanos).reversed())
                .toList();

        for (TraceOperationSpanRow span : ranked) {
            out.append(BULLET_PREFIX).append(span.name()).append(DASH_SEPARATOR);
            out.append("self ").append(TraceAiFormat.duration(span.selfNanos()));
            out.append(" (median ").append(TraceAiFormat.duration(span.p50SelfNanos())).append(')');
            out.append(", inclusive ").append(TraceAiFormat.duration(span.totalNanos()));
            out.append(" (median ").append(TraceAiFormat.duration(span.p50Nanos())).append(')');
            out.append(", ").append(TraceAiFormat.count(span.occurrences(), "occurrence"))
                    .append(" in ").append(TraceAiFormat.count(span.traceCount(), "trace"));
            out.append('\n');
        }
    }

    /**
     * What the application said, one line per kind, the most severe first. The trace count is
     * carried beside the operation's own so the reader can tell "every request" from "some".
     */
    private void renderNotifications(StringBuilder out) {
        out.append(HEADING_NOTIFICATIONS).append('\n').append('\n');

        if (notifications.isEmpty()) {
            out.append(NO_NOTIFICATIONS_NOTE).append('\n');
            return;
        }

        for (TraceNotificationGroupRow group : notifications) {
            out.append(BULLET_PREFIX)
                    .append(group.type())
                    .append(" ×").append(group.count())
                    .append(" [").append(group.severity()).append(']')
                    .append(" in ").append(group.traceCount())
                    .append(" of ").append(operation.count()).append(" traces");
            if (group.category() != null || group.source() != null) {
                out.append(DASH_SEPARATOR);
                if (group.category() != null) {
                    out.append(group.category());
                }
                if (group.source() != null) {
                    out.append(group.category() != null ? " from " : "from ").append(group.source());
                }
            }
            out.append('\n');

            if (group.message() != null && !group.message().isBlank()) {
                out.append(INDENT_UNIT).append("message: ").append(flatten(group.message())).append('\n');
            }
            if (!group.exemplarTraceIds().isEmpty()) {
                out.append(INDENT_UNIT).append("raised in traces: ")
                        .append(String.join(", ", group.exemplarTraceIds()))
                        .append(" (slowest first, for traces_traceExport)")
                        .append('\n');
            }
        }
    }

    /** A message reduced to one line and a readable length, so a bullet cannot be split by it. */
    private static String flatten(String message) {
        String single = message.replaceAll("\\s+", " ").strip();
        return single.length() <= MAX_MESSAGE_CHARS
                ? single
                : single.substring(0, MAX_MESSAGE_CHARS) + "… (truncated)";
    }

    private void renderThreads(StringBuilder out) {
        out.append(HEADING_THREADS).append('\n').append('\n');

        TraceOperationThreads threads = summary.threads();
        out.append("distinct_threads: ").append(threads.distinctThreads()).append('\n');
        out.append("platform_spans: ").append(threads.platformSpans()).append('\n');
        out.append("virtual_spans: ").append(threads.virtualSpans()).append('\n');
        // Worth naming rather than hiding in a zero: an unresolved thread means the span cannot be
        // matched to profiler samples at all, which limits what any deeper analysis can say.
        out.append("unknown_thread_spans: ").append(threads.unknownSpans()).append('\n');
    }

    private void renderSlowestTraces(StringBuilder out) {
        out.append(HEADING_SLOWEST).append('\n').append('\n');

        if (slowestTraces.isEmpty()) {
            out.append(NO_TRACES_NOTE).append('\n');
            return;
        }

        out.append("Individual traces worth exporting on their own for JVM context:")
                .append('\n').append('\n');
        for (TraceRow trace : slowestTraces) {
            out.append(BULLET_PREFIX)
                    .append(trace.traceId())
                    .append(DASH_SEPARATOR)
                    .append(TraceAiFormat.duration(trace.durationNanos()))
                    .append(", ").append(TraceAiFormat.count(trace.spanCount(), "span"));
            if (trace.errorCount() > 0) {
                out.append(", ").append(TraceAiFormat.count(trace.errorCount(), "error"));
            }
            out.append(", started ").append(trace.startMillisFromBeginning())
                    .append("ms into the recording");
            out.append('\n');
        }
    }
}
